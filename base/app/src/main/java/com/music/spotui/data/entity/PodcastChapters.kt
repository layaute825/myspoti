package com.music.spotui.data.entity

/** A timestamped chapter supplied in the podcast's episode description. */
data class PodcastChapter(val positionMs: Long, val title: String)

/**
 * Extract publisher-provided chapter timestamps from common show-note formats:
 *   00:00 Introduction
 *   12:34 - Interview
 *   [1:02:03] Conclusion
 *
 * This deliberately does not invent chapters when no timestamps are published.
 */
fun parsePodcastChapters(description: String): List<PodcastChapter> {
    val line = Regex("""(?m)^[ \t]*(?:[-•*][ \t]*)?\[?((?:\d{1,2}:)?\d{1,2}:\d{2})\]?[ \t]*(?:[-–—|:][ \t]*)?([^\r\n]+?)[ \t]*$""")
    return line.findAll(description).mapNotNull { match ->
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
