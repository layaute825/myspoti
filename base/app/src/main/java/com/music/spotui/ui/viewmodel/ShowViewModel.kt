package com.music.spotui.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.EpisodeFilter
import com.music.spotui.data.preferences.PodcastPrefs
import com.music.spotui.data.entity.ShowPage
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A show page (podcast) or a book page (audiobook): one flow for the header and the rows, plus
 * the states a row can change on its own (followed, finished).
 */
class ShowViewModel constructor(
    private val repository: AppRepository = Graph.repository,
    private val api: Api = Graph.api,
    private val currentSongState: CurrentSongState = Graph.currentSongState,
) : ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId

    private val _page = MutableStateFlow<Response<ShowPage>>(Response.Loading())
    val page: StateFlow<Response<ShowPage>> = _page

    /** Following the show (the pill's state), updated on tap before Spotify answers. */
    val following = mutableStateOf(false)

    /** This podcast's automatic new-episode download switch. */
    val autoDownload = mutableStateOf(false)

    /**
     * Episode uri → finished. Spotify's per-episode state comes with the rows; this holds what
     * the user just changed (and the page's own "markedasfinished" set).
     */
    val finished: SnapshotStateMap<String, Boolean> = androidx.compose.runtime.mutableStateMapOf()

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    /**
     * When the user opened this show before this visit (ms, 0 = never): episodes released after
     * it carry the "New" mark, which stays for the whole visit.
     */
    val previousOpened = mutableStateOf(0L)

    /** The episode filter of this show (remembered per show). */
    val filter = mutableStateOf(EpisodeFilter.ALL)

    fun setFilter(showId: String, value: EpisodeFilter) {
        filter.value = value
        PodcastPrefs.setFilter(showId, value)
    }

    /** True when [episode] came out since the user last opened the show. */
    fun isNew(episode: SongsModel): Boolean = !isFinished(episode) && PodcastPrefs.releasedAfter(episode.releaseDate, previousOpened.value)

    private var showKey: String? = null

    fun loadShow(showId: String) {
        if (showKey == showId) return
        showKey = showId
        previousOpened.value = PodcastPrefs.lastOpened(showId)
        filter.value = PodcastPrefs.filter(showId)
        autoDownload.value = PodcastPrefs.autoDownload(showId)
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideShowPage(showId).collect { response ->
                // Spotify's show page may already include a played position for each row. Import
                // it only when this phone has no snapshot: after that, local progress (including
                // an intentional reset) is the offline source of truth and stale cloud data must
                // not move it forwards or backwards.
                val loaded = (response as? Response.Success)?.data
                if (loaded != null && !loaded.audiobook) {
                    loaded.items.filter { it.isEpisode && it.playedPositionMs > 0L }.forEach { episode ->
                        val episodeId = episode.url.removePrefix("episode:")
                        if (!PodcastPrefs.hasStoredProgress(episodeId)) {
                            PodcastPrefs.saveProgress(
                                episodeId, episode.playedPositionMs, episode.durationMs.toLong(),
                            )
                        }
                    }
                }
                _page.value = response
                // A failed page may be loaded again (Try again, or opening it anew).
                if (response is Response.Error) showKey = null
                if (loaded == null) return@collect
                following.value = loaded.show.saved || SongPlayer.library["spotify:show:$showId"] == true
                if (!loaded.audiobook && loaded.items.isNotEmpty()) {
                    val newest = loaded.items.maxOf { it.releaseDate }
                    PodcastPrefs.markOpened(showId, newest, following.value)
                    PodcastPrefs.autoDownloadCandidates(showId, loaded.items).forEach { episode ->
                        com.music.spotui.data.offline.PodcastDownloader.enqueue(episode)
                    }
                }
            }
        }
        // The page's own "finished" marks are a separate set: read once, then the rows carry
        // Spotify's playedState.
        viewModelScope.launch(Dispatchers.IO) {
            val marked = runCatching { api.finishedEpisodes() }.getOrDefault(emptySet())
            if (marked.isEmpty()) return@launch
            marked.forEach { uri ->
                val id = uri.substringAfterLast(':')
                finished["episode:$id"] = true
            }
        }
    }

    /** True when [episode] is finished: the last tap wins over what the row said. */
    fun isFinished(episode: SongsModel): Boolean = finished[episode.url] ?: episode.played

    /** "Marquer comme terminé" / "Marquer comme non commencé". */
    fun setFinished(episode: SongsModel, played: Boolean) {
        finished[episode.url] = played
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.setEpisodePlayed("spotify:episode:${episode.url.removePrefix("episode:")}", played) }
                .onFailure {
                    // Spotify refused: back to what the row said, the row is the truth.
                    kotlinx.coroutines.withContext(Dispatchers.Main) { finished.remove(episode.url) }
                }
        }
    }

    /** Enable downloads for releases newer than the newest episode already visible. */
    fun setAutoDownload(showId: String, on: Boolean) {
        val loaded = (_page.value as? Response.Success)?.data
        PodcastPrefs.setAutoDownload(showId, on, loaded?.items.orEmpty())
        autoDownload.value = on
    }

    fun setFollow(showId: String, on: Boolean) {
        following.value = on
        PodcastPrefs.setFollowed(showId, on)
        SongPlayer.setSaved("spotify:show:$showId", on)
    }
}
