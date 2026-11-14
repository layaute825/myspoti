package app.myspoti.spotify

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun ignoresSpeakerAndPersonLabelsButKeepsRealChapterTitles() {
        val json = Json.parseToJsonElement(
            """
            {"section": [
              {"startMs": 0, "title": {"title": "Introduction"}},
              {"startMs": 12000, "title": {"title": "Personne 1"}},
              {"startMs": 24000, "title": {"title": "Speaker 2: invited guest"}},
              {"startMs": 36000, "title": {"title": "Participant #3"}},
              {"startMs": 48000, "title": {"title": "The actual chapter"}},
              {"startMs": 60000, "title": {"title": "Locuteur 4"}},
              {"startMs": 72000, "title": {"title": "Conclusion"}}
            ]}
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                app.myspoti.api.EpisodeChapter(0L, "Introduction"),
                app.myspoti.api.EpisodeChapter(48000L, "The actual chapter"),
                app.myspoti.api.EpisodeChapter(72000L, "Conclusion"),
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
