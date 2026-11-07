package com.music.spotui.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastProgressTest {
    @Test
    fun restoresEpisodeWhenPlayerStartsAtBeginning() {
        assertEquals(240_000L, podcastResumePosition(240_000L, 0L))
    }

    @Test
    fun doesNotSeekWhenPlayerAlreadyHasTheSavedPosition() {
        assertNull(podcastResumePosition(240_000L, 239_000L))
    }

    @Test
    fun doesNotRestoreAnEmptyOrTinyProgressPoint() {
        assertNull(podcastResumePosition(0L, 0L))
        assertNull(podcastResumePosition(1_500L, 0L))
    }

    @Test
    fun restoresWhenTheReportedPositionIsSignificantlyBehind() {
        assertEquals(240_000L, podcastResumePosition(240_000L, 120_000L))
    }
    @Test
    fun repairsAnExplicitEpisodeStartWhenSpotifyIgnoresTheRequestedPosition() {
        // Regression: play(uri, positionMs = 3 min) can still open at 0:00.
        assertEquals(180_000L, podcastResumePosition(180_000L, 0L))
    }

    @Test
    fun doesNotSeekAgainWhenSpotifyHasAppliedTheRequestedEpisodeStart() {
        assertNull(podcastResumePosition(180_000L, 180_000L))
        assertNull(podcastResumePosition(180_000L, 179_000L))
    }

    @Test
    fun doesNotRewindAnEpisodeThatHasAlreadyPassedTheRequestedStart() {
        assertNull(podcastResumePosition(180_000L, 181_000L))
    }

    @Test
    fun showPageKeepsLocalProgressWhenSpotifyReturnsZero() {
        assertEquals(180_000L, podcastDisplayedPositionMs(0L, 180_000L, 0L))
    }

    @Test
    fun showPageUsesLivePlayerPositionBeforeThePeriodicDiskSnapshot() {
        assertEquals(180_000L, podcastDisplayedPositionMs(0L, 0L, 180_000L))
    }

    @Test
    fun showPageNeverMovesBehindTheNewestKnownProgressSource() {
        assertEquals(240_000L, podcastDisplayedPositionMs(240_000L, 180_000L, 200_000L))
    }

}
