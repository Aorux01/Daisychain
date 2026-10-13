package com.daisychain.app.run

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.daisychain.app.ui.editor.actionIconFor
import com.daisychain.app.ui.library.colorFor

/** Pictures drawn from a shortcut's icon and colour: for the launcher and for the widget. */
internal object ShortcutArt {
    /**
     * The icon on its colour, as a square the launcher crops to its own shape.
     * The glyph stays in the middle half so no launcher mask can cut it.
     */
    fun launcherIcon(iconKey: String, colorKey: String): Bitmap {
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colorFor(colorKey).toArgb())
        drawGlyph(canvas, iconKey, size * 0.25f, size * 0.25f, size * 0.5f)
        return bitmap
    }

    /** A rounded tile with the icon and the name, for a widget. */
    fun tile(name: String, iconKey: String, colorKey: String, size: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(colorKey).toArgb() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.18f, size * 0.18f, background)
        drawGlyph(canvas, iconKey, size * 0.08f, size * 0.08f, size * 0.30f)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.11f
            isFakeBoldText = true
        }
        // The name, on at most two lines, at the bottom.
        val words = name.split(' ')
        val lines = mutableListOf("")
        for (w in words) {
            val candidate = (lines.last() + " " + w).trim()
            if (text.measureText(candidate) <= size * 0.84f || lines.last().isEmpty()) lines[lines.lastIndex] = candidate else lines += w
        }
        val shown = lines.take(2).mapIndexed { i, l -> if (i == 1 && lines.size > 2) "$l…" else l }
        shown.forEachIndexed { i, line ->
            val fitted = if (text.measureText(line) > size * 0.84f) line.take(14) + "…" else line
            canvas.drawText(fitted, size * 0.08f, size * (0.80f - 0.12f * (shown.size - 1 - i)), text)
        }
        return bitmap
    }

    private fun drawGlyph(canvas: Canvas, iconKey: String, left: Float, top: Float, side: Float) {
        val icon = actionIconFor(iconKey)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.save()
        canvas.translate(left, top)
        val scale = side / icon.viewportWidth
        canvas.scale(scale, scale)
        drawGroup(icon.root, canvas, paint)
        canvas.restore()
    }

    private fun drawGroup(group: VectorGroup, canvas: Canvas, paint: Paint) {
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(node, canvas, paint)
                is VectorPath -> {
                    val path: Path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}
