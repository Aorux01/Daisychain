package com.daisychain.app.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daisychain.app.R
import com.daisychain.app.ui.common.SetupOrange
import com.daisychain.app.ui.common.needsShizukuSetup
import com.daisychain.app.ui.common.rememberShizukuReady
import com.daisychain.app.run.RunState
import com.daisychain.app.ui.library.colorFor
import com.daisychain.app.ui.theme.Indigo
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.edit.FlowNode
import com.daisychain.engine.edit.Segment
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.RepeatableParams

/** What the cards need from the screen. */
class EditorActions(
    val actions: List<ActionInstance>,
    val run: RunState,
    val drag: DragController,
    val onEditParam: (ActionInstance, ParamSpec) -> Unit,
    val onDuplicate: (ActionInstance) -> Unit,
    val onDelete: (ActionInstance) -> Unit,
    /** Asks to add a block at this position of the action list; the label says where ("Otherwise"...). */
    val onAddAt: (index: Int, label: String) -> Unit,
    val onMove: (uuid: String, index: Int) -> Unit,
    val onEditRowParam: (ActionInstance, row: Int, ParamSpec) -> Unit,
    val onSetRowParam: (ActionInstance, row: Int, key: String, ParamValue?) -> Unit,
    val onAddRow: (ActionInstance) -> Unit,
    val onRemoveRow: (ActionInstance, row: Int) -> Unit,
    val onRenameOutput: (ActionInstance) -> Unit,
    val onReplaceMissing: (ActionInstance) -> Unit,
    val onAddSegment: (ActionInstance) -> Unit,
) {
    private val indexOf = actions.withIndex().associate { it.value.uuid to it.index }
    fun indexOf(uuid: String): Int = indexOf[uuid] ?: actions.size
    val size get() = actions.size
}

private val CardRadius = 24.dp

/** Horizontal position of the icon centre inside a card, so the connector lines up with it. */
private val ConnectorOffset = 36.dp

