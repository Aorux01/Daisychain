package com.daisychain.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * State of a block being dragged to a new place.
 *
 * Every place a block can be dropped registers itself as a "slot" (see [dropSlot]) with the position in the
 * flat action list it stands for. While dragging, the slot closest to the finger is the target.
 */
class DragController {
    var draggingUuid by mutableStateOf<String?>(null)
        private set
    var draggingTitle by mutableStateOf("")
        private set

    /** Finger position in root coordinates. */
    var finger by mutableStateOf(Offset.Zero)
        private set
    var targetKey by mutableStateOf<String?>(null)
        private set

    /** The scrolling area, so the screen can auto-scroll near its edges. */
    var viewport: LayoutCoordinates? = null

    private var excluded: IntRange = IntRange.EMPTY

    private class Slot(var insertIndex: Int, var coords: LayoutCoordinates?, val centered: Boolean)

    private val slots = mutableMapOf<String, Slot>()

    val isDragging get() = draggingUuid != null

    internal fun registerIndex(key: String, insertIndex: Int, centered: Boolean) {
        val slot = slots.getOrPut(key) { Slot(insertIndex, null, centered) }
        slot.insertIndex = insertIndex
    }

    internal fun registerCoords(key: String, coords: LayoutCoordinates) {
        slots[key]?.coords = coords
    }

    internal fun unregister(key: String) { slots.remove(key) }

    /** @param noDropZone positions inside the dragged block itself, where it cannot be dropped. */
    fun start(uuid: String, title: String, at: Offset, noDropZone: IntRange) {
        draggingUuid = uuid
        draggingTitle = title
        finger = at
        excluded = noDropZone
        updateTarget()
    }

    fun dragBy(delta: Offset) {
        finger += delta
        updateTarget()
    }

    fun updateTarget() {
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((key, slot) in slots) {
            val coords = slot.coords ?: continue
            if (!coords.isAttached || slot.insertIndex in excluded) continue
            val y = coords.positionInRoot().y + if (slot.centered) coords.size.height / 2f else 0f
            val distance = abs(y - finger.y)
            if (distance < bestDistance) { bestDistance = distance; best = key }
        }
        targetKey = best
    }

    /** Ends the drag; returns (block, position to move it to), or null when it was dropped nowhere useful. */
    fun finish(): Pair<String, Int>? {
        val uuid = draggingUuid
        val index = targetKey?.let { slots[it]?.insertIndex }
        cancel()
        return if (uuid != null && index != null) uuid to index else null
    }

    fun cancel() {
        draggingUuid = null
        targetKey = null
        excluded = IntRange.EMPTY
    }
}

/**
 * Marks this element as a place where a dragged block can be dropped: before the block at [insertIndex]
 * in the flat action list. Non-centered slots show a line at their top edge while targeted;
 * centered ones (the "add action" placeholders) style themselves.
 */
@Composable
fun Modifier.dropSlot(drag: DragController, key: String, insertIndex: Int, centered: Boolean = false): Modifier {
    SideEffect { drag.registerIndex(key, insertIndex, centered) }
    DisposableEffect(key) { onDispose { drag.unregister(key) } }
    val active = drag.targetKey == key
    return this
        .onGloballyPositioned { drag.registerCoords(key, it) }
        .drawWithContent {
            drawContent()
            if (active && !centered) {
                // In the gap above the block, so the bar is not hidden by the card.
                drawRoundRect(
                    Color(0xFF0B6B5D),
                    topLeft = Offset(0f, -8.dp.toPx()),
                    size = Size(size.width, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
}
