package com.example.customgalleryviewer.data

enum class GestureType {
    DOUBLE_TAP_LEFT,
    DOUBLE_TAP_RIGHT,
    DOUBLE_TAP_CENTER,
    SWIPE_LEFT_VERTICAL,
    SWIPE_RIGHT_VERTICAL,
    SWIPE_HORIZONTAL,
    LONG_PRESS
}

enum class GestureAction(val label: String) {
    PREVIOUS("Previous"),
    NEXT("Next"),
    TOGGLE_CONTROLS("Toggle Controls"),
    VOLUME("Volume"),
    BRIGHTNESS("Brightness"),
    SEEK("Seek"),
    ACTION_MENU("Action Menu"),
    NONE("None")
}
