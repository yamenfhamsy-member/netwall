package com.netwall.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy per-app icon loader with an in-memory LRU cache.
 * Icons are resolved on demand when a row becomes visible instead of
 * scanning every icon upfront (which used to freeze the list for seconds).
 */
object IconLoader {

    private val cache = object : LruCache<String, ImageBitmap>(128) {}

    @Synchronized
    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cached(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toImageBitmapCompat()?.also {
                    synchronized(this@IconLoader) { cache.put(packageName, it) }
                }
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun Drawable.toImageBitmapCompat(): ImageBitmap? {
        return try {
            if (this is BitmapDrawable && bitmap != null) {
                return bitmap.asImageBitmap()
            }
            val width = if (intrinsicWidth > 0) intrinsicWidth else 96
            val height = if (intrinsicHeight > 0) intrinsicHeight else 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
