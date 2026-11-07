package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.Graph
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** How a show page lists its episodes, besides the newest / oldest order. */
enum class EpisodeFilter {
    /** Spotify's own order. */
    ALL,
    /** Episodes not played yet first, the played ones after them (each group keeps its order). */
    UNPLAYED_FIRST,
    /** Played episodes are left out. */
    HIDE_PLAYED;

    fun next(): EpisodeFilter = entries[(ordinal + 1) % entries.size]

    /** [episodes] in this filter's order; [played] says which of them are finished. */
    fun apply(episodes: List<SongsModel>, played: (SongsModel) -> Boolean): List<SongsModel> = when (this) {
        ALL -> episodes
        UNPLAYED_FIRST -> episodes.sortedBy { if (played(it)) 1 else 0 }
        HIDE_PLAYED -> episodes.filterNot(played)
    }
}

/**
 * What the app remembers about podcasts on this phone: the episode filter chosen on a show page
 * (per show, the last choice for the shows not chosen yet), when each show was last opened, and
 * the date of the newest episode seen, for the "new episode" marks. Nothing is sent anywhere.
 */
object PodcastPrefs {
    private const val FILE = "podcast_state"
    /** The cheap "newest episode" check of a show is repeated at most this often. */
    private const val CHECK_EVERY_MS = 6 * 3_600_000L

    private fun prefs() = Graph.app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Bumped on every change a mark depends on, so a composable reading [hasNew] redraws. */
    private var tick by mutableIntStateOf(0)

    /** Delete a podcast file automatically after the episode reaches its end. */
    var deleteAfterFinish by mutableStateOf(prefs().getBoolean("delete_episode_after_finish", false))
        private set

    fun updateDeleteAfterFinish(value: Boolean) {
        deleteAfterFinish = value
        prefs().edit().putBoolean("delete_episode_after_finish", value).apply()
        tick++
    }

    /** Whether new releases of this show should be queued for download. */
    fun autoDownload(showId: String): Boolean {
        @Suppress("UNUSED_VARIABLE") val read = tick
        return showId.isNotBlank() && prefs().getBoolean("auto_download:$showId", false)
    }

    /**
     * Enabling auto-download starts from the newest episode already visible: it does not
     * unexpectedly download a show's entire back catalogue. Later releases are queued on refresh.
     */
    fun setAutoDownload(
        showId: String,
        enabled: Boolean,
        newestKnownEpisodes: List<SongsModel> = emptyList(),
    ) {
        if (showId.isBlank()) return
        val newestDate = newestKnownEpisodes.maxOfOrNull { it.releaseDate }.orEmpty()
        val latestIds = newestKnownEpisodes.filter { it.releaseDate == newestDate }.map { it.url }.toSet()
        val edit = prefs().edit().putBoolean("auto_download:$showId", enabled)
        if (enabled && newestDate.isNotBlank()) {
            edit.putString("auto_cursor:$showId", newestDate)
            edit.putStringSet("auto_seen:$showId", latestIds)
        }
        edit.apply()
        tick++
        com.music.spotui.data.offline.PodcastAutoDownloadScheduler.schedule(Graph.app)
    }

    /** IDs of the shows whose automatic download option is enabled. */
    fun autoDownloadShowIds(): Set<String> = prefs().all.entries
        .asSequence()
        .filter { it.key.startsWith("auto_download:") && it.value == true }
        .map { it.key.removePrefix("auto_download:") }
        .filter { it.isNotBlank() }
        .toSet()

    /** New releases since the last auto-download check, including a second episode dated today. */
    fun autoDownloadCandidates(showId: String, episodes: List<SongsModel>): List<SongsModel> {
        if (!autoDownload(showId) || episodes.isEmpty()) return emptyList()
        val p = prefs()
        val cursor = p.getString("auto_cursor:$showId", "").orEmpty()
        val seen = p.getStringSet("auto_seen:$showId", emptySet()).orEmpty()
        val dated = episodes.filter { it.isEpisode && it.releaseDate.isNotBlank() }
        if (cursor.isBlank()) {
            val newest = dated.maxOfOrNull { it.releaseDate }.orEmpty()
            if (newest.isNotBlank()) {
                p.edit().putString("auto_cursor:$showId", newest)
                    .putStringSet("auto_seen:$showId", dated.filter { it.releaseDate == newest }.map { it.url }.toSet())
                    .apply()
            }
            tick++
            return emptyList()
        }
        val candidates = dated.filter {
            it.releaseDate > cursor || (it.releaseDate == cursor && it.url !in seen)
        }.sortedBy { it.releaseDate }
        if (candidates.isNotEmpty()) {
            val newest = maxOf(cursor, candidates.maxOf { it.releaseDate })
            val newestIds = dated.filter { it.releaseDate == newest }.map { it.url }.toMutableSet()
            if (newest == cursor) newestIds.addAll(seen)
            p.edit().putString("auto_cursor:$showId", newest)
                .putStringSet("auto_seen:$showId", newestIds)
                .apply()
            tick++
        }
        return candidates
    }

    fun isCompleted(episodeId: String): Boolean {
        if (episodeId.isBlank()) return false
        @Suppress("UNUSED_VARIABLE") val read = tick
        return prefs().getBoolean("episode_completed:$episodeId", false)
    }

