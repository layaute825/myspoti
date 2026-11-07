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
}
