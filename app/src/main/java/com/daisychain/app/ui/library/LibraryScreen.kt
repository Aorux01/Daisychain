package com.daisychain.app.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisychain.app.R
import com.daisychain.app.data.ImportResult
import com.daisychain.app.data.db.FolderEntity
import com.daisychain.app.run.HomeShortcuts
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.ui.theme.Teal

/** The shortcut a dialog is currently acting on. */
private sealed interface Dialog {
    data object Create : Dialog
    data class Edit(val shortcut: ShortcutEntity) : Dialog
    data class Delete(val shortcut: ShortcutEntity) : Dialog
    data class Move(val shortcut: ShortcutEntity) : Dialog
    data object NewFolder : Dialog
    data class RenameFolder(val folder: FolderEntity) : Dialog
    data class DeleteFolder(val folder: FolderEntity) : Dialog
}

@Composable
fun LibraryScreen(
    onOpenShortcut: (Long) -> Unit,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val copyNameFormat = stringResource(R.string.copy_name)
    val context = LocalContext.current
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(context.contentResolver, uri) { importResult = it }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.FileOpen, stringResource(R.string.import_shortcut))
                }
                Button(onClick = { dialog = Dialog.Create }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.new_shortcut))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(
                state = state,
                onFilter = vm::setFilter,
                onNewFolder = { dialog = Dialog.NewFolder },
                onRename = { dialog = Dialog.RenameFolder(it) },
                onDelete = { dialog = Dialog.DeleteFolder(it) },
            )
        }

        if (state.shortcuts.isEmpty() && state.loaded) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val searching = state.query.isNotBlank()
                val firstRun = state.filter == LibraryFilter.All && !searching
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                searching -> R.string.no_results
                                firstRun -> R.string.empty_library_title
                                else -> R.string.empty_filter
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (firstRun) {
                        Text(
                            stringResource(R.string.empty_library_message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        items(state.shortcuts, key = { it.id }) { s ->
            ShortcutTile(
                s,
                onOpen = { onOpenShortcut(s.id) },
                onFavorite = { vm.toggleFavorite(s) },
                onEdit = { dialog = Dialog.Edit(s) },
                onDuplicate = { vm.duplicate(s, copyNameFormat.format(s.name)) },
                onMove = { dialog = Dialog.Move(s) },
                onAddToHome = {
                    if (!HomeShortcuts.requestPin(context, s)) {
                        Toast.makeText(context, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = { dialog = Dialog.Delete(s) },
                canMove = state.folders.isNotEmpty(),
                hasMissingBlock = s.id in state.missing,
            )
        }
        item { CreateTile { dialog = Dialog.Create } }
    }

    importResult?.let { result ->
        ImportResultDialog(result, onOpen = { id -> importResult = null; onOpenShortcut(id) }, onDismiss = { importResult = null })
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.Create -> ShortcutEditDialog(
            initial = null,
            onDismiss = { dialog = null },
            onSave = { name, icon, color -> vm.createShortcut(name, icon, color, onOpenShortcut); dialog = null },
        )
        is Dialog.Edit -> ShortcutEditDialog(
            initial = d.shortcut,
            onDismiss = { dialog = null },
            onSave = { name, icon, color -> vm.updateAppearance(d.shortcut, name, icon, color); dialog = null },
        )
        is Dialog.Delete -> ConfirmDeleteDialog(
            title = R.string.delete_shortcut_title,
            message = stringResource(R.string.delete_shortcut_message, d.shortcut.name),
            onDismiss = { dialog = null },
            onConfirm = { vm.delete(d.shortcut); dialog = null },
        )
        is Dialog.Move -> MoveToFolderDialog(
            folders = state.folders,
            currentFolderId = d.shortcut.folderId,
            onDismiss = { dialog = null },
            onPick = { vm.moveToFolder(d.shortcut, it); dialog = null },
        )
        Dialog.NewFolder -> FolderNameDialog(
            title = R.string.new_folder,
            initialName = "",
            onDismiss = { dialog = null },
            onConfirm = { vm.createFolder(it); dialog = null },
        )
        is Dialog.RenameFolder -> FolderNameDialog(
            title = R.string.rename_folder,
            initialName = d.folder.name,
            onDismiss = { dialog = null },
            onConfirm = { vm.renameFolder(d.folder.id, it); dialog = null },
        )
        is Dialog.DeleteFolder -> ConfirmDeleteDialog(
            title = R.string.delete_folder_title,
            message = stringResource(R.string.delete_folder_message, d.folder.name),
            onDismiss = { dialog = null },
            onConfirm = { vm.deleteFolder(d.folder.id); dialog = null },
        )
    }
}

@Composable
private fun FilterRow(
    state: LibraryUiState,
    onFilter: (LibraryFilter) -> Unit,
    onNewFolder: () -> Unit,
    onRename: (FolderEntity) -> Unit,
    onDelete: (FolderEntity) -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
        selectedLabelColor = MaterialTheme.colorScheme.background,
        selectedTrailingIconColor = MaterialTheme.colorScheme.background,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.filter == LibraryFilter.All,
                onClick = { onFilter(LibraryFilter.All) },
                label = { Text(stringResource(R.string.filter_all)) },
                shape = CircleShape,
                colors = chipColors,
            )
        }
        item {
            FilterChip(
                selected = state.filter == LibraryFilter.Favorites,
                onClick = { onFilter(LibraryFilter.Favorites) },
                label = { Text(stringResource(R.string.filter_favorites)) },
                shape = CircleShape,
                colors = chipColors,
            )
        }
        rowItems(state.folders, key = { "folder-${it.id}" }) { folder ->
            val selected = state.filter == LibraryFilter.Folder(folder.id)
            var menu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selected,
                    onClick = { onFilter(LibraryFilter.Folder(folder.id)) },
                    label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = CircleShape,
                    colors = chipColors,
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Filled.MoreVert,
                                stringResource(R.string.folder_options),
                                Modifier.size(18.dp).clickable { menu = true },
                            )
                        }
                    } else null,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_folder)) },
                        onClick = { menu = false; onRename(folder) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_folder), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete(folder) },
                    )
                }
            }
        }
        item {
            FilterChip(
                selected = false,
                onClick = onNewFolder,
                label = { Text(stringResource(R.string.new_folder)) },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    s: ShortcutEntity,
    canMove: Boolean,
    hasMissingBlock: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onAddToHome: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().aspectRatio(1.28f).clip(RoundedCornerShape(24.dp))
                .background(colorFor(s.colorKey))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(iconFor(s.iconKey), null, Modifier.size(30.dp), tint = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasMissingBlock) {
                        Box(Modifier.padding(end = 6.dp).size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Warning, stringResource(R.string.missing_block_badge), Modifier.size(16.dp), tint = Color(0xFFD32F2F))
                        }
                    }
                    if (s.isFavorite) Icon(Icons.Filled.Star, null, Modifier.size(20.dp), tint = Color.White)
                    Icon(
                        Icons.Filled.MoreVert,
                        stringResource(R.string.editor_more),
                        Modifier.padding(start = 4.dp).size(24.dp).clip(CircleShape).clickable { menu = true },
                        tint = Color.White,
                    )
                }
            }
            Column {
                Text(
                    s.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.actions_count, s.actionCount, s.actionCount),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (s.isFavorite) R.string.remove_favorite else R.string.add_favorite)) },
                onClick = { menu = false; onFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_appearance)) },
                onClick = { menu = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duplicate)) },
                onClick = { menu = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_home)) },
                onClick = { menu = false; onAddToHome() },
            )
            if (canMove) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_folder)) },
                    onClick = { menu = false; onMove() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun CreateTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.28f).clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.5.dp, outline), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.create_shortcut),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
