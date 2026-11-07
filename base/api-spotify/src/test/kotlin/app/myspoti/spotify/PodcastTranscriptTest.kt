package app.myspoti.spotify

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastTranscriptTest {

    @Test
    fun extractsSpotifyChapterHeadingsAndIgnoresTranscriptLinesAndSpeakers() {
        val json = Json.parseToJsonElement(
            """
            {
              "section": [
                {"startMs": 0, "title": {"title": "Introduction"}},
                {"startMs": 12000, "title": {"title": "Speaker 1"}},
                {"startMs": 15000, "text": {"sentence": {"startMs": 15000, "text": "Hello"}}},
                {"startMs": 188000, "title": {"title": "The first topic"}},
                {"startMs": 734000, "title": {"title": "Questions and answers"}}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                app.myspoti.api.EpisodeChapter(0L, "Introduction"),
                app.myspoti.api.EpisodeChapter(188000L, "The first topic"),
                app.myspoti.api.EpisodeChapter(734000L, "Questions and answers"),
            ),
            spotifyEpisodeChapters(json),
        )
    }

    @Test
    fun missingOrEmptyTranscriptHasNoChapters() {
        assertTrue(spotifyEpisodeChapters(Json.parseToJsonElement("""{"section": []}""")).isEmpty())
        assertTrue(spotifyEpisodeChapters(Json.parseToJsonElement("""{}""")).isEmpty())
    }

    @Test
    fun sortsAndDeduplicatesChapterStartPositions() {
        val json = Json.parseToJsonElement(
            """
            {"section": [
              {"startMs": 60000, "title": {"title": "Later"}},
              {"startMs": 0, "title": {"title": "Start"}},
              {"startMs": 60000, "title": {"title": "Duplicate"}}
            ]}
            """.trimIndent(),
        )

        assertEquals(
            listOf(app.myspoti.api.EpisodeChapter(0L, "Start"), app.myspoti.api.EpisodeChapter(60000L, "Later")),
            spotifyEpisodeChapters(json),
        )
    }
}
