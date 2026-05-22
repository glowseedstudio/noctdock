package com.glowseed.noctdock.sender

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * Caches launcher icon bitmaps by package so Compose tiles do not re-decode icons when
 * screens leave composition (e.g. Library → Home). Entries invalidate when the app is updated.
 */
internal object SenderAppIconCache {
    private const val MAX_PACKAGES = 96
    private const val TARGET_PX = 128

    private val bitmaps = LruCache<String, ImageBitmap>(MAX_PACKAGES)

    /** Package version when we last loaded or cached an icon for that package. */
    private val iconVersions = hashMapOf<String, Long>()

    fun bitmapFor(context: Context, packageName: String, drawable: Drawable): ImageBitmap {
        val version = installedVersion(context.packageManager, packageName)
        synchronized(this) {
            val cached = bitmaps.get(packageName)
            if (cached != null && iconVersions[packageName] == version) {
                return cached
            }
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: TARGET_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: TARGET_PX
        val scale = TARGET_PX.toFloat() / maxOf(width, height).coerceAtLeast(1)
        val outW = (width * scale).roundToInt().coerceAtLeast(1)
        val outH = (height * scale).roundToInt().coerceAtLeast(1)
        val imageBitmap = drawable.toBitmap(outW, outH).asImageBitmap()
        synchronized(this) {
            bitmaps.put(packageName, imageBitmap)
            iconVersions[packageName] = version
        }
        return imageBitmap
    }

    fun canReuseDrawable(packageManager: PackageManager, packageName: String, existing: Drawable?): Boolean {
        if (existing == null) return false
        val current = installedVersion(packageManager, packageName)
        synchronized(this) {
            return iconVersions[packageName] == current
        }
    }

    fun trackLoadedIcon(packageManager: PackageManager, packageName: String) {
        val version = installedVersion(packageManager, packageName)
        synchronized(this) {
            iconVersions[packageName] = version
        }
    }

    fun clear() {
        synchronized(this) {
            bitmaps.evictAll()
            iconVersions.clear()
        }
    }

    private fun installedVersion(packageManager: PackageManager, packageName: String): Long = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
    }.getOrDefault(-1L)
}
