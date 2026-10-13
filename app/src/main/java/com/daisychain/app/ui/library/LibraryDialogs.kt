package com.daisychain.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.daisychain.app.R
import com.daisychain.app.data.db.FolderEntity
import com.daisychain.app.data.db.ShortcutEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutEditDialog(
    initial: ShortcutEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, colorKey: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: DEFAULT_ICON) }
    var colorKey by remember { mutableStateOf(initial?.colorKey ?: DEFAULT_COLOR) }
    val fallbackName = stringResource(R.string.default_shortcut_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.dialog_new_shortcut else R.string.dialog_edit_shortcut))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(fallbackName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shortcutColors.forEach { (key, color) ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(color)
                                .selectable(key == colorKey, role = Role.RadioButton) { colorKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key == colorKey) Icon(Icons.Filled.Check, null, tint = Color.White)
                        }
                    }
                }
                Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortcutIcons.forEach { (key, icon) ->
                        val selected = key == iconKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) colorFor(colorKey) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp).selectable(selected, role = Role.RadioButton) { iconKey = key },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon, null,
                                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { fallbackName }, iconKey, colorKey) }) {
                Text(stringResource(if (initial == null) R.string.create else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun FolderNameDialog(
    title: Int,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(title: Int, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderOption(stringResource(R.string.no_folder), currentFolderId == null) { onPick(null) }
                folders.forEach { f -> FolderOption(f.name, currentFolderId == f.id) { onPick(f.id) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label)
    }
}
