package com.youthlin.bingwallpaper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun galleryPermissionToRequest(context: Context, willUseGallery: Boolean = true): String? {
    return galleryReadPermissionToRequest(context, willUseGallery)
}

fun galleryReadPermissionsToRequest(context: Context, willUseGallery: Boolean = true): Array<String> {
    val permission = galleryReadPermissionToRequest(context, willUseGallery) ?: return emptyArray()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        permission == Manifest.permission.READ_MEDIA_IMAGES
    ) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else {
        arrayOf(permission)
    }
}

fun galleryReadPermissionToRequest(context: Context, willUseGallery: Boolean = true): String? {
    if (!willUseGallery) return null
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) !=
                PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED -> Manifest.permission.READ_MEDIA_IMAGES
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }
}

fun galleryWritePermissionToRequest(context: Context, willWriteGallery: Boolean): String? {
    if (!willWriteGallery) return null
    return when {
        needsLegacyStoragePermission(context) -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        else -> null
    }
}

private fun needsLegacyStoragePermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
}
