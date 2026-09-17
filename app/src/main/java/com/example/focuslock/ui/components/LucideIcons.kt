package com.example.focuslock.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Thin-stroke Lucide icons (https://lucide.dev, ISC license) used by the design, at stroke 1.5. */
object LucideIcons {
    val Home = icon("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .7-1.5l7-6a2 2 0 0 1 2.6 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Calendar = icon("M8 2v4", "M16 2v4", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 10h18")
    val List = icon("M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")
    val Sliders = icon("M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4")
    val ChevronLeft = icon("m15 18-6-6 6-6", strokeWidth = 1.7f)
    val ChevronRight = icon("m9 18 6-6-6-6")
    val ChevronUp = icon("m18 15-6-6-6 6", strokeWidth = 1.8f)
    val ChevronDown = icon("m6 9 6 6 6-6", strokeWidth = 1.8f)
    val Plus = icon("M5 12h14", "M12 5v14", strokeWidth = 1.8f)
    val Lock = icon("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4", strokeWidth = 1.6f)
    val Search = icon("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "m20 20-3.9-3.9", strokeWidth = 1.6f)
    val Copy = icon("M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1", strokeWidth = 1.6f)
    val Check = icon("M20 6 9 17l-5-5", strokeWidth = 3f)
    val Trash = icon("M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2")
    val Sun = icon("M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Moon = icon("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9")
    val System = icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20", "M12 18a6 6 0 0 0 0-12")

    private fun icon(vararg paths: String, strokeWidth: Float = 1.5f): ImageVector {
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
