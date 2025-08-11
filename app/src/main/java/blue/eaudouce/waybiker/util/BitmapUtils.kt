package blue.eaudouce.waybiker.util

// Taken from https://docs.mapbox.com/android/maps/examples/add-point-annotations/

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap

/**
 * Utility class to work with bitmaps and drawables.
 */
object BitmapUtils {

    /**
     * Convert given drawable id to bitmap.
     */
    fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        drawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

    fun drawableToBitmap(
        sourceDrawable: Drawable?,
        flipX: Boolean = false,
        flipY: Boolean = false,
        @ColorInt tint: Int? = null,
    ): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            // copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            tint?.let(drawable::setTint)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            canvas.scale(
                if (flipX) -1f else 1f,
                if (flipY) -1f else 1f,
                canvas.width / 2f,
                canvas.height / 2f
            )
            drawable.draw(canvas)
            bitmap
        }
    }
}