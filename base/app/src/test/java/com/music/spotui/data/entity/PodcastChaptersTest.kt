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
    fun parsesSpotifyParenthesizedTimestampsAndHtmlBreaks() {
        val chapters = parsePodcastChapters(
            """
            <p>(00:00) Introduction</p><p>(12:34) Interview &amp; discussion</p>
            (1:02:03) Conclusion
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                PodcastChapter(0L, "Introduction"),
                PodcastChapter(12 * 60_000L + 34_000L, "Interview & discussion"),
                PodcastChapter(3_723_000L, "Conclusion"),
            ),
            chapters,
        )
    }

    @Test
    fun filtersSpeakerLabelsButKeepsRealDescriptionChapters() {
        val chapters = parsePodcastChapters(
            """
            00:00 Introduction
            01:00 Personne 1
            02:00 Speaker 2: invited guest
            03:00 Participant #3
            04:00 Les vrais chapitres
            05:00 Conclusion
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                PodcastChapter(0L, "Introduction"),
                PodcastChapter(240_000L, "Les vrais chapitres"),
                PodcastChapter(300_000L, "Conclusion"),
            ),
            chapters,
        )
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
