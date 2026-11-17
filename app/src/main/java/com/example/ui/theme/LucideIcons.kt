package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Unified monochrome Lucide-style vector icon registry for ZELKAI.
 *
 * Every icon uses a consistent 24x24dp viewport, transparent fill,
 * 1.75f stroke width, and rounded caps & joins so `Icon(..., tint = ...)`
 * tints cleanly across Light and Dark themes.
 */
object LucideIcons {

    private const val STROKE_WIDTH = 1.75f

    private inline fun lucideIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = "Lucide.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

    private inline fun ImageVector.Builder.strokePath(
        pathBuilder: PathBuilder.() -> Unit
    ) {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder
        )
    }

    private fun PathBuilder.circlePath(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, cx + r, cy)
        close()
    }

    private fun PathBuilder.roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val cr = r.coerceAtMost(minOf(w, h) / 2f)
        if (cr <= 0f) {
            moveTo(x, y)
            lineTo(x + w, y)
            lineTo(x + w, y + h)
            lineTo(x, y + h)
            close()
        } else {
            moveTo(x + cr, y)
            lineTo(x + w - cr, y)
            arcTo(cr, cr, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w, y + cr)
            lineTo(x + w, y + h - cr)
            arcTo(cr, cr, 0f, isMoreThanHalf = false, isPositiveArc = true, x + w - cr, y + h)
            lineTo(x + cr, y + h)
            arcTo(cr, cr, 0f, isMoreThanHalf = false, isPositiveArc = true, x, y + h - cr)
            lineTo(x, y + cr)
            arcTo(cr, cr, 0f, isMoreThanHalf = false, isPositiveArc = true, x + cr, y)
            close()
        }
    }

    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y)
        lineTo(x + 0.01f, y)
    }

    // --- Navigation & Layout ---

    val Menu: ImageVector by lazy {
        lucideIcon("Menu") {
            strokePath {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }
    }

    val PanelLeft: ImageVector by lazy {
        lucideIcon("PanelLeft") {
            strokePath {
                roundRectPath(3f, 3f, 18f, 18f, 2f)
                moveTo(9f, 3f); lineTo(9f, 21f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucideIcon("Plus") {
            strokePath {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }
    val Add: ImageVector get() = Plus

    val X: ImageVector by lazy {
        lucideIcon("X") {
            strokePath {
                moveTo(18f, 6f); lineTo(6f, 18f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }
    }
    val Close: ImageVector get() = X

    val ChevronRight: ImageVector by lazy {
        lucideIcon("ChevronRight") {
            strokePath {
                moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        lucideIcon("ChevronLeft") {
            strokePath {
                moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        lucideIcon("ChevronDown") {
            strokePath {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }
    }

    val ChevronUp: ImageVector by lazy {
        lucideIcon("ChevronUp") {
            strokePath {
                moveTo(18f, 15f); lineTo(12f, 9f); lineTo(6f, 15f)
            }
        }
    }

    val ArrowDown: ImageVector by lazy {
        lucideIcon("ArrowDown") {
            strokePath {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(19f, 12f); lineTo(12f, 19f); lineTo(5f, 12f)
            }
        }
    }

    val ArrowUp: ImageVector by lazy {
        lucideIcon("ArrowUp") {
            strokePath {
                moveTo(12f, 19f); lineTo(12f, 5f)
                moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f)
            }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        lucideIcon("ArrowLeft") {
            strokePath {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
            }
        }
    }

    val ArrowRight: ImageVector by lazy {
        lucideIcon("ArrowRight") {
            strokePath {
                moveTo(5f, 12f); lineTo(19f, 12f)
                moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f)
            }
        }
    }

    val ArrowUpLeft: ImageVector by lazy {
        lucideIcon("ArrowUpLeft") {
            strokePath {
                moveTo(17f, 17f); lineTo(7f, 7f)
                moveTo(7f, 17f); lineTo(7f, 7f); lineTo(17f, 7f)
            }
        }
    }

    val ArrowUpDown: ImageVector by lazy {
        lucideIcon("ArrowUpDown") {
            strokePath {
                moveTo(21f, 16f); lineTo(17f, 20f); lineTo(13f, 16f)
                moveTo(17f, 20f); lineTo(17f, 4f)
                moveTo(3f, 8f); lineTo(7f, 4f); lineTo(11f, 8f)
                moveTo(7f, 4f); lineTo(7f, 20f)
            }
        }
    }

    val MoreHorizontal: ImageVector by lazy {
        lucideIcon("MoreHorizontal") {
            strokePath {
                circlePath(12f, 12f, 1f)
                circlePath(19f, 12f, 1f)
                circlePath(5f, 12f, 1f)
            }
        }
    }

    val MoreVertical: ImageVector by lazy {
        lucideIcon("MoreVertical") {
            strokePath {
                circlePath(12f, 12f, 1f)
                circlePath(12f, 5f, 1f)
                circlePath(12f, 19f, 1f)
            }
        }
    }

    // --- Chat, Composer & Actions ---

    val MessageSquare: ImageVector by lazy {
        lucideIcon("MessageSquare") {
            strokePath {
                moveTo(21f, 15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 17f)
                lineTo(7f, 17f)
                lineTo(3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
                close()
            }
        }
    }

    val Send: ImageVector by lazy {
        lucideIcon("Send") {
            strokePath {
                moveTo(22f, 2f); lineTo(11f, 13f)
                moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); lineTo(22f, 2f)
                close()
            }
        }
    }

    val Mic: ImageVector by lazy {
        lucideIcon("Mic") {
            strokePath {
                roundRectPath(9f, 2f, 6f, 12f, 3f)
                moveTo(19f, 10f); lineTo(19f, 12f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 12f)
                lineTo(5f, 10f)
                moveTo(12f, 19f); lineTo(12f, 22f)
                moveTo(8f, 22f); lineTo(16f, 22f)
            }
        }
    }

    val AudioLines: ImageVector by lazy {
        lucideIcon("AudioLines") {
            strokePath {
                moveTo(2f, 10f); lineTo(2f, 13f)
                moveTo(6f, 6f); lineTo(6f, 17f)
                moveTo(10f, 3f); lineTo(10f, 21f)
                moveTo(14f, 8f); lineTo(14f, 15f)
                moveTo(18f, 5f); lineTo(18f, 18f)
                moveTo(22f, 10f); lineTo(22f, 13f)
            }
        }
    }
    val Waveform: ImageVector get() = AudioLines

    val Paperclip: ImageVector by lazy {
        lucideIcon("Paperclip") {
            strokePath {
                moveTo(21.44f, 11.05f)
                lineTo(12.25f, 20.24f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.76f, 11.76f)
                lineTo(12.95f, 2.57f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.61f, 8.22f)
                lineTo(9.41f, 17.41f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.59f, 14.59f)
                lineTo(15.07f, 6.1f)
            }
        }
    }
    val AttachFile: ImageVector get() = Paperclip

    val Copy: ImageVector by lazy {
        lucideIcon("Copy") {
            strokePath {
                roundRectPath(9f, 9f, 13f, 13f, 2f)
                moveTo(5f, 15f)
                lineTo(4f, 15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 13f)
                lineTo(2f, 4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 2f)
                lineTo(13f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 4f)
                lineTo(15f, 5f)
            }
        }
    }

    val Clipboard: ImageVector by lazy {
        lucideIcon("Clipboard") {
            strokePath {
                roundRectPath(8f, 2f, 8f, 4f, 1f)
                moveTo(16f, 4f); lineTo(18f, 4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 6f)
                lineTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 22f)
                lineTo(6f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 20f)
                lineTo(4f, 6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6f, 4f)
                lineTo(8f, 4f)
            }
        }
    }
    val Paste: ImageVector get() = Clipboard

    val Pencil: ImageVector by lazy {
        lucideIcon("Pencil") {
            strokePath {
                moveTo(17f, 3f)
                arcTo(2.83f, 2.83f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 7f)
                lineTo(7.5f, 20.5f)
                lineTo(2f, 22f)
                lineTo(3.5f, 16.5f)
                close()
                moveTo(15f, 5f); lineTo(19f, 9f)
            }
        }
    }
    val Edit: ImageVector get() = Pencil

    val Trash2: ImageVector by lazy {
        lucideIcon("Trash2") {
            strokePath {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(19f, 6f); lineTo(19f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 22f)
                lineTo(7f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 20f)
                lineTo(5f, 6f)
                moveTo(8f, 6f); lineTo(8f, 4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 2f)
                lineTo(14f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 4f)
                lineTo(16f, 6f)
                moveTo(10f, 11f); lineTo(10f, 17f)
                moveTo(14f, 11f); lineTo(14f, 17f)
            }
        }
    }
    val Delete: ImageVector get() = Trash2

    val Eraser: ImageVector by lazy {
        lucideIcon("Eraser") {
            strokePath {
                moveTo(7f, 21f); lineTo(2.7f, 16.7f)
                arcTo(2.41f, 2.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.7f, 13.3f)
                lineTo(12.3f, 3.7f)
                arcTo(2.41f, 2.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.7f, 3.7f)
                lineTo(20.3f, 8.3f)
                arcTo(2.41f, 2.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.3f, 11.7f)
                lineTo(11f, 21f)
                close()
                moveTo(22f, 21f); lineTo(7f, 21f)
                moveTo(5f, 11f); lineTo(13f, 19f)
            }
        }
    }
    val DeleteSweep: ImageVector get() = Eraser

    val Pin: ImageVector by lazy {
        lucideIcon("Pin") {
            strokePath {
                moveTo(12f, 17f); lineTo(12f, 22f)
                moveTo(5f, 17f); lineTo(19f, 17f)
                lineTo(17.5f, 14f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 12.6f)
                lineTo(17f, 7f)
                lineTo(18.5f, 4.5f)
                lineTo(18.5f, 3f)
                lineTo(5.5f, 3f)
                lineTo(5.5f, 4.5f)
                lineTo(7f, 7f)
                lineTo(7f, 12.6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.5f, 14f)
                close()
            }
        }
    }

    val PinOff: ImageVector by lazy {
        lucideIcon("PinOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(12f, 17f); lineTo(12f, 22f)
                moveTo(9f, 9f); lineTo(9f, 12.6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 14f)
                lineTo(5f, 17f)
                lineTo(17f, 17f)
                moveTo(15f, 9.34f); lineTo(15f, 7f)
                lineTo(16.5f, 4.5f)
                lineTo(16.5f, 3f)
                lineTo(8.5f, 3f)
            }
        }
    }

    val Search: ImageVector by lazy {
        lucideIcon("Search") {
            strokePath {
                circlePath(11f, 11f, 8f)
                moveTo(21f, 21f); lineTo(16.65f, 16.65f)
            }
        }
    }

    val Share2: ImageVector by lazy {
        lucideIcon("Share2") {
            strokePath {
                circlePath(18f, 5f, 3f)
                circlePath(6f, 12f, 3f)
                circlePath(18f, 19f, 3f)
                moveTo(8.59f, 13.51f); lineTo(15.42f, 17.49f)
                moveTo(15.41f, 6.51f); lineTo(8.59f, 10.49f)
            }
        }
    }
    val Share: ImageVector get() = Share2

    val ThumbsUp: ImageVector by lazy {
        lucideIcon("ThumbsUp") {
            strokePath {
                moveTo(7f, 10f); lineTo(7f, 22f)
                moveTo(14f, 9f); lineTo(14f, 5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11f, 2f)
                lineTo(7f, 10f)
                lineTo(3f, 10f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 11f)
                lineTo(2f, 21f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 22f)
                lineTo(17.7f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.67f, 20.3f)
                lineTo(20.93f, 12.3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18.96f, 10f)
                close()
            }
        }
    }

    val ThumbsDown: ImageVector by lazy {
        lucideIcon("ThumbsDown") {
            strokePath {
                moveTo(17f, 14f); lineTo(17f, 2f)
                moveTo(10f, 15f); lineTo(10f, 19f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13f, 22f)
                lineTo(17f, 14f)
                lineTo(21f, 14f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 13f)
                lineTo(22f, 3f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 2f)
                lineTo(6.3f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.33f, 3.7f)
                lineTo(3.07f, 11.7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.04f, 14f)
                close()
            }
        }
    }

    val TextSelect: ImageVector by lazy {
        lucideIcon("TextSelect") {
            strokePath {
                moveTo(5f, 3f); lineTo(4f, 3f); arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 4f); lineTo(3f, 5f)
                moveTo(19f, 3f); lineTo(20f, 3f); arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 4f); lineTo(21f, 5f)
                moveTo(21f, 19f); lineTo(21f, 20f); arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 21f); lineTo(19f, 21f)
                moveTo(5f, 21f); lineTo(4f, 21f); arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 20f); lineTo(3f, 19f)
                moveTo(9f, 3f); lineTo(10f, 3f)
                moveTo(14f, 3f); lineTo(15f, 3f)
                moveTo(9f, 21f); lineTo(10f, 21f)
                moveTo(14f, 21f); lineTo(15f, 21f)
                moveTo(3f, 9f); lineTo(3f, 10f)
                moveTo(3f, 14f); lineTo(3f, 15f)
                moveTo(21f, 9f); lineTo(21f, 10f)
                moveTo(21f, 14f); lineTo(21f, 15f)
                moveTo(7f, 8f); lineTo(17f, 8f)
                moveTo(7f, 12f); lineTo(17f, 12f)
                moveTo(7f, 16f); lineTo(13f, 16f)
            }
        }
    }
    val SelectAll: ImageVector get() = TextSelect

    // --- Media / Execution Controls ---

    val Play: ImageVector by lazy {
        lucideIcon("Play") {
            strokePath {
                moveTo(6f, 4f); lineTo(20f, 12f); lineTo(6f, 20f); close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        lucideIcon("Pause") {
            strokePath {
                roundRectPath(6f, 4f, 4f, 16f, 1f)
                roundRectPath(14f, 4f, 4f, 16f, 1f)
            }
        }
    }

    val Square: ImageVector by lazy {
        lucideIcon("Square") {
            strokePath {
                roundRectPath(5f, 5f, 14f, 14f, 2f)
            }
        }
    }
    val Stop: ImageVector get() = Square

    val StopCircle: ImageVector by lazy {
        lucideIcon("StopCircle") {
            strokePath {
                circlePath(12f, 12f, 10f)
                roundRectPath(9f, 9f, 6f, 6f, 1f)
            }
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucideIcon("RefreshCw") {
            strokePath {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.36f, 5.64f)
                lineTo(21f, 8f)
                moveTo(21f, 3f); lineTo(21f, 8f); lineTo(16f, 8f)
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.64f, 18.36f)
                lineTo(3f, 16f)
                moveTo(8f, 16f); lineTo(3f, 16f); lineTo(3f, 21f)
            }
        }
    }
    val Sync: ImageVector get() = RefreshCw

    val RotateCcw: ImageVector by lazy {
        lucideIcon("RotateCcw") {
            strokePath {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 5.64f, 5.64f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
            }
        }
    }

    val History: ImageVector by lazy {
        lucideIcon("History") {
            strokePath {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 5.64f, 5.64f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
            }
        }
    }

    // --- Status & Semantic ---

    val Check: ImageVector by lazy {
        lucideIcon("Check") {
            strokePath {
                moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        lucideIcon("CheckCircle") {
            strokePath {
                circlePath(12f, 12f, 10f)
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
            }
        }
    }

    val Circle: ImageVector by lazy {
        lucideIcon("Circle") {
            strokePath {
                circlePath(12f, 12f, 10f)
            }
        }
    }

    val Info: ImageVector by lazy {
        lucideIcon("Info") {
            strokePath {
                circlePath(12f, 12f, 10f)
                moveTo(12f, 16f); lineTo(12f, 12f)
                dot(12f, 8f)
            }
        }
    }

    val HelpCircle: ImageVector by lazy {
        lucideIcon("HelpCircle") {
            strokePath {
                circlePath(12f, 12f, 10f)
                moveTo(9.09f, 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 10f)
                curveTo(15f, 12f, 12f, 13f, 12f, 13f)
                dot(12f, 17f)
            }
        }
    }

    val AlertCircle: ImageVector by lazy {
        lucideIcon("AlertCircle") {
            strokePath {
                circlePath(12f, 12f, 10f)
                moveTo(12f, 8f); lineTo(12f, 12f)
                dot(12f, 16f)
            }
        }
    }

    val AlertTriangle: ImageVector by lazy {
        lucideIcon("AlertTriangle") {
            strokePath {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.53f, 21f)
                lineTo(20.47f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22.18f, 18f)
                lineTo(13.71f, 3.86f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.29f, 3.86f)
                close()
                moveTo(12f, 9f); lineTo(12f, 13f)
                dot(12f, 17f)
            }
        }
    }

    val ListChecks: ImageVector by lazy {
        lucideIcon("ListChecks") {
            strokePath {
                moveTo(3f, 17f); lineTo(5f, 19f); lineTo(9f, 15f)
                moveTo(3f, 7f); lineTo(5f, 9f); lineTo(9f, 5f)
                moveTo(13f, 6f); lineTo(21f, 6f)
                moveTo(13f, 12f); lineTo(21f, 12f)
                moveTo(13f, 18f); lineTo(21f, 18f)
            }
        }
    }

    val Star: ImageVector by lazy {
        lucideIcon("Star") {
            strokePath {
                moveTo(12f, 2f)
                lineTo(15.09f, 8.26f)
                lineTo(22f, 9.27f)
                lineTo(17f, 14.14f)
                lineTo(18.18f, 21.02f)
                lineTo(12f, 17.77f)
                lineTo(5.82f, 21.02f)
                lineTo(7f, 14.14f)
                lineTo(2f, 9.27f)
                lineTo(8.91f, 8.26f)
                close()
            }
        }
    }

    val Heart: ImageVector by lazy {
        lucideIcon("Heart") {
            strokePath {
                moveTo(19f, 14f)
                curveTo(20.49f, 12.54f, 22f, 10.79f, 22f, 8.5f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.5f, 3f)
                curveTo(14.74f, 3f, 13.5f, 3.5f, 12f, 5f)
                curveTo(10.5f, 3.5f, 9.26f, 3f, 7.5f, 3f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 8.5f)
                curveTo(2f, 10.8f, 3.5f, 12.55f, 5f, 14f)
                lineTo(12f, 21f)
                close()
            }
        }
    }

    val BellRing: ImageVector by lazy {
        lucideIcon("BellRing") {
            strokePath {
                moveTo(6f, 8f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 8f)
                curveTo(18f, 15f, 21f, 17f, 21f, 17f)
                lineTo(3f, 17f)
                curveTo(3f, 17f, 6f, 15f, 6f, 8f)
                moveTo(10.3f, 21f)
                arcTo(1.94f, 1.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.7f, 21f)
                moveTo(4f, 2f)
                curveTo(2.5f, 3.5f, 2f, 5.2f, 2f, 7f)
                moveTo(20f, 2f)
                curveTo(21.5f, 3.5f, 22f, 5.2f, 22f, 7f)
            }
        }
    }
    val Bell: ImageVector get() = BellRing

    // --- AI, Terminal, Code & Tools ---

    val Sparkles: ImageVector by lazy {
        lucideIcon("Sparkles") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(13.8f, 8.2f)
                lineTo(19f, 10f)
                lineTo(13.8f, 11.8f)
                lineTo(12f, 17f)
                lineTo(10.2f, 11.8f)
                lineTo(5f, 10f)
                lineTo(10.2f, 8.2f)
                close()
                moveTo(19f, 16f); lineTo(19f, 20f)
                moveTo(17f, 18f); lineTo(21f, 18f)
                moveTo(5f, 3f); lineTo(5f, 6f)
                moveTo(3.5f, 4.5f); lineTo(6.5f, 4.5f)
            }
        }
    }
    val AutoAwesome: ImageVector get() = Sparkles

    val Brain: ImageVector by lazy {
        lucideIcon("Brain") {
            strokePath {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(12f, 5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6.2f, 6.2f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.2f, 11.8f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 17.5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19f)
                moveTo(12f, 5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.8f, 6.2f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.8f, 11.8f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 17.5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 19f)
                moveTo(8.5f, 9f); lineTo(10f, 10.5f)
                moveTo(15.5f, 9f); lineTo(14f, 10.5f)
                moveTo(8f, 14f); lineTo(10f, 14f)
                moveTo(16f, 14f); lineTo(14f, 14f)
            }
        }
    }

    val Bot: ImageVector by lazy {
        lucideIcon("Bot") {
            strokePath {
                moveTo(12f, 8f); lineTo(12f, 4f); lineTo(8f, 4f)
                roundRectPath(4f, 8f, 16f, 12f, 2f)
                moveTo(2f, 14f); lineTo(4f, 14f)
                moveTo(20f, 14f); lineTo(22f, 14f)
                moveTo(15f, 13f); lineTo(15f, 15f)
                moveTo(9f, 13f); lineTo(9f, 15f)
            }
        }
    }

    val Terminal: ImageVector by lazy {
        lucideIcon("Terminal") {
            strokePath {
                moveTo(4f, 17f); lineTo(10f, 11f); lineTo(4f, 5f)
                moveTo(12f, 19f); lineTo(20f, 19f)
            }
        }
    }

    val Code: ImageVector by lazy {
        lucideIcon("Code") {
            strokePath {
                moveTo(16f, 18f); lineTo(22f, 12f); lineTo(16f, 6f)
                moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
            }
        }
    }

    val Code2: ImageVector by lazy {
        lucideIcon("Code2") {
            strokePath {
                moveTo(18f, 16f); lineTo(22f, 12f); lineTo(18f, 8f)
                moveTo(6f, 8f); lineTo(2f, 12f); lineTo(6f, 16f)
                moveTo(14.5f, 4f); lineTo(9.5f, 20f)
            }
        }
    }

    val Zap: ImageVector by lazy {
        lucideIcon("Zap") {
            strokePath {
                moveTo(13f, 2f)
                lineTo(3f, 14f)
                lineTo(12f, 14f)
                lineTo(11f, 22f)
                lineTo(21f, 10f)
                lineTo(12f, 10f)
                close()
            }
        }
    }
    val Bolt: ImageVector get() = Zap

    val Cpu: ImageVector by lazy {
        lucideIcon("Cpu") {
            strokePath {
                roundRectPath(4f, 4f, 16f, 16f, 2f)
                roundRectPath(9f, 9f, 6f, 6f, 1f)
                moveTo(15f, 2f); lineTo(15f, 4f)
                moveTo(15f, 20f); lineTo(15f, 22f)
                moveTo(2f, 15f); lineTo(4f, 15f)
                moveTo(2f, 9f); lineTo(4f, 9f)
                moveTo(20f, 15f); lineTo(22f, 15f)
                moveTo(20f, 9f); lineTo(22f, 9f)
                moveTo(9f, 2f); lineTo(9f, 4f)
                moveTo(9f, 20f); lineTo(9f, 22f)
            }
        }
    }
    val Memory: ImageVector get() = Cpu

    val Wrench: ImageVector by lazy {
        lucideIcon("Wrench") {
            strokePath {
                moveTo(14.7f, 6.3f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14.7f, 7.7f)
                lineTo(16.3f, 9.3f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.7f, 9.3f)
                lineTo(21.47f, 5.53f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 13.53f, 13.47f)
                lineTo(6.62f, 20.38f)
                arcTo(2.12f, 2.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.62f, 17.38f)
                lineTo(10.53f, 10.47f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.47f, 2.53f)
                close()
            }
        }
    }
    val Build: ImageVector get() = Wrench

    val HardDrive: ImageVector by lazy {
        lucideIcon("HardDrive") {
            strokePath {
                moveTo(22f, 12f); lineTo(2f, 12f)
                moveTo(5.45f, 5.11f)
                lineTo(2f, 12f)
                lineTo(2f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 20f)
                lineTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 18f)
                lineTo(22f, 12f)
                lineTo(18.55f, 5.11f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.76f, 4f)
                lineTo(7.24f, 4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.45f, 5.11f)
                close()
                dot(6f, 16f)
                dot(10f, 16f)
            }
        }
    }

    val Gauge: ImageVector by lazy {
        lucideIcon("Gauge") {
            strokePath {
                moveTo(12f, 14f); lineTo(16f, 10f)
                moveTo(3.34f, 19f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 20.66f, 19f)
            }
        }
    }
    val Speed: ImageVector get() = Gauge

    val Activity: ImageVector by lazy {
        lucideIcon("Activity") {
            strokePath {
                moveTo(22f, 12f)
                lineTo(18f, 12f)
                lineTo(15f, 21f)
                lineTo(9f, 3f)
                lineTo(6f, 12f)
                lineTo(2f, 12f)
            }
        }
    }

    val BarChart2: ImageVector by lazy {
        lucideIcon("BarChart2") {
            strokePath {
                moveTo(18f, 20f); lineTo(18f, 10f)
                moveTo(12f, 20f); lineTo(12f, 4f)
                moveTo(6f, 20f); lineTo(6f, 14f)
            }
        }
    }
    val Analytics: ImageVector get() = BarChart2

    val WrapText: ImageVector by lazy {
        lucideIcon("WrapText") {
            strokePath {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(3f, 12f); lineTo(18f, 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 18f)
                lineTo(13f, 18f)
                moveTo(15f, 16f); lineTo(13f, 18f); lineTo(15f, 20f)
                moveTo(3f, 18f); lineTo(9f, 18f)
            }
        }
    }

    val GitBranch: ImageVector by lazy {
        lucideIcon("GitBranch") {
            strokePath {
                moveTo(6f, 3f); lineTo(6f, 15f)
                circlePath(18f, 6f, 3f)
                circlePath(6f, 18f, 3f)
                moveTo(18f, 9f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 18f)
            }
        }
    }

    val Github: ImageVector by lazy {
        lucideIcon("Github") {
            strokePath {
                moveTo(15f, 22f)
                lineTo(15f, 18f)
                curveTo(15f, 17f, 14.9f, 16.5f, 14.5f, 16f)
                curveTo(17.5f, 15.6f, 20f, 14.4f, 20f, 10.5f)
                curveTo(20f, 9.2f, 19.5f, 8f, 18.7f, 7.1f)
                curveTo(18.9f, 6.2f, 18.9f, 5.1f, 18.5f, 4f)
                curveTo(18.5f, 4f, 17.4f, 3.7f, 15f, 5.3f)
                curveTo(13f, 4.8f, 11f, 4.8f, 9f, 5.3f)
                curveTo(6.6f, 3.7f, 5.5f, 4f, 5.5f, 4f)
                curveTo(5.1f, 5.1f, 5.1f, 6.2f, 5.3f, 7.1f)
                curveTo(4.5f, 8f, 4f, 9.2f, 4f, 10.5f)
                curveTo(4f, 14.4f, 6.5f, 15.6f, 9.5f, 16f)
                curveTo(9.1f, 16.5f, 9f, 17f, 9f, 18f)
                lineTo(9f, 22f)
                moveTo(9f, 19f)
                curveTo(5.5f, 20.3f, 4.5f, 17.5f, 3f, 17f)
            }
        }
    }

    // --- Files & Folders ---

    val Folder: ImageVector by lazy {
        lucideIcon("Folder") {
            strokePath {
                moveTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 18f)
                lineTo(22f, 8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 6f)
                lineTo(12.1f, 6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.4f, 5.1f)
                lineTo(9.6f, 3.9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.9f, 3f)
                lineTo(4f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 5f)
                lineTo(2f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 20f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        lucideIcon("FolderOpen") {
            strokePath {
                moveTo(6f, 14f)
                lineTo(7.45f, 11.1f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.24f, 10f)
                lineTo(20f, 10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.92f, 12.56f)
                lineTo(20.14f, 18.56f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.22f, 20f)
                lineTo(4f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 18f)
                lineTo(2f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 3f)
                lineTo(7.9f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.6f, 3.9f)
                lineTo(10.4f, 5.1f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12.1f, 6f)
                lineTo(18f, 6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 8f)
                lineTo(20f, 10f)
            }
        }
    }

    val FolderPlus: ImageVector by lazy {
        lucideIcon("FolderPlus") {
            strokePath {
                moveTo(12f, 10f); lineTo(12f, 16f)
                moveTo(9f, 13f); lineTo(15f, 13f)
                moveTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 18f)
                lineTo(22f, 8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 6f)
                lineTo(12.1f, 6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.4f, 5.1f)
                lineTo(9.6f, 3.9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.9f, 3f)
                lineTo(4f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 5f)
                lineTo(2f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 20f)
                close()
            }
        }
    }

    val FileText: ImageVector by lazy {
        lucideIcon("FileText") {
            strokePath {
                moveTo(14f, 2f); lineTo(6f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 4f)
                lineTo(4f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 22f)
                lineTo(18f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 20f)
                lineTo(20f, 8f)
                close()
                moveTo(14f, 2f); lineTo(14f, 8f); lineTo(20f, 8f)
                moveTo(16f, 13f); lineTo(8f, 13f)
                moveTo(16f, 17f); lineTo(8f, 17f)
                moveTo(10f, 9f); lineTo(8f, 9f)
            }
        }
    }

    val FileUp: ImageVector by lazy {
        lucideIcon("FileUp") {
            strokePath {
                moveTo(14f, 2f); lineTo(6f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 4f)
                lineTo(4f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 22f)
                lineTo(18f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 20f)
                lineTo(20f, 8f)
                close()
                moveTo(14f, 2f); lineTo(14f, 8f); lineTo(20f, 8f)
                moveTo(12f, 18f); lineTo(12f, 12f)
                moveTo(9f, 15f); lineTo(12f, 12f); lineTo(15f, 15f)
            }
        }
    }

    val Download: ImageVector by lazy {
        lucideIcon("Download") {
            strokePath {
                moveTo(21f, 15f); lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
                lineTo(3f, 15f)
                moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
                moveTo(12f, 15f); lineTo(12f, 3f)
            }
        }
    }

    // --- Cloud, Network & Connectivity ---

    val Cloud: ImageVector by lazy {
        lucideIcon("Cloud") {
            strokePath {
                moveTo(17.5f, 19f)
                lineTo(7f, 19f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.1f, 9.08f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.7f, 11.2f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 19f)
                close()
            }
        }
    }

    val CloudCheck: ImageVector by lazy {
        lucideIcon("CloudCheck") {
            strokePath {
                moveTo(17.5f, 19f)
                lineTo(7f, 19f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.1f, 9.08f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.7f, 11.2f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 19f)
                close()
                moveTo(9f, 13.5f); lineTo(11f, 15.5f); lineTo(15f, 11.5f)
            }
        }
    }
    val CloudDone: ImageVector get() = CloudCheck

    val CloudDownload: ImageVector by lazy {
        lucideIcon("CloudDownload") {
            strokePath {
                moveTo(12f, 13f); lineTo(12f, 21f)
                moveTo(9f, 18f); lineTo(12f, 21f); lineTo(15f, 18f)
                moveTo(4.39f, 15.27f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.1f, 7.08f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.7f, 9.2f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 16.7f)
            }
        }
    }

    val CloudOff: ImageVector by lazy {
        lucideIcon("CloudOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(17.5f, 19f)
                lineTo(7f, 19f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.1f, 9.08f)
                moveTo(9.2f, 5.1f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.7f, 11.2f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 16.5f)
            }
        }
    }

    val CloudSync: ImageVector by lazy {
        lucideIcon("CloudSync") {
            strokePath {
                moveTo(4f, 14.899f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.71f, 8f)
                lineTo(17.5f, 8f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16.242f)
                moveTo(9f, 16f); lineTo(9f, 21f); lineTo(14f, 21f)
                moveTo(15f, 17f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 16f)
                moveTo(9f, 19f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 20f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        lucideIcon("Wifi") {
            strokePath {
                moveTo(5f, 12.55f); arcTo(11f, 11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 12.55f)
                moveTo(1.42f, 9f); arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22.58f, 9f)
                moveTo(8.53f, 16.11f); arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.47f, 16.11f)
                dot(12f, 20f)
            }
        }
    }

    val WifiOff: ImageVector by lazy {
        lucideIcon("WifiOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(8.53f, 16.11f); arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.5f, 15.3f)
                moveTo(10.72f, 5.05f); arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22.58f, 9f)
                moveTo(1.42f, 9f); arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.2f, 6.2f)
                moveTo(16.72f, 11.06f); arcTo(11f, 11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 12.55f)
                moveTo(5f, 12.55f); arcTo(11f, 11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.17f, 10.17f)
                dot(12f, 20f)
            }
        }
    }

    val Network: ImageVector by lazy {
        lucideIcon("Network") {
            strokePath {
                roundRectPath(16f, 16f, 6f, 6f, 1f)
                roundRectPath(2f, 16f, 6f, 6f, 1f)
                roundRectPath(9f, 2f, 6f, 6f, 1f)
                moveTo(5f, 16f); lineTo(5f, 12f); lineTo(19f, 12f); lineTo(19f, 16f)
                moveTo(12f, 12f); lineTo(12f, 8f)
            }
        }
    }
    val Hub: ImageVector get() = Network

    val ExternalLink: ImageVector by lazy {
        lucideIcon("ExternalLink") {
            strokePath {
                moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f)
                moveTo(10f, 14f); lineTo(21f, 3f)
                moveTo(18f, 13f); lineTo(18f, 19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
                lineTo(3f, 8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 6f)
                lineTo(11f, 6f)
            }
        }
    }

    val Globe: ImageVector by lazy {
        lucideIcon("Globe") {
            strokePath {
                circlePath(12f, 12f, 10f)
                moveTo(2f, 12f); lineTo(22f, 12f)
                moveTo(12f, 2f)
                curveTo(14.5f, 4.7f, 16f, 8.3f, 16f, 12f)
                curveTo(16f, 15.7f, 14.5f, 19.3f, 12f, 22f)
                curveTo(9.5f, 19.3f, 8f, 15.7f, 8f, 12f)
                curveTo(8f, 8.3f, 9.5f, 4.7f, 12f, 2f)
                close()
            }
        }
    }

    // --- Security, Auth & Identity ---

    val Lock: ImageVector by lazy {
        lucideIcon("Lock") {
            strokePath {
                roundRectPath(3f, 11f, 18f, 11f, 2f)
                moveTo(7f, 11f); lineTo(7f, 7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 7f)
                lineTo(17f, 11f)
            }
        }
    }

    val Unlock: ImageVector by lazy {
        lucideIcon("Unlock") {
            strokePath {
                roundRectPath(3f, 11f, 18f, 11f, 2f)
                moveTo(7f, 11f); lineTo(7f, 7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.9f, 6f)
            }
        }
    }

    val Key: ImageVector by lazy {
        lucideIcon("Key") {
            strokePath {
                circlePath(7.5f, 15.5f, 4.5f)
                moveTo(10.8f, 12.2f); lineTo(20f, 3f)
                moveTo(17f, 6f); lineTo(20f, 9f)
                moveTo(14.5f, 8.5f); lineTo(17f, 11f)
            }
        }
    }

    val KeyOff: ImageVector by lazy {
        lucideIcon("KeyOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                circlePath(7.5f, 15.5f, 4.5f)
                moveTo(14f, 9f); lineTo(20f, 3f)
                moveTo(17f, 6f); lineTo(20f, 9f)
            }
        }
    }

    val Shield: ImageVector by lazy {
        lucideIcon("Shield") {
            strokePath {
                moveTo(12f, 22f)
                curveTo(12f, 22f, 20f, 18f, 20f, 12f)
                lineTo(20f, 5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 12f)
                curveTo(4f, 18f, 12f, 22f, 12f, 22f)
                close()
            }
        }
    }

    val ShieldCheck: ImageVector by lazy {
        lucideIcon("ShieldCheck") {
            strokePath {
                moveTo(12f, 22f)
                curveTo(12f, 22f, 20f, 18f, 20f, 12f)
                lineTo(20f, 5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 12f)
                curveTo(4f, 18f, 12f, 22f, 12f, 22f)
                close()
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
            }
        }
    }

    val BadgeCheck: ImageVector by lazy {
        lucideIcon("BadgeCheck") {
            strokePath {
                circlePath(12f, 12f, 9f)
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
            }
        }
    }
    val Verified: ImageVector get() = BadgeCheck

    val Eye: ImageVector by lazy {
        lucideIcon("Eye") {
            strokePath {
                moveTo(2f, 12f)
                curveTo(3.6f, 7f, 7.5f, 4f, 12f, 4f)
                curveTo(16.5f, 4f, 20.4f, 7f, 22f, 12f)
                curveTo(20.4f, 17f, 16.5f, 20f, 12f, 20f)
                curveTo(7.5f, 20f, 3.6f, 17f, 2f, 12f)
                close()
                circlePath(12f, 12f, 3f)
            }
        }
    }

    val EyeOff: ImageVector by lazy {
        lucideIcon("EyeOff") {
            strokePath {
                moveTo(2f, 2f); lineTo(22f, 22f)
                moveTo(10.58f, 10.58f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.42f, 13.42f)
                moveTo(9.88f, 4.24f)
                curveTo(10.58f, 4.08f, 11.28f, 4f, 12f, 4f)
                curveTo(16.5f, 4f, 20.4f, 7f, 22f, 12f)
                curveTo(21.4f, 13.8f, 20.4f, 15.4f, 19.1f, 16.7f)
                moveTo(6.6f, 6.6f)
                curveTo(4.6f, 8f, 3f, 9.8f, 2f, 12f)
                curveTo(3.6f, 17f, 7.5f, 20f, 12f, 20f)
                curveTo(13.9f, 20f, 15.7f, 19.4f, 17.2f, 18.4f)
            }
        }
    }

    val User: ImageVector by lazy {
        lucideIcon("User") {
            strokePath {
                moveTo(19f, 21f); lineTo(19f, 19f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 15f)
                lineTo(9f, 15f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 19f)
                lineTo(5f, 21f)
                circlePath(12f, 7f, 4f)
            }
        }
    }

    val Mail: ImageVector by lazy {
        lucideIcon("Mail") {
            strokePath {
                roundRectPath(2f, 4f, 20f, 16f, 2f)
                moveTo(22f, 7f); lineTo(13.03f, 12.7f)
                arcTo(1.94f, 1.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.97f, 12.7f)
                lineTo(2f, 7f)
            }
        }
    }

    val LogIn: ImageVector by lazy {
        lucideIcon("LogIn") {
            strokePath {
                moveTo(15f, 3f); lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
                lineTo(15f, 21f)
                moveTo(10f, 17f); lineTo(15f, 12f); lineTo(10f, 7f)
                moveTo(15f, 12f); lineTo(3f, 12f)
            }
        }
    }

    val LogOut: ImageVector by lazy {
        lucideIcon("LogOut") {
            strokePath {
                moveTo(9f, 21f); lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
                lineTo(9f, 3f)
                moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
                moveTo(21f, 12f); lineTo(9f, 12f)
            }
        }
    }

    val Incognito: ImageVector by lazy {
        lucideIcon("Incognito") {
            strokePath {
                // Hat crown
                moveTo(5.8f, 10.5f)
                lineTo(7.9f, 4.2f)
                lineTo(12f, 5.4f)
                lineTo(16.1f, 4.2f)
                lineTo(18.2f, 10.5f)
                // Hat brim
                moveTo(3f, 11f); lineTo(21f, 11f)
                // Eyeglasses lenses & bridge
                circlePath(7.5f, 17.2f, 3.4f)
                circlePath(16.5f, 17.2f, 3.4f)
                moveTo(10.9f, 17.2f); lineTo(13.1f, 17.2f)
            }
        }
    }

    // --- Settings, Theme & Device ---

    val Settings: ImageVector by lazy {
        lucideIcon("Settings") {
            strokePath {
                circlePath(12f, 12f, 3f)
                moveTo(19.4f, 15f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.73f, 16.82f)
                lineTo(19.79f, 16.88f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.96f, 19.71f)
                lineTo(16.9f, 19.65f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15.08f, 19.32f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14.08f, 20.83f)
                lineTo(14.08f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.08f, 21f)
                lineTo(10.08f, 20.91f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 19.4f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.18f, 19.73f)
                lineTo(7.12f, 19.79f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.29f, 16.96f)
                lineTo(4.35f, 16.9f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.68f, 15.08f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.17f, 14.08f)
                lineTo(3f, 14.08f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 10.08f)
                lineTo(3.09f, 10.08f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.6f, 9f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.27f, 7.18f)
                lineTo(4.21f, 7.12f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.04f, 4.29f)
                lineTo(7.1f, 4.35f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8.92f, 4.68f)
                lineTo(9f, 4.68f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10f, 3.17f)
                lineTo(10f, 3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 3f)
                lineTo(14f, 3.09f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 4.6f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.82f, 4.27f)
                lineTo(16.88f, 4.21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19.71f, 7.04f)
                lineTo(19.65f, 7.1f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.32f, 8.92f)
                lineTo(19.32f, 9f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.83f, 10f)
                lineTo(21f, 10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 14f)
                lineTo(20.91f, 14f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.4f, 15f)
                close()
            }
        }
    }

    val SlidersHorizontal: ImageVector by lazy {
        lucideIcon("SlidersHorizontal") {
            strokePath {
                moveTo(21f, 4f); lineTo(14f, 4f)
                moveTo(10f, 4f); lineTo(3f, 4f)
                moveTo(21f, 12f); lineTo(12f, 12f)
                moveTo(8f, 12f); lineTo(3f, 12f)
                moveTo(21f, 20f); lineTo(16f, 20f)
                moveTo(12f, 20f); lineTo(3f, 20f)
                moveTo(14f, 2f); lineTo(14f, 6f)
                moveTo(8f, 10f); lineTo(8f, 14f)
                moveTo(16f, 18f); lineTo(16f, 22f)
            }
        }
    }
    val Tune: ImageVector get() = SlidersHorizontal

    val Sun: ImageVector by lazy {
        lucideIcon("Sun") {
            strokePath {
                circlePath(12f, 12f, 4f)
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
                moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        lucideIcon("Moon") {
            strokePath {
                moveTo(12f, 3f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
                close()
            }
        }
    }

    val Monitor: ImageVector by lazy {
        lucideIcon("Monitor") {
            strokePath {
                roundRectPath(2f, 3f, 20f, 14f, 2f)
                moveTo(8f, 21f); lineTo(16f, 21f)
                moveTo(12f, 17f); lineTo(12f, 21f)
            }
        }
    }

    val SunMoon: ImageVector by lazy {
        lucideIcon("SunMoon") {
            strokePath {
                circlePath(12f, 12f, 4f)
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
                moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
                moveTo(12f, 8f); lineTo(12f, 16f)
            }
        }
    }
    val BrightnessAuto: ImageVector get() = Monitor

    val BatteryCharging: ImageVector by lazy {
        lucideIcon("BatteryCharging") {
            strokePath {
                moveTo(15f, 7f); lineTo(16f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 9f)
                lineTo(18f, 15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 17f)
                lineTo(14f, 17f)
                moveTo(6f, 7f); lineTo(4f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 9f)
                lineTo(2f, 15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 17f)
                lineTo(7f, 17f)
                moveTo(11f, 7f); lineTo(8f, 12f); lineTo(12f, 12f); lineTo(9f, 17f)
                moveTo(22f, 11f); lineTo(22f, 13f)
            }
        }
    }

    val Camera: ImageVector by lazy {
        lucideIcon("Camera") {
            strokePath {
                moveTo(14.5f, 4f); lineTo(9.5f, 4f)
                lineTo(7f, 7f); lineTo(4f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 9f)
                lineTo(2f, 18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 20f)
                lineTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 18f)
                lineTo(22f, 9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 7f)
                lineTo(17f, 7f)
                close()
                circlePath(12f, 13f, 3f)
            }
        }
    }

    val Image: ImageVector by lazy {
        lucideIcon("Image") {
            strokePath {
                roundRectPath(3f, 3f, 18f, 18f, 2f)
                circlePath(9f, 9f, 2f)
                moveTo(21f, 15f); lineTo(16f, 10f); lineTo(5f, 21f)
            }
        }
    }
}
