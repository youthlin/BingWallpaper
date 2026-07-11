package com.youthlin.bingwallpaper.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRepositoryTest {
    @Test
    fun storedContentUriWithoutValidationStaysUsableForSyncUiPath() {
        assertTrue(
            GalleryStorage.usableContentUriPath(
                path = "content://media/external/images/media/42",
                validateUri = false,
                isReadableImageUri = { false }
            )
        )
    }

    @Test
    fun storedContentUriWithValidationRequiresReadableImage() {
        assertFalse(
            GalleryStorage.usableContentUriPath(
                path = "content://media/external/images/media/42",
                validateUri = true,
                isReadableImageUri = { false }
            )
        )
        assertTrue(
            GalleryStorage.usableContentUriPath(
                path = "content://media/external/images/media/42",
                validateUri = true,
                isReadableImageUri = { true }
            )
        )
    }
}
