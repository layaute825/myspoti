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
        assertEquals(180_000L, podcastDisplayedPositionMs(0L, 180_000L, 0L, preferLocalSnapshot = true))
    }

    @Test
    fun showPageUsesLivePlayerPositionBeforeThePeriodicDiskSnapshot() {
        assertEquals(180_000L, podcastDisplayedPositionMs(0L, 0L, 180_000L))
    }

    @Test
    fun showPageNeverMovesBehindTheNewestKnownProgressSource() {
        assertEquals(240_000L, podcastDisplayedPositionMs(240_000L, 180_000L, 200_000L))
    }

    @Test
    fun staleSpotifyPositionCannotEraseNewerLocalProgress() {
        assertNull(podcastProgressToPersist(180_000L, 0L))
        assertNull(podcastProgressToPersist(180_000L, 120_000L))
    }

    @Test
    fun smallReportingJitterNeverMovesTheStoredPositionBackwards() {
        assertEquals(180_000L, podcastProgressToPersist(180_000L, 179_000L))
    }

    @Test
    fun newerSnapshotsAdvanceProgress() {
        assertEquals(185_000L, podcastProgressToPersist(180_000L, 185_000L))
    }

    @Test
    fun explicitSeekCanMoveProgressBackwardsOrResetIt() {
        assertEquals(60_000L, podcastProgressToPersist(180_000L, 60_000L, allowRewind = true))
        assertEquals(0L, podcastProgressToPersist(180_000L, 0L, allowRewind = true))
    }


    @Test
    fun existingLocalSnapshotWinsOverAStaleOrHigherSpotifyPosition() {
        assertEquals(
            60_000L,
            podcastDisplayedPositionMs(
                serverPositionMs = 180_000L,
                savedPositionMs = 60_000L,
                livePositionMs = 0L,
                preferLocalSnapshot = true,
            ),
        )
    }

}
