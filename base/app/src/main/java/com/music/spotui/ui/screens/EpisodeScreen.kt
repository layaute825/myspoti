package com.music.spotui.ui.screens

import com.music.spotui.ui.components.wideClickable
import com.music.spotui.ui.theme.headerTint
import com.music.spotui.ui.theme.AppText

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.EpisodeDownloadButton
import com.music.spotui.ui.components.EpisodeOptionsSheet
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.episodeClock
import com.music.spotui.ui.components.episodeDateLabel
import com.music.spotui.ui.components.episodeMinutes
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.components.shareEpisode
import com.music.spotui.ui.components.spotifyUri
import com.music.spotui.ui.navigation.showRoute
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.viewmodel.EpisodeViewModel

/**
 * One podcast episode (or book chapter), as the official page shows it: cover, title, the show
 * it belongs to, date and length, the action row, then the description.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun EpisodeScreen(navController: NavController, episodeId: String, fallbackName: String = "") {
    val vm: EpisodeViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(episodeId) { vm.load(episodeId) }

    val episode by vm.episode.collectAsState()
    val finished = vm.finished.value
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val np by SongPlayer.nowPlaying
    val uri = "spotify:episode:$episodeId"
    val playing = np.uri == uri && SongPlayer.isPlaying()
    val saved = SongPlayer.library[uri] == true

    fun play() {
        val ep = episode ?: return
        val resumeAt = com.music.spotui.data.preferences.PodcastPrefs.positionMs(episodeId)
        if (com.music.spotui.data.preferences.PodcastPrefs.isCompleted(episodeId)) {
            com.music.spotui.data.preferences.PodcastPrefs.resetProgress(episodeId)
        }
        SongPlayer.playSong(ep.url, context, positionMs = resumeAt.takeIf { it > 0L })
        // The mini player and the notification show it before Spotify reports it.
        com.music.spotui.di.Graph.currentSongState.updateSongState(ep.coverUri, ep.title, ep.singer, true, ep.id, 0, ep.album)
    }

    val spotifyChapters by vm.chapters.collectAsState()
    val chaptersLoading = vm.chaptersLoading.value
    val descriptionChapters = remember(episode?.description) {
        com.music.spotui.data.entity.parsePodcastChapters(episode?.description.orEmpty())
    }
    // Spotify transcript headings are authoritative. Only fall back to the publisher's notes
    // when Spotify has no chapter transcript for this episode.
    val chapters = remember(spotifyChapters, descriptionChapters) {
        spotifyChapters.map {
            com.music.spotui.data.entity.PodcastChapter(
                positionMs = it.startMs,
                title = it.title,
                source = com.music.spotui.data.entity.PodcastChapterSource.SPOTIFY_TRANSCRIPT,
            )
        }.ifEmpty { descriptionChapters }
    }
    val chaptersFromSpotify = spotifyChapters.isNotEmpty()

    episode?.let { ep ->
        if (menuOpen) EpisodeOptionsSheet(
            song = ep,
            finished = finished,
            onPlay = ::play,
            onMarkFinished = { vm.setFinished(ep, it) },
            onDismiss = { menuOpen = false },
        )
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(headerTint(Color(0xFF34302B), 0f), AppBackground)))
                        .statusBarsPadding(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(12.dp)
                            .wideClickable { navController.navigateUp() }
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = Color.White)
                    }
                    GlideImage(
                        transition = CrossFade,
                        model = episode?.coverUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(1f)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = episode?.title?.takeIf { it.isNotBlank() } ?: fallbackName,
                        color = AppText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    // The show, as the page links it.
                    val showId = episode?.showId.orEmpty()
                    if (showId.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { navController.navigate(showRoute(showId, episode?.singer.orEmpty())) },
                        ) {
                            GlideImage(
                                transition = CrossFade,
                                model = episode?.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = episode?.singer.orEmpty(),
                                color = AppText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val date = episodeDateLabel(episode?.releaseDate.orEmpty())
                    val minutes = episodeMinutes(episode?.durationMs ?: 0)
                    Text(
                        text = if (date.isBlank()) minutes else "$date • $minutes",
                        color = AppText.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    // Action row: save, download, share, "…", then Play.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        SaveEpisodeButton(saved) {
                            val ep = episode ?: return@SaveEpisodeButton
                            SongPlayer.setSaved(ep.spotifyUri, !saved)
                        }
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            episode?.let { EpisodeDownloadButton(it, size = 24.dp) }
                        }
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                                episode?.let { shareEpisode(context, it) }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.a11y_share),
                                tint = AppText.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.episode_options),
                                tint = AppText.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(AppAccent)
                                .pressable(0.95f) { if (playing) SongPlayer.pause() else play() }
                                .padding(horizontal = 18.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.music.spotui.ui.components.PlayPauseIcon(
                                    playing = playing,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (playing) R.string.notif_action_pause else R.string.episode_play), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (vm.loading.value) item { Loader() }
            if (vm.failed.value) item {
                Text(
                    stringResource(R.string.episode_not_found),
                    color = AppText.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            episode?.let { ep ->
                val localPosition = com.music.spotui.data.preferences.PodcastPrefs.positionMs(episodeId)
                val resumePosition = maxOf(ep.playedPositionMs, localPosition)
                if (resumePosition > 0L && !finished) item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.episode_remaining, episodeClock((ep.durationMs.toLong() - resumePosition).coerceAtLeast(0L))),
                            color = AppAccent,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (chaptersLoading && chapters.isEmpty()) item(key = "chapters-loading") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppAccent,
                        )
                        Text(
                            stringResource(R.string.podcast_chapters_loading),
                            color = AppText.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                if (chapters.isNotEmpty()) item(key = "chapters-heading") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.podcast_chapters),
                            color = AppText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(
                                if (chaptersFromSpotify) R.string.podcast_chapters_spotify
                                else R.string.podcast_chapters_description,
                            ),
                            color = AppText.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
                        )
                    }
                }
                if (chapters.isNotEmpty()) items(chapters, key = { "chapter:${it.positionMs}" }) { chapter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppText.copy(alpha = 0.045f))
                            .clickable {
                                if (SongPlayer.isEpisodeCurrent(episodeId)) {
                                    SongPlayer.seekTo(chapter.positionMs)
                                } else {
                                    if (com.music.spotui.data.preferences.PodcastPrefs.isCompleted(episodeId)) {
                                        com.music.spotui.data.preferences.PodcastPrefs.resetProgress(episodeId)
                                    }
                                    SongPlayer.playSong(ep.url, context, positionMs = chapter.positionMs)
                                    com.music.spotui.di.Graph.currentSongState.updateSongState(
                                        ep.coverUri, ep.title, ep.singer, true, ep.id, 0, ep.album,
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AppAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = chapter.title,
                            color = AppText,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 12.dp),
                        )
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(AppAccent.copy(alpha = 0.12f))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = episodeClock(chapter.positionMs),
                                color = AppAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (ep.description.isNotBlank()) item {
                    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(250)).padding(16.dp)) {
                        Text(
                            text = com.music.spotui.ui.components.rememberLinkedText(ep.description, AppAccent),
                            color = AppText.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(if (expanded) R.string.episode_show_less else R.string.episode_show_more),
                            color = AppText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
            }
        }
    }
}

/** ✓ disc when the episode is saved, outlined + otherwise (same shape as the player's). */
@Composable
private fun SaveEpisodeButton(saved: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        if (saved) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(26.dp).clip(CircleShape).background(AppAccent),
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.episode_unsave), tint = Color.Black, modifier = Modifier.size(17.dp))
            }
        } else {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.episode_save),
                tint = AppText.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
