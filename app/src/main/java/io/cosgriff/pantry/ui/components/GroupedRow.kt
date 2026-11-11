package io.cosgriff.pantry.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.unit.Dp
import io.cosgriff.pantry.ui.theme.PantryDimens

/**
 * One row of a grouped list (a bordered, rounded card of rows) that is laid out row by row, as in a LazyColumn.
 * Row [index] of [count] gets the [card] shape's top corners if first, its bottom corners if last, the card's
 * border on its sides, and a [divider] line above it unless it is the first.
 */
fun Modifier.groupedRow(
    index: Int,
    count: Int,
    card: CornerBasedShape,
    surface: Color,
    border: Color,
    divider: Color,
    borderWidth: Dp = PantryDimens.hairline,
): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = card.copy(
        topStart = if (first) card.topStart else ZeroCornerSize,
        topEnd = if (first) card.topEnd else ZeroCornerSize,
        bottomStart = if (last) card.bottomStart else ZeroCornerSize,
        bottomEnd = if (last) card.bottomEnd else ZeroCornerSize,
    )
    return this
        .clip(shape)
        .background(surface)
        .drawWithContent {
            drawContent()
            val w = borderWidth.toPx()
            val r = card.topStart.toPx(size, this)
            // The card's outline, stretched past this row's edges where the card goes on, then cut to the row:
            // a middle row shows only the side lines.
            val top = if (first) w / 2 else -r - w
            val bottom = if (last) size.height - w / 2 else size.height + r + w
            clipRect {
                drawRoundRect(
                    color = border,
                    topLeft = Offset(w / 2, top),
                    size = Size(size.width - w, bottom - top),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(w),
                )
            }
            if (!first) drawLine(divider, Offset(w, w / 2), Offset(size.width - w, w / 2), w)
        }
}
