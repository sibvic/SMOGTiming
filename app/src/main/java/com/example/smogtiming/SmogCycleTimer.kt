package com.example.smogtiming

/**
 * Tracks two consecutive false transitions after true and measures elapsed wall time
 * between the first and second false frame.
 */
class SmogCycleTimer {

    sealed class Event {
        data object None : Event()
        data class Completed(val elapsedMs: Long) : Event()
    }

    private companion object {
        const val MIN_INTERVAL_AFTER_COMPLETE_MS = 100L
    }

    private var previousState: Boolean? = null
    private var falseTransitionCount = 0
    private var timerStartTimeMs = 0L
    /** Wall time of the last [Event.Completed]; used to debounce a quick re-start. */
    private var lastCompletedAtMs = 0L
    /** Start time of the cycle that just completed; restored if a new first-false is too soon after complete. */
    private var lastCompletedCycleStartMs = 0L

    fun onFrameAnalyzed(isConeVisible: Boolean): Event {
        val previous = previousState
        val now = System.currentTimeMillis()

        if (previous == true && isConeVisible == false) {
            falseTransitionCount++
            when {
                falseTransitionCount == 1 && timerStartTimeMs == 0L -> {
                    timerStartTimeMs =
                        if (lastCompletedAtMs > 0L && now - lastCompletedAtMs < MIN_INTERVAL_AFTER_COMPLETE_MS) {
                            lastCompletedCycleStartMs
                        } else {
                            now
                        }
                }
                falseTransitionCount == 2 && timerStartTimeMs > 0L -> {
                    val elapsedMs = now - timerStartTimeMs
                    lastCompletedCycleStartMs = timerStartTimeMs
                    lastCompletedAtMs = now
                    timerStartTimeMs = 0L
                    falseTransitionCount = 0
                    previousState = isConeVisible
                    return Event.Completed(elapsedMs)
                }
            }
        }

        previousState = isConeVisible
        return Event.None
    }
}