/** The little vertical line between two blocks that shows they are chained. */
@Composable
private fun Connector() {
    Box(
        Modifier.padding(start = ConnectorOffset).width(2.dp).height(12.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
    )
}

/** @param connectors draw the little chain lines between blocks (top level only, not inside If / Repeat / Menu). */
@Composable
fun FlowNodes(nodes: List<FlowNode>, ctx: EditorActions, connectors: Boolean = true, depth: Int = 0) {
    Column(verticalArrangement = if (connectors) Arrangement.Top else Arrangement.spacedBy(12.dp)) {
        nodes.forEachIndexed { i, node ->
            if (i > 0 && connectors) Connector()
            val headUuid = when (node) {
                is FlowNode.Leaf -> node.action.uuid
                is FlowNode.Group -> node.segments.first().header.uuid
            }
            val dim = if (ctx.drag.draggingUuid == headUuid) 0.35f else 1f
            Box(Modifier.alpha(dim).dropSlot(ctx.drag, "before-$headUuid", ctx.indexOf(headUuid))) {
                when (node) {
                    is FlowNode.Leaf -> ActionCard(node.action, ctx)
                    is FlowNode.Group -> GroupContainer(node, ctx, depth)
                }
            }
        }
    }
}

/** "Choice \u00abYes\u00bb", "Otherwise"\u2026: what a branch is called, so the "add" placeholder can say where it adds. */
private fun branchName(segment: Segment, lang: String): String {
    val role = definitionOf(segment.header)?.flow?.roles?.getOrNull(segment.header.flow?.role ?: 0) ?: return ""
    val title = role.params.firstOrNull()?.let { spec ->
        ((segment.header.params[spec.key] as? ParamValue.Text)?.value?.parts?.firstOrNull() as? com.daisychain.engine.model.TokenString.Part.Literal)?.text
    }?.trim().orEmpty()
    return role.title.resolve(lang) + if (title.isNotEmpty()) " \u00ab $title \u00bb" else ""
}

/**
 * A control-flow group. Its first card is as wide as any other block; everything that belongs to it
 * (branches, labels, "add" placeholders) sits slightly inset on the grey background below.
 * A group inside a group gets a different shade, so it is clear which one a placeholder belongs to.
 */
@Composable
private fun GroupContainer(group: FlowNode.Group, ctx: EditorActions, depth: Int) {
    val lang = currentLanguage()
    val header = group.segments.first().header
    val spec = definitionOf(header)?.flow
    val background = if (depth % 2 == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().background(background, RoundedCornerShape(CardRadius))) {
        ActionCard(header, ctx)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            group.segments.forEachIndexed { index, segment ->
                if (index > 0) RoleLabel(segment, ctx)
                if (segment.children.isNotEmpty()) FlowNodes(segment.children, ctx, connectors = false, depth = depth + 1)
                // Place to put (or drop) blocks inside this branch (a menu's title line holds none: only its choices run).
                if (index > 0 || spec?.startHoldsBlocks != false) {
                    val insertIndex = group.segments.getOrNull(index + 1)?.header?.uuid?.let(ctx::indexOf)
                        ?: group.end?.uuid?.let(ctx::indexOf)
                        ?: ctx.size
                    AddSlot(key = "add-${segment.header.uuid}", insertIndex = insertIndex, label = branchName(segment, lang), ctx = ctx)
                }
            }
            // One more choice for a menu.
            spec?.roles?.firstOrNull { it.repeatable }?.addLabel?.let { addLabel ->
                ParamChip(ChipData("+ " + addLabel.resolve(lang), isKeyword = true)) { ctx.onAddSegment(header) }
            }
            group.end?.let { end ->
                val title = definitionOf(end)?.flow?.roles?.lastOrNull()?.title?.resolve(lang) ?: ""
                val lit = ctx.run.active == end.uuid
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** A dashed "+ Add action" placeholder: tap to add here, or drop a dragged block on it. */
@Composable
fun AddSlot(key: String, insertIndex: Int, label: String, ctx: EditorActions, visible: Boolean = true) {
    val targeted = ctx.drag.targetKey == key
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (targeted) 1f else 0.4f)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(if (visible) 44.dp else 56.dp)
            .dropSlot(ctx.drag, key, insertIndex, centered = true)
            .clip(shape)
            .background(if (targeted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (visible) Modifier.border(BorderStroke(1.5.dp, outline), shape) else Modifier)
            .clickable(enabled = visible) { ctx.onAddAt(insertIndex, label) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (visible) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (label.isEmpty()) stringResource(R.string.slot_add_action) else stringResource(R.string.slot_add_action_in, label),
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Otherwise", or one menu item with its title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleLabel(segment: Segment, ctx: EditorActions) {
    val header = segment.header
    val def = definitionOf(header)
    val role = def?.flow?.roles?.getOrNull(header.flow?.role ?: 0)
    val lang = currentLanguage()
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            role?.title?.resolve(lang) ?: "",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (role != null) {
            FlowRow(
                Modifier.weight(1f).padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleParams(role.params, header.params).forEach { spec ->
                    chipsFor(spec, header.params, ctx.actions).forEach { chip ->
                        ParamChip(chip) { ctx.onEditParam(header, spec) }
                    }
                }
            }
            if (role.repeatable) {
                CardMenu(onDuplicate = null, onDelete = { ctx.onDelete(header) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionCard(action: ActionInstance, ctx: EditorActions) {
    val def = definitionOf(action)
    val missing = def == null
    val lang = currentLanguage()
    val scheme = MaterialTheme.colorScheme
    val active = ctx.run.active == action.uuid
    val done = action.uuid in ctx.run.done
    val failed = ctx.run.failed == action.uuid
    val shape = RoundedCornerShape(CardRadius)
    val setup = def.needsShizukuSetup(rememberShizukuReady())

    val borderColor by animateColorAsState(
        when {
            failed || missing -> scheme.error
            setup -> SetupOrange
            active -> scheme.primary
            done -> scheme.primary.copy(alpha = 0.45f)
            else -> scheme.outline
        },
        label = "border",
    )
    val glow by animateDpAsState(if (active || failed) 14.dp else 0.dp, label = "glow")
    val borderWidth = if (active || failed) 2.5.dp else if (missing || setup) 1.5.dp else 1.dp
    val glowColor = if (failed) scheme.error else scheme.primary
    val title = def?.title?.resolve(lang) ?: stringResource(R.string.missing_block_title)
    // What the block receives while its input is on "automatic".
    val defaults: Map<String, String?> = (def?.displayParams ?: emptyList()).associate { spec ->
        spec.key to ActionOps.previousOutput(ctx.actions, action.uuid, spec.defaultFromType)?.let { variableLabel(it, ctx.actions) }
    }
    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Surface(
        shape = shape,
        color = when {
            active -> scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surface)
            missing -> scheme.error.copy(alpha = 0.08f).compositeOver(scheme.surface)
            else -> scheme.surface
        },
        modifier = Modifier.fillMaxWidth()
            .shadow(glow, shape, ambientColor = glowColor, spotColor = glowColor)
            .border(borderWidth, borderColor, shape),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (def == null) scheme.error else colorFor(def.colorKey)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (missing) Icons.Filled.Warning else actionIconFor(def?.iconKey ?: "bolt"), null, tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (missing) scheme.error else Color.Unspecified,
                )
                if (setup) Text(stringResource(R.string.needs_shizuku), style = MaterialTheme.typography.bodySmall, color = SetupOrange)
                if (def == null) {
                    Text(
                        stringResource(R.string.missing_block_note, action.identifier),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                    TextButton(onClick = { ctx.onReplaceMissing(action) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                        Text(stringResource(R.string.replace_block), color = scheme.error, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    val specs = visibleParams(if ((action.flow?.role ?: 0) == 0) def.displayParams else emptyList(), action.params)
                    if (specs.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            specs.forEach { spec ->
                                ParamLine(spec.label.resolve(lang)) {
                                    chipsFor(spec, action.params, ctx.actions, defaults[spec.key]).forEach { chip ->
                                        ParamChip(chip) { ctx.onEditParam(action, spec) }
                                    }
                                }
                            }
                        }
                    }
                    // Extra rows (more conditions), each starting with its "and" / "or".
                    val repeatable = def.repeatable
                    if (repeatable != null) {
                        ActionOps.rowsOf(action).forEachIndexed { index, row ->
                            ExtraRow(action, def.params, repeatable, index, row, ctx, defaults)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ParamChip(ChipData(repeatable.addLabel.resolve(lang), isKeyword = true)) { ctx.onAddRow(action) }
                            val rowCount = ActionOps.rowsOf(action).size
                            if (rowCount > 0) {
                                ParamChip(ChipData(repeatable.removeLabel.resolve(lang), isKeyword = true)) { ctx.onRemoveRow(action, rowCount - 1) }
                            }
                        }
                    }
                    // What this block gives to the next ones; touch it to give it your own name.
                    if (def.produces != null) {
                        val outputName = action.outputName ?: (def.outputLabel ?: def.title).resolve(lang)
                        ParamChip(ChipData("\u2192 $outputName", isKeyword = true)) { ctx.onRenameOutput(action) }
                    }
                }
            }
            CardMenu(
                onDuplicate = { ctx.onDuplicate(action) },
                onDelete = { ctx.onDelete(action) },
                onRename = if (def?.produces != null) ({ ctx.onRenameOutput(action) }) else null,
            )
            // The handle: grab it to move this block (and everything inside it) somewhere else.
            Box(
                Modifier.size(30.dp).align(Alignment.Top)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(action.uuid) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val at = handle?.localToRoot(offset) ?: return@detectDragGestures
                                val range = ActionOps.range(ctx.actions, action.uuid)
                                ctx.drag.start(action.uuid, title, at, (range.first + 1)..range.last)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                ctx.drag.dragBy(amount)
                            },
                            onDragEnd = { ctx.drag.finish()?.let { (uuid, index) -> ctx.onMove(uuid, index) } },
                            onDragCancel = { ctx.drag.cancel() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Menu,
                    stringResource(R.string.drag_handle),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A parameter in a thin outline: its name, then its value as chips. Several of them sit side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParamLine(label: String, chips: @Composable () -> Unit) {
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 110.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraRow(
    action: ActionInstance,
    specs: List<ParamSpec>,
    repeatable: RepeatableParams,
    index: Int,
    row: Map<String, ParamValue>,
    ctx: EditorActions,
    defaults: Map<String, String?>,
) {
    val lang = currentLanguage()
    var menu by remember { mutableStateOf(false) }
    val join = (row[RepeatableParams.JOIN_KEY] as? ParamValue.Choice)?.optionId
    val joinOption = repeatable.joinOptions.firstOrNull { it.id == join } ?: repeatable.joinOptions.firstOrNull()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (joinOption != null) Box {
            ParamChip(ChipData(joinOption.label.resolve(lang), isKeyword = true)) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                repeatable.joinOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label.resolve(lang)) },
                        onClick = { menu = false; ctx.onSetRowParam(action, index, RepeatableParams.JOIN_KEY, ParamValue.Choice(option.id)) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; ctx.onRemoveRow(action, index) },
                )
            }
        }
        visibleParams(specs, row).forEach { spec ->
            chipsFor(spec, row, ctx.actions, defaults[spec.key]).forEach { chip ->
                ParamChip(chip) { ctx.onEditRowParam(action, index, spec) }
            }
        }
    }
}

@Composable
fun ParamChip(chip: ChipData, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (container, content) = when {
        chip.isKeyword -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) to MaterialTheme.colorScheme.primary
        chip.isVariable -> if (dark) Indigo.copy(alpha = 0.35f) to Color(0xFFD9DAFF) else Color(0xFFE8E6F8) to Color(0xFF34378F)
        chip.isPlaceholder -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = CircleShape, color = container, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Text(
            chip.text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            fontStyle = if (chip.isDefault) FontStyle.Italic else null,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CardMenu(onDuplicate: (() -> Unit)?, onDelete: () -> Unit, onRename: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.editor_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onRename != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename_output)) }, onClick = { open = false; onRename() })
            }
            if (onDuplicate != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { open = false; onDuplicate() })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}
