package com.dialed.app.wear.common

/**
 * What must happen for a freshly-installed pushed face to actually become active
 * (adapted from Google's androidify sample). Computed on the watch BEFORE install,
 * because installing/switching makes the "do I own the active face" API inaccurate.
 */
enum class WatchFaceActivationStrategy {
    NO_ACTION_NEEDED, // this app already owns the active face -> replacing the slot keeps it active
    CALL_SET_ACTIVE_NO_USER_ACTION, // permission granted -> ATTEMPT the unattended set (platform decides)
    FOLLOW_PROMPT_ON_WATCH, // request the runtime permission on the watch, then attempt the set
    LONG_PRESS_TO_SET, // the platform refused the unattended set -> guide the user to set it by hand
    GO_TO_WATCH_SETTINGS, // permission permanently denied -> send to settings
    ;

    companion object {
        /**
         * Chosen BEFORE install (installing/switching makes "do I own the active face" inaccurate).
         *
         * We deliberately DO NOT gate on a persisted "already used the one-shot" flag any more. The
         * unattended set-active allowance is a PLATFORM-enforced budget: `setWatchFaceAsActive`
         * throws (ERROR_MAXIMUM_ATTEMPTS) once it's exhausted and the live face is another app's.
         * A local latch can only ever be MORE pessimistic than the platform — it permanently blocks
         * every future attempt, so it silently forfeits any activation the platform would still grant
         * (e.g. the exact "first push of the day" case, and any per-boot / per-newly-added-face reset
         * whose semantics Google's public docs leave ambiguous). So when we don't already own the
         * active face and we hold the permission, we ATTEMPT the set and let the platform's own
         * response be the authority. A refusal falls back to [LONG_PRESS_TO_SET] post-install (in the
         * listener) — never to a false "applied".
         */
        fun fromWatchFaceState(
            hasActiveWatchFace: Boolean = false,
            hasGrantedSetActivePermission: Boolean = false,
            canRequestSetActivePermission: Boolean = true,
        ): WatchFaceActivationStrategy = when {
            hasActiveWatchFace -> NO_ACTION_NEEDED
            hasGrantedSetActivePermission -> CALL_SET_ACTIVE_NO_USER_ACTION
            canRequestSetActivePermission -> FOLLOW_PROMPT_ON_WATCH
            else -> GO_TO_WATCH_SETTINGS
        }
    }
}
