package com.music.spotui.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastChaptersTest {

    @Test
    fun parsesMinuteAndHourTimestampsAndSortsThem() {
        val chapters = parsePodcastChapters(
            """
            Notes for the episode
            [1:02:03] Conclusion
            00:00 Introduction
            12:34 - Interview
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                PodcastChapter(0L, "Introduction"),
                PodcastChapter(12 * 60_000L + 34_000L, "Interview"),
                PodcastChapter(3_723_000L, "Conclusion"),
            ),
            chapters,
        )
    }

    @Test
    fun ignoresInvalidOrUntitledTimestamps() {
        val chapters = parsePodcastChapters(
            """
            00:00
            99:99 Invalid
            This line has 12:34 a timestamp in the middle
            [1:61:00] Invalid hours format
            """.trimIndent(),
        )

        assertTrue(chapters.isEmpty())
    }

    @Test
    fun removesDuplicatePositions() {
        val chapters = parsePodcastChapters(
            """
            00:00 Introduction
            [00:00] Start
            01:00 Second
            """.trimIndent(),
        )

        assertEquals(
            listOf(PodcastChapter(0L, "Introduction"), PodcastChapter(60_000L, "Second")),
            chapters,
        )
    }
}
