package com.cleantv.launcher

/**
 * Debug-build switches for profiling on the TV, set from launch extras, e.g.
 * `adb shell am start -n com.cleantv.launcher/.MainActivity --ez perf_static_wallpaper true --ez perf_no_blur true`
 */
object PerfFlags {
    var staticWallpaper = false
    var noBlur = false
}
