package com.music.spotui.data.preferences

/**
 * Computes the next durable local snapshot for an episode.
 *
 * Spotify can briefly publish an older position while switching screens/devices. Such a snapshot
 * must not erase progress already stored on this phone. A deliberate seek/restart opts into
 * [allowRewind], because moving backwards is then the user's explicit choice.
 */
internal fun podcastProgressToPersist(
    previousPositionMs: Long,
    incomingPositionMs: Long,
    allowRewind: Boolean = false,
    toleranceMs: Long = 2_000L,
): Long? {
    if (previousPositionMs < 0L || incomingPositionMs < 0L || toleranceMs < 0L) return null
    if (allowRewind || previousPositionMs == 0L) return incomingPositionMs
    if (incomingPositionMs < previousPositionMs - toleranceMs) return null
    return maxOf(previousPositionMs, incomingPositionMs)
}

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