    /** Last listened position for this episode (ms); completed episodes start from the beginning. */
    fun positionMs(episodeId: String): Long {
        if (episodeId.isBlank()) return 0L
        @Suppress("UNUSED_VARIABLE") val read = tick
        val p = prefs()
        if (p.getBoolean("episode_completed:$episodeId", false)) return 0L
        return p.getLong("episode_position:$episodeId", 0L).coerceAtLeast(0L)
    }

    /**
     * Persist independent episode progress. A completed episode clears its resume point and,
     * when requested, removes its downloaded copy exactly once.
     */
    fun saveProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        if (episodeId.isBlank() || positionMs < 0L) return
        val p = prefs()
        val wasCompleted = p.getBoolean("episode_completed:$episodeId", false)
        val complete = durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)
        p.edit().apply {
            if (complete) {
                putBoolean("episode_completed:$episodeId", true)
                putLong("episode_position:$episodeId", 0L)
            } else {
                // A replay from the queue may not pass through the episode page's reset action.
                // The first partial position therefore clears the completion marker itself.
                remove("episode_completed:$episodeId")
                putLong("episode_position:$episodeId", positionMs)
            }
        }.apply()
        tick++
        if (complete && !wasCompleted && deleteAfterFinish) {
            com.music.spotui.data.offline.PodcastDownloader.remove("episode:$episodeId")
        }
    }

    /** A deliberate replay clears a previous completion marker. */
    fun resetProgress(episodeId: String) {
        if (episodeId.isBlank()) return
        prefs().edit().remove("episode_completed:$episodeId").putLong("episode_position:$episodeId", 0L).apply()
        tick++
    }

    fun filter(showId: String): EpisodeFilter {
        val p = prefs()
        val v = p.getString("filter:$showId", null) ?: p.getString("filter", null)
        return EpisodeFilter.entries.firstOrNull { it.name == v } ?: EpisodeFilter.ALL
    }

    fun setFilter(showId: String, filter: EpisodeFilter) {
        prefs().edit().putString("filter:$showId", filter.name).putString("filter", filter.name).apply()
    }

    /** The filter of "Your Episodes" (remembered on its own). */
    fun libraryFilter(): EpisodeFilter {
        val v = prefs().getString("filter:library", null)
        return EpisodeFilter.entries.firstOrNull { it.name == v } ?: EpisodeFilter.ALL
    }

    fun setLibraryFilter(filter: EpisodeFilter) {
        prefs().edit().putString("filter:library", filter.name).apply()
    }

    /** When [showId] was last opened (ms), 0 when never. */
    fun lastOpened(showId: String): Long = prefs().getLong("opened:$showId", 0L)

    /** The show page was opened: [newest] is its newest episode's release date seen there. */
    fun markOpened(showId: String, newest: String, followed: Boolean) {
        if (showId.isBlank()) return
        prefs().edit().apply {
            putLong("opened:$showId", System.currentTimeMillis())
            if (newest.isNotBlank()) putString("latest:$showId", newest)
            putBoolean("followed:$showId", followed)
        }.apply()
        tick++
    }

    /** The follow button was used on the show page. */
    fun setFollowed(showId: String, followed: Boolean) {
        if (showId.isBlank()) return
        prefs().edit().putBoolean("followed:$showId", followed).apply()
        tick++
    }

    fun isFollowed(showId: String): Boolean = prefs().getBoolean("followed:$showId", false)

    /** Release date of the newest episode known for [showId] ("" when never seen). */
    fun latest(showId: String): String = prefs().getString("latest:$showId", "").orEmpty()

    /** The cheap check is due: never done, or older than a few hours. */
    fun checkDue(showId: String): Boolean = System.currentTimeMillis() - prefs().getLong("checked:$showId", 0L) > CHECK_EVERY_MS

    /** The cheap check answered: [newest] is the newest episode's release date. */
    fun checked(showId: String, newest: String) {
        prefs().edit().apply {
            putLong("checked:$showId", System.currentTimeMillis())
            if (newest > latest(showId)) putString("latest:$showId", newest)
        }.apply()
        tick++
    }

    /** True when [release] is after [since] (ms); a date without a time counts from its start. */
    fun releasedAfter(release: String, since: Long): Boolean {
        if (release.isBlank() || since <= 0L) return false
        // Spotify gives a plain date ("2026-10-27") nearly always: no exception for the common case.
        val ms = (if (release.length > 10) runCatching { Instant.parse(release).toEpochMilli() }.getOrNull() else null)
            ?: runCatching { LocalDate.parse(release.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: return false
        return ms > since
    }

    /** The cheap check for [showId] when it is due: one episode asked for, at most every few hours. */
    suspend fun refresh(showId: String) {
        if (!isFollowed(showId) || lastOpened(showId) == 0L || !checkDue(showId)) return
        checked(showId, Graph.api.latestEpisodeDate(showId))
    }

    /** A followed show has an episode released since the user last opened it. */
    fun hasNew(showId: String): Boolean {
        @Suppress("UNUSED_VARIABLE") val read = tick
        if (!isFollowed(showId)) return false
        return releasedAfter(latest(showId), lastOpened(showId))
    }
}
