package com.youthlin.bingwallpaper.data

import android.os.Build

internal object GalleryStorage {
    fun canWriteGalleryOn(sdkInt: Int, hasLegacyWritePermission: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.Q || hasLegacyWritePermission

    fun usableContentUriPath(
        path: String,
        validateUri: Boolean,
        isReadableImageUri: (String) -> Boolean
    ): Boolean {
        if (!path.startsWith("content://")) return false
        return !validateUri || isReadableImageUri(path)
    }

    fun displayName(date: String, title: String, isPortrait: Boolean): String {
        val cleanTitle = sanitizeFileName(title)
            .takeIf { value -> value.any { it.isLetterOrDigit() } }
            ?.take(50)
            .orEmpty()
            .ifBlank { "wallpaper" }
        return if (isPortrait) {
            "${date}_Bing_${cleanTitle}_768x1366.jpg"
        } else {
            "${date}_Bing_$cleanTitle.jpg"
        }
    }

    private fun sanitizeFileName(value: String): String =
        value
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
}
