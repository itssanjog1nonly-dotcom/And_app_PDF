// FILE: app/src/main/java/com/sanjog/pdfscrollreader/util/Constants.kt
package com.sanjog.pdfscrollreader.util

object Constants {
    const val TAG_MAIN_ACTIVITY = "MainActivity"
    const val TAG_STROKE = "Stroke"
    const val TAG_ANNOTATION_DATA = "AnnotationData"
    const val TAG_BOOKMARK_ENTRY = "BookmarkEntry"
    const val TAG_RECENT_FILE = "RecentFile"
    const val TAG_SCROLL_STATE = "ScrollState"
    const val TAG_ZOOM_AWARE_SCROLLER = "ZoomAwareScroller"
    const val TAG_ANNOTATION_VIEW = "AnnotationView"

    const val PREFS_NAME = "pdf_scroll_reader_prefs"
    const val KEY_DARK_MODE_ENABLED = "key_dark_mode_enabled"
    const val KEY_SCROLL_SPEED_MS = "key_scroll_speed_ms"
    const val KEY_LOOP_ENABLED = "key_loop_enabled"

    const val ANNOTATIONS_DIRECTORY_NAME = "annotations"
    const val ANNOTATION_FILE_RELATIVE_PATH_TEMPLATE = "annotations/%s.json"
    const val ANNOTATION_FILE_ABSOLUTE_PATH_TEMPLATE = "%s/annotations/%s.json"

    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 5.0f

    const val DEFAULT_SCROLL_DURATION_MS = 30_000L
    const val MAX_UNDO_STACK_SIZE = 20
}
