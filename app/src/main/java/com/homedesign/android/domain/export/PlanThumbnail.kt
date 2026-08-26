package com.homedesign.android.domain.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.model.Home
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Best-effort plan preview: paper background, light room fills, black wall footprints.
 * Stored as JPEG on [com.homedesign.android.domain.project.ProjectRepository.saveHome].
 */
object PlanThumbnail {
    const val DEFAULT_SIZE_PX = 512
    const val JPEG_QUALITY = 82

    private const val PAPER = 0xFFFFFFFF.toInt()
    private const val ROOM_FILL = 0xFFD4E6F6.toInt()
    private const val INK = 0xFF1A1A1A.toInt()
    private const val PAD_FRAC = 0.08

    fun renderJpeg(home: Home, sizePx: Int = DEFAULT_SIZE_PX): ByteArray? {
        val levelId = home.selectedLevelID ?: home.levels.firstOrNull()?.id
        val bounds = computePlanBounds(home, levelId) ?: return null
        val planW = max(bounds.maxX - bounds.minX, 1.0)
        val planH = max(bounds.maxY - bounds.minY, 1.0)
        val pad = max(planW, planH) * PAD_FRAC
        val minX = bounds.minX - pad
        val minY = bounds.minY - pad
        val maxX = bounds.maxX + pad
        val maxY = bounds.maxY + pad
        val worldW = max(maxX - minX, 1.0)
        val worldH = max(maxY - minY, 1.0)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        val scale = min(sizePx / worldW, sizePx / worldH).toFloat()
        val originX = ((sizePx - worldW * scale) / 2.0).toFloat()
        val originY = ((sizePx - worldH * scale) / 2.0).toFloat()

        fun fx(x: Double): Float = originX + ((x - minX) * scale).toFloat()
        fun fy(y: Double): Float = originY + ((y - minY) * scale).toFloat()

        val roomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ROOM_FILL
        }
        val wallFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = PAPER
        }
        val wallStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = INK
            strokeWidth = 1.6f
        }
        val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0x8C1A1A1A.toInt()
            strokeWidth = 0.9f
        }

        for (room in home.rooms) {
            if (levelId != null && room.level != null && room.level != levelId) continue
            if (room.points.size < 3) continue
            val path = Path()
            val first = room.points.first()
            path.moveTo(fx(first.x), fy(first.y))
            for (i in 1 until room.points.size) {
                val p = room.points[i]
                path.lineTo(fx(p.x), fy(p.y))
            }
            path.close()
            canvas.drawPath(path, roomPaint)
        }

        for (wall in home.walls) {
            if (levelId != null && wall.level != null && wall.level != levelId) continue
            val outline = if (ArcWallGeometry.isCurved(wall)) {
                ArcWallGeometry.footprint(wall)
            } else {
                WallGeometry.unjoinedOutline(wall)
            }
            if (outline.size < 3) continue
            val path = Path()
            path.moveTo(fx(outline[0].x), fy(outline[0].y))
            for (i in 1 until outline.size) {
                path.lineTo(fx(outline[i].x), fy(outline[i].y))
            }
            path.close()
            canvas.drawPath(path, wallFillPaint)
            drawThumbHatch(canvas, path, hatchPaint, scale)
            canvas.drawPath(path, wallStrokePaint)
        }

        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        if (!ok) return null
        return out.toByteArray()
    }

    private fun drawThumbHatch(canvas: Canvas, path: Path, paint: Paint, pxPerCm: Float) {
        val box = RectF()
        path.computeBounds(box, true)
        if (box.width() <= 1f || box.height() <= 1f) return
        val spacing = (8f * pxPerCm).coerceAtLeast(4f)
        val diag = sqrt(box.width() * box.width() + box.height() * box.height())
        val half = diag / 2f
        val cx = box.centerX()
        val cy = box.centerY()
        val angle = PI / 4
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val nx = -dy
        val ny = dx
        canvas.save()
        canvas.clipPath(path)
        var offset = -half
        while (offset <= half) {
            val mx = cx + nx * offset
            val my = cy + ny * offset
            canvas.drawLine(
                mx - dx * half,
                my - dy * half,
                mx + dx * half,
                my + dy * half,
                paint,
            )
            offset += spacing
        }
        canvas.restore()
    }
}
