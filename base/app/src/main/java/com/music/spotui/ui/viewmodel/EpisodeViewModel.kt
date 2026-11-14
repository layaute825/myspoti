package com.music.spotui.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Api
import com.music.spotui.data.entity.SongsModel
import app.myspoti.api.EpisodeChapter
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One episode page: the episode itself plus the two marks a listener can change on it. */
class EpisodeViewModel constructor(
    private val repository: AppRepository = Graph.repository,
    private val api: Api = Graph.api,
) : ViewModel() {

    private val _episode = MutableStateFlow<SongsModel?>(null)
    val episode: StateFlow<SongsModel?> = _episode

    private val _chapters = MutableStateFlow<List<EpisodeChapter>>(emptyList())
    /** Spotify transcript headings (not merely timestamps copied into the description). */
    val chapters: StateFlow<List<EpisodeChapter>> = _chapters
    val chaptersLoading = mutableStateOf(false)

    /** False once Spotify answered nothing: the page says so instead of staying empty. */
    val failed = mutableStateOf(false)

    val loading = mutableStateOf(true)

    /** Finished, as the episode page's own menu shows it. */
    val finished = mutableStateOf(false)

    private var key: String? = null

    fun load(episodeId: String) {
        if (key == episodeId) return
        key = episodeId
        _episode.value = null
        _chapters.value = emptyList()
        chaptersLoading.value = true
        loading.value = true
        failed.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { repository.provideEpisode(episodeId) }.getOrNull()
            // Seed this phone's offline history from Spotify once, but never replace a local
            // snapshot (which may represent a deliberate rewind or reset).
            if (loaded?.isEpisode == true && loaded.playedPositionMs > 0L &&
                !com.music.spotui.data.preferences.PodcastPrefs.hasStoredProgress(episodeId)
            ) {
                com.music.spotui.data.preferences.PodcastPrefs.saveProgress(
                    episodeId, loaded.playedPositionMs, loaded.durationMs.toLong(),
                )
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                loading.value = false
                failed.value = loaded == null
                if (loaded != null) {
                    _episode.value = loaded
                    finished.value = loaded.played
                    SongPlayer.requestSaved("spotify:episode:$episodeId")
                }
            }
        }
        // Spotify's transcript-read-along endpoint is separate from the description and
        // contains auto-generated chapter headings when this episode has a transcript.
        viewModelScope.launch(Dispatchers.IO) {
            val chapters = try {
                api.getEpisodeChapters(episodeId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _chapters.value = chapters
                chaptersLoading.value = false
            }
        }

        // The page's own "markedasfinished" set wins over the playedState when it holds the id.
        viewModelScope.launch(Dispatchers.IO) {
            val uri = "spotify:episode:$episodeId"
            if (runCatching { api.finishedEpisodes() }.getOrDefault(emptySet()).contains(uri)) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { finished.value = true }
            }
        }
    }

    /** "Marquer comme terminé" / "Marquer comme non commencé". */
    fun setFinished(episode: SongsModel, played: Boolean) {
        finished.value = played
        _episode.value = episode.copy(played = played)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.setEpisodePlayed("spotify:episode:${episode.url.removePrefix("episode:")}", played) }
                .onFailure { kotlinx.coroutines.withContext(Dispatchers.Main) { finished.value = episode.played } }
        }
    }
}
