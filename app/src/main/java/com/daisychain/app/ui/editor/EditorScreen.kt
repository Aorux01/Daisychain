package com.daisychain.app.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisychain.app.R
import com.daisychain.app.run.AndroidDevice
import com.daisychain.app.run.HomeShortcuts
import com.daisychain.app.run.RuntimePermissions
import com.daisychain.app.ui.library.ConfirmDeleteDialog
import com.daisychain.app.ui.library.ExportNotesDialog
import com.daisychain.app.ui.library.ShortcutEditDialog
import com.daisychain.app.ui.library.colorFor
import com.daisychain.app.ui.library.iconFor
import com.daisychain.app.ui.theme.Teal
import com.daisychain.engine.apple.ShortcutFile
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.edit.ActionTree
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.ParamSpec
import kotlinx.coroutines.delay

/** The parameter being edited: of a block, or of one of its extra rows. */
private data class Editing(val uuid: String, val spec: ParamSpec, val row: Int?)

/** Where the next added block goes, when the user tapped a "+ Add action" placeholder. */
private data class InsertTarget(val index: Int, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(shortcutId: Long, onBack: () -> Unit, onOpenHelp: () -> Unit) {
    val vm: EditorViewModel = viewModel(key = "editor-$shortcutId", factory = EditorViewModel.Factory(shortcutId))
    val shortcut by vm.shortcut.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val runState by vm.runState.collectAsStateWithLifecycle()
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lang = currentLanguage()
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    val drag = remember { DragController() }
    val scroll = rememberScrollState()
    val device = remember { AndroidDevice(context.applicationContext) }

    var editing by remember { mutableStateOf<Editing?>(null) }
    var pendingDelete by remember { mutableStateOf<ActionInstance?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var insertTarget by remember { mutableStateOf<InsertTarget?>(null) }
    var renameTarget by remember { mutableStateOf<ActionInstance?>(null) }
    var exportNotes by remember { mutableStateOf<List<LocalizedText>?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val exported = ShortcutFile.export(shortcut?.colorKey ?: "teal", actions)
            val written = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(exported.bytes) } }.isSuccess
            if (written) exportNotes = exported.warnings else Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }
    // The missing block the user is replacing: the next block picked takes its place.
    var replaceTarget by remember { mutableStateOf<String?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    fun leave() { vm.flush(); onBack() }
    BackHandler(onBack = ::leave)

    // Everything the shortcut's blocks need (notifications, location, contacts...) is asked once, before it runs.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.run(device) }
    fun runOrStop() {
        if (runState.running) { vm.stop(); return }
        val missing = RuntimePermissions.missing(context, actions)
        if (missing.isNotEmpty()) permissions.launch(missing) else vm.run(device)
    }

    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it.resolve(lang), Toast.LENGTH_LONG).show() }
        vm.clearError()
    }

    // Scroll by itself when a dragged block is held near the top or bottom edge.
    LaunchedEffect(drag.isDragging) {
        while (drag.isDragging) {
            val viewport = drag.viewport
            if (viewport != null && viewport.isAttached) {
                val top = viewport.positionInRoot().y
                val bottom = top + viewport.size.height
                val edge = 140f
                val speed = when {
                    drag.finger.y < top + edge -> -24f
                    drag.finger.y > bottom - edge -> 24f
                    else -> 0f
                }
                if (speed != 0f) {
                    scroll.scrollBy(speed)
                    drag.updateTarget()
                }
            }
            delay(16)
        }
    }

    val ctx = EditorActions(
        actions = actions,
        run = runState,
        drag = drag,
        onEditParam = { action, spec -> editing = Editing(action.uuid, spec, null) },
        onEditRowParam = { action, row, spec -> editing = Editing(action.uuid, spec, row) },
        onSetRowParam = { action, row, key, value -> vm.setRowParam(action.uuid, row, key, value) },
        onAddRow = { vm.addRow(it.uuid) },
        onRemoveRow = { action, row -> vm.removeRow(action.uuid, row) },
        onRenameOutput = { renameTarget = it },
        onAddSegment = { vm.addSegment(it.uuid) },
        onReplaceMissing = { replaceTarget = it.uuid; insertTarget = null; if (!wide) showPicker = true },
        onDuplicate = { vm.duplicate(it.uuid) },
        onDelete = { action ->
            if (ActionOps.removalCount(actions, action.uuid) > 1) pendingDelete = action else vm.remove(action.uuid)
        },
        onAddAt = { index, label ->
            insertTarget = InsertTarget(index, label)
            if (!wide) showPicker = true
        },
        onMove = vm::move,
    )

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                EditorHeader(
                    name = shortcut?.name.orEmpty(),
                    iconKey = shortcut?.iconKey ?: "bolt",
                    colorKey = shortcut?.colorKey ?: "teal",
                    actionCount = actions.size,
                    wide = wide,
                    running = runState.running,
                    onBack = ::leave,
                    onRun = ::runOrStop,
                    onEditAppearance = { showAppearance = true },
                    onOpenHelp = onOpenHelp,
                    onExport = { exporter.launch((shortcut?.name ?: "Shortcut") + ".shortcut") },
                    onAddToHome = {
                        shortcut?.let { s ->
                            if (!HomeShortcuts.requestPin(context, s)) {
                                Toast.makeText(context, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { drag.viewport = it }) {
                    if (loaded && actions.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.empty_editor_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.empty_editor_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            FlowNodes(ActionTree.parse(actions), ctx)
                            // End of the list: a drop target (and, on wide screens, a visible "add" placeholder).
                            AddSlot(key = "end", insertIndex = actions.size, label = "", ctx = ctx, visible = wide)
                        }
                    }
                }
                if (!wide) {
                    Button(
                        onClick = { insertTarget = null; showPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.add_action), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (wide) {
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                Column(Modifier.width(380.dp).fillMaxHeight().padding(20.dp)) {
                    Text(
                        stringResource(R.string.actions_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    insertTarget?.takeIf { it.label.isNotEmpty() }?.let { target ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.adding_into, target.label),
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            IconButton(onClick = { insertTarget = null }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    if (replaceTarget != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.replacing_block), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            IconButton(onClick = { replaceTarget = null }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    ActionPicker(
                        onAdd = { def ->
                            val replacing = replaceTarget
                            if (replacing != null) vm.replaceBlock(replacing, def) else vm.addAction(def, insertTarget?.index)
                            replaceTarget = null
                            insertTarget = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // The block being dragged follows the finger.
        if (drag.isDragging) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.offset {
                    IntOffset((drag.finger.x - rootOrigin.x).toInt() - 60, (drag.finger.y - rootOrigin.y).toInt() - 130)
                },
            ) {
                Text(
                    drag.draggingTitle,
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false; insertTarget = null; replaceTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
                insertTarget?.takeIf { it.label.isNotEmpty() }?.let {
                    Text(
                        stringResource(R.string.adding_into, it.label),
                        Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (replaceTarget != null) {
                    Text(
                        stringResource(R.string.replacing_block),
                        Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                ActionPicker(onAdd = { def ->
                    val replacing = replaceTarget
                    if (replacing != null) vm.replaceBlock(replacing, def) else vm.addAction(def, insertTarget?.index)
                    insertTarget = null
                    replaceTarget = null
                    showPicker = false
                })
            }
        }
    }

    editing?.let { edit ->
        val action = actions.firstOrNull { it.uuid == edit.uuid }
        // For an extra row, the dialog sees the row's values as if they were the block's.
        val shown = if (action != null && edit.row != null) action.copy(params = ActionOps.rowsOf(action).getOrElse(edit.row) { emptyMap() }) else action
        if (action == null || shown == null) editing = null else ParamEditDialog(
            action = shown,
            spec = edit.spec,
            actions = actions,
            variableNames = ActionOps.variableNames(actions),
            defaultInput = ActionOps.previousOutput(actions, edit.uuid, edit.spec.defaultFromType)?.let { variableLabel(it, actions) },
            onCreateVariable = { name -> vm.createVariable(edit.uuid, name) },
            onDismiss = { editing = null },
            onSave = { value ->
                if (edit.row == null) vm.setParam(edit.uuid, edit.spec.key, value) else vm.setRowParam(edit.uuid, edit.row, edit.spec.key, value)
                editing = null
            },
        )
    }

    pendingDelete?.let { action ->
        ConfirmDeleteDialog(
            title = R.string.delete_action_title,
            message = stringResource(R.string.delete_group_message, ActionOps.removalCount(actions, action.uuid) - 1),
            onDismiss = { pendingDelete = null },
            onConfirm = { vm.remove(action.uuid); pendingDelete = null },
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.uuid) { mutableStateOf(target.outputName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.output_name_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.output_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setOutputName(target.uuid, name); renameTarget = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showAppearance) {
        shortcut?.let { s ->
            ShortcutEditDialog(
                initial = s,
                onDismiss = { showAppearance = false },
                onSave = { name, icon, color -> vm.updateAppearance(name, icon, color); showAppearance = false },
            )
        }
    }

    SystemRequestHost(prompt)
    exportNotes?.let { ExportNotesDialog(it) { exportNotes = null } }
    prompt?.let { RunPromptDialog(it) }
}

@Composable
private fun EditorHeader(
    name: String,
    iconKey: String,
    colorKey: String,
    actionCount: Int,
    wide: Boolean,
    running: Boolean,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onEditAppearance: () -> Unit,
    onAddToHome: () -> Unit,
    onOpenHelp: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val runIcon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow
    val runLabel = stringResource(if (running) R.string.editor_stop else R.string.editor_run)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.editor_back)) }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(colorFor(colorKey)),
            contentAlignment = Alignment.Center,
        ) { Icon(iconFor(iconKey), null, tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.editor_subtitle,
                    stringResource(R.string.editor_manual),
                    pluralStringResource(R.plurals.actions_count, actionCount, actionCount),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (wide) {
            Button(onClick = onRun, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                Icon(runIcon, null)
                Text(runLabel, Modifier.padding(start = 6.dp))
            }
        } else {
            IconButton(
                onClick = onRun,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Teal),
            ) { Icon(runIcon, runLabel, tint = Color.White) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.editor_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_appearance)) },
                    onClick = { menu = false; onEditAppearance() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_home)) },
                    onClick = { menu = false; onAddToHome() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_shortcut)) },
                    onClick = { menu = false; onExport() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_help)) },
                    onClick = { menu = false; onOpenHelp() },
                )
            }
        }
    }
}
