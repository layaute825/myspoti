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


/**
 * The progress shown for an episode must never move backwards just because Spotify's show-page
 * response is stale. The server, local durable snapshot, and current player clock are all valid
 * sources; the furthest known non-negative position wins.
 */
internal fun podcastDisplayedPositionMs(
    serverPositionMs: Long,
    savedPositionMs: Long,
    livePositionMs: Long,
): Long = maxOf(0L, serverPositionMs, savedPositionMs, livePositionMs)
