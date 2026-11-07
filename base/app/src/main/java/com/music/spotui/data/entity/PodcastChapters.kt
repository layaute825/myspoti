package com.music.spotui.data.entity

/** Where a chapter heading came from. Spotify transcript headings take precedence over show notes. */
enum class PodcastChapterSource { SPOTIFY_TRANSCRIPT, DESCRIPTION }

/** A timestamped and seekable podcast chapter. */
data class PodcastChapter(
    val positionMs: Long,
    val title: String,
    val source: PodcastChapterSource = PodcastChapterSource.DESCRIPTION,
)

/**
 * Extract publisher-provided chapter timestamps from common show-note formats, including Spotify's
 * documented (MM:SS) / (HH:MM:SS) syntax, square brackets, bullets and HTML line breaks.
 *
 * The parser intentionally does not interpret arbitrary timestamps embedded in prose as chapters.
 */
fun parsePodcastChapters(description: String): List<PodcastChapter> {
    val text = description
        .replace(Regex("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>|</li\\s*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    val line = Regex(
        """(?m)^[ \t]*(?:[-•*][ \t]*)?[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?[ \t]*(?:[-–—|:][ \t]*)?([^\r\n]+?)[ \t]*$""",
    )
    return line.findAll(text).mapNotNull { match ->
        val time = match.groupValues[1].split(':').mapNotNull { it.toLongOrNull() }
        val title = match.groupValues[2].trim().trim('-', '–', '—', '|', ':').trim()
        if (time.size !in 2..3 || title.isBlank()) return@mapNotNull null
        val seconds = if (time.size == 3) {
            if (time[1] >= 60 || time[2] >= 60) return@mapNotNull null
            time[0] * 3600L + time[1] * 60L + time[2]
        } else {
            if (time[1] >= 60) return@mapNotNull null
            time[0] * 60L + time[1]
        }
        PodcastChapter(seconds * 1000L, title)
    }.distinctBy { it.positionMs }.sortedBy { it.positionMs }.toList()
}
