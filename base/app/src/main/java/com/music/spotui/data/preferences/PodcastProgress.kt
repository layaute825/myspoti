package com.music.spotui.data.preferences

/**
 * Returns a stored podcast position only when it is meaningfully ahead of the player's current
 * position. The tolerance avoids repeated seeks caused by normal state-reporting jitter.
 */
internal fun podcastResumePosition(
    savedPositionMs: Long,
    currentPositionMs: Long,
    toleranceMs: Long = 2_000L,
): Long? = savedPositionMs.takeIf {
    it > toleranceMs && currentPositionMs + toleranceMs < it
}
