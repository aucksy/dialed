package com.dialed.app.wear.common

/**
 * What must happen for a freshly-installed pushed face to actually become active
 * (adapted from Google's androidify sample). Computed on the watch BEFORE install,
 * because installing/switching makes the "do I own the active face" API inaccurate.
 */
enum class WatchFaceActivationStrategy {
    NO_ACTION_NEEDED, // this app already owns the active face
    CALL_SET_ACTIVE_NO_USER_ACTION, // permission granted + one-shot API unused -> just call it
    FOLLOW_PROMPT_ON_WATCH, // request the runtime permission on the watch
    LONG_PRESS_TO_SET, // guide the user to long-press and pick the face
    GO_TO_WATCH_SETTINGS, // permission permanently denied -> send to settings
    ;

    companion object {
        fun fromWatchFaceState(
            hasActiveWatchFace: Boolean = false,
            hasGrantedSetActivePermission: Boolean = false,
            canRequestSetActivePermission: Boolean = true,
            hasUsedSetActiveApi: Boolean = false,
        ): WatchFaceActivationStrategy = when {
            hasActiveWatchFace -> NO_ACTION_NEEDED
            hasGrantedSetActivePermission && !hasUsedSetActiveApi -> CALL_SET_ACTIVE_NO_USER_ACTION
            canRequestSetActivePermission && !hasUsedSetActiveApi -> FOLLOW_PROMPT_ON_WATCH
            !canRequestSetActivePermission && !hasGrantedSetActivePermission && !hasUsedSetActiveApi -> GO_TO_WATCH_SETTINGS
            else -> LONG_PRESS_TO_SET
        }
    }
}
