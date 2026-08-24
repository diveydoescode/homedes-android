package com.homedesign.android.domain.textures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.homedesign.android.domain.model.WallTexture
import com.homedesign.android.domain.sketch.SketchImagePrep
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

const val USER_TEXTURE_PREFIX = "user:"
private const val MAX_EDGE = 1024
private const val DEFAULT_TILE_CM = 100.0

data class UserTextureEntry(
    val handle: String,
    val name: String,
    val filePath: String,
    val tileWidthCM: Double = DEFAULT_TILE_CM,
    val tileHeightCM: Double = DEFAULT_TILE_CM,
)

/**
 * Gallery → `files/textures/<uuid>.jpg`. Handle = `user:<uuid>`;
 * [WallTexture.image] stores the absolute file path for Filament/Coil.
 */
object UserTextureStore {
    fun texturesDir(context: Context): File =
        File(context.filesDir, "textures").apply { mkdirs() }

    fun importFromUri(
        context: Context,
        uri: Uri,
        displayName: String? = null,
        tileWidthCM: Double = DEFAULT_TILE_CM,
        tileHeightCM: Double = DEFAULT_TILE_CM,
    ): UserTextureEntry? {
        val upright = SketchImagePrep.decodeUpright(context, uri) ?: return null
        val scaled = scaleBitmap(upright, MAX_EDGE)
        if (scaled !== upright) upright.recycle()
        val id = UUID.randomUUID().toString()
        val handle = "$USER_TEXTURE_PREFIX$id"
        val out = File(texturesDir(context), "$id.jpg")
        return try {
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            scaled.recycle()
            UserTextureEntry(
                handle = handle,
                name = displayName?.takeIf { it.isNotBlank() } ?: "Imported",
                filePath = out.absolutePath,
                tileWidthCM = tileWidthCM,
                tileHeightCM = tileHeightCM,
            )
        } catch (_: Exception) {
            scaled.recycle()
            runCatching { out.delete() }
            null
        }
    }

    fun toWallTexture(entry: UserTextureEntry): WallTexture =
        WallTexture(
            catalogID = entry.handle,
            name = entry.name,
            image = entry.filePath,
            width = entry.tileWidthCM,
            height = entry.tileHeightCM,
        )

    fun isUserPath(image: String?): Boolean {
        if (image.isNullOrBlank()) return false
        return image.startsWith(USER_TEXTURE_PREFIX) ||
            image.contains("${File.separator}textures${File.separator}") ||
            image.contains("/textures/")
    }

    private fun scaleBitmap(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        val nh = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}

/** Decode without EXIF helper when SketchImagePrep is unavailable to callers. */
fun decodeUserTextureFile(path: String): Bitmap? =
    BitmapFactory.decodeFile(path)
