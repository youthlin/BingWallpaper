package com.youthlin.bingwallpaper.data

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryStorageTest {
    @Test
    fun displayName_sanitizesUnsafeTitleCharacters() {
        val displayName = GalleryStorage.displayName(
            date = "20260711",
            title = "  a/b\\c:d*e?f\"g<h>i|j\r\n\t  k  ",
            isPortrait = false
        )

        assertEquals("20260711_Bing_a_b_c_d_e_f_g_h_i_j_ k.jpg", displayName)
    }

    @Test
    fun displayName_usesFallbackWhenTitleHasNoSafeCharacters() {
        assertEquals(
            "20260711_Bing_wallpaper_768x1366.jpg",
            GalleryStorage.displayName(
                date = "20260711",
                title = " .\n\t/\\. ",
                isPortrait = true
            )
        )
    }

    @Test
    fun canWriteGalleryOn_requiresLegacyWritePermissionBeforeAndroidQ() {
        assertFalse(
            GalleryStorage.canWriteGalleryOn(
                sdkInt = Build.VERSION_CODES.P,
                hasLegacyWritePermission = false
            )
        )
        assertTrue(
            GalleryStorage.canWriteGalleryOn(
                sdkInt = Build.VERSION_CODES.P,
                hasLegacyWritePermission = true
            )
        )
    }

    @Test
    fun canWriteGalleryOn_allowsScopedStorageOnAndroidQAndAbove() {
        assertTrue(
            GalleryStorage.canWriteGalleryOn(
                sdkInt = Build.VERSION_CODES.Q,
                hasLegacyWritePermission = false
            )
        )
    }
}
