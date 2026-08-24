package com.homedesign.android.domain.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CropRectPx(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Decode EXIF-upright, crop in pixel space, downscale long edge to [TARGET_LONG_EDGE],
 * encode JPEG at [JPEG_QUALITY]. Mirrors web `imagePreparer` / crop confirm path.
 */
object SketchImagePrep {
    fun prepareJpeg(
        context: Context,
        uri: Uri,
        cropInDisplayed: android.graphics.RectF?,
        displayedWidth: Float,
        displayedHeight: Float,
    ): ByteArray {
        val upright = decodeUpright(context, uri)
            ?: throw SketchApiException(SketchErrorCode.Invalid, SketchCopy.HEIC_UNREADABLE)
        try {
            val crop = if (
                cropInDisplayed != null &&
                displayedWidth > 1f &&
                displayedHeight > 1f
            ) {
                cropPixelRect(
                    cropInDisplayed,
                    displayedWidth,
                    displayedHeight,
                    upright.width,
                    upright.height,
                )
            } else {
                CropRectPx(0, 0, upright.width, upright.height)
            }
            val cropped = Bitmap.createBitmap(
                upright,
                crop.left.coerceIn(0, upright.width - 1),
                crop.top.coerceIn(0, upright.height - 1),
                crop.width.coerceAtMost(upright.width - crop.left).coerceAtLeast(1),
                crop.height.coerceAtMost(upright.height - crop.top).coerceAtLeast(1),
            )
            val scaled = scaleLongEdge(cropped, SketchConstants.TARGET_LONG_EDGE)
            if (cropped !== upright && cropped !== scaled) cropped.recycle()
            val bytes = encodeJpeg(scaled, SketchConstants.JPEG_QUALITY)
            if (scaled !== upright) scaled.recycle()
            if (bytes.size > SketchConstants.MAX_UPLOAD_BYTES) {
                throw SketchApiException(SketchErrorCode.Http, SketchCopy.ERROR_413, status = 413)
            }
            return bytes
        } finally {
            upright.recycle()
        }
    }

    fun decodeUpright(context: Context, uri: Uri): Bitmap? {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching { ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            ) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return raw
        }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun cropPixelRect(
        cropInDisplayed: android.graphics.RectF,
        displayedW: Float,
        displayedH: Float,
        pixelW: Int,
        pixelH: Int,
    ): CropRectPx {
        val sx = pixelW / displayedW
        val sy = pixelH / displayedH
        val left = (cropInDisplayed.left * sx).toInt().coerceIn(0, pixelW - 1)
        val top = (cropInDisplayed.top * sy).toInt().coerceIn(0, pixelH - 1)
        val right = (cropInDisplayed.right * sx).toInt().coerceIn(left + 1, pixelW)
        val bottom = (cropInDisplayed.bottom * sy).toInt().coerceIn(top + 1, pixelH)
        return CropRectPx(left, top, right - left, bottom - top)
    }

    private fun scaleLongEdge(src: Bitmap, longEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= longEdge) return src
        val scale = longEdge.toFloat() / longest
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
