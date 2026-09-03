package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream

data class IconPreset(
    val id: String,
    val name: String,
    val symbolChar: String,
    val defaultBgColor: Int
)

object IconHelper {

    val presets = listOf(
        IconPreset("web", "Globe / Web", "🌐", Color.parseColor("#2563EB")),
        IconPreset("book", "Documentation", "📖", Color.parseColor("#0D9488")),
        IconPreset("code", "Developer / Code", "💻", Color.parseColor("#EA580C")),
        IconPreset("dashboard", "Dashboard / Analytics", "📊", Color.parseColor("#4F46E5")),
        IconPreset("tools", "Utility / Tools", "⚙️", Color.parseColor("#475569")),
        IconPreset("shopping", "Store / Shop", "🛍️", Color.parseColor("#DB2777")),
        IconPreset("news", "News / Articles", "📰", Color.parseColor("#D97706")),
        IconPreset("media", "Media / Video", "🎬", Color.parseColor("#DC2626")),
        IconPreset("rocket", "Productivity / App", "🚀", Color.parseColor("#7C3AED")),
        IconPreset("cloud", "Cloud Service", "☁️", Color.parseColor("#0284C7"))
    )

    fun getPreset(id: String): IconPreset {
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }

    fun renderIconBitmap(presetId: String, size: Int = 192, customColor: Int? = null): Bitmap {
        val preset = getPreset(presetId)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val primaryColor = customColor ?: preset.defaultBgColor
        val darkColor = darkenColor(primaryColor, 0.75f)

        // Gradient rounded background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                primaryColor, darkColor,
                Shader.TileMode.CLAMP
            )
        }
        val cornerRadius = size * 0.22f
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Subtle inner border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 40
            style = Paint.Style.STROKE
            strokeWidth = size * 0.02f
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw symbol
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.48f
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(preset.symbolChar, size / 2f, size / 2f - yOffset, textPaint)

        return bitmap
    }

    fun renderCustomBitmap(source: Bitmap, size: Int = 192): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val minDim = minOf(source.width, source.height)
        val cropX = (source.width - minDim) / 2
        val cropY = (source.height - minDim) / 2
        val cropped = Bitmap.createBitmap(source, cropX, cropY, minDim, minDim)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)

        val cornerRadius = size * 0.22f
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                cornerRadius, cornerRadius,
                Path.Direction.CW
            )
        }
        canvas.clipPath(path)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        return output
    }

    fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
