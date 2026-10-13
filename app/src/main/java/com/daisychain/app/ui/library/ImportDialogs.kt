package com.daisychain.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daisychain.app.R
import com.daisychain.app.data.ImportResult
import com.daisychain.app.ui.editor.currentLanguage
import com.daisychain.engine.apple.ImportError

/** Says how an import went; on success it can open the new shortcut. */
@Composable
fun ImportResultDialog(result: ImportResult, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val lang = currentLanguage()
    when (result) {
        is ImportResult.Failure -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = {
                Text(
                    stringResource(
                        if (result.reason == ImportError.Reason.Signed) R.string.import_signed else R.string.import_not_shortcut,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
        is ImportResult.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_done_title, result.name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(pluralStringResource(R.plurals.actions_count, result.blockCount, result.blockCount))
                    if (result.warnings.isNotEmpty()) {
                        Text(
                            stringResource(R.string.import_warnings),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        result.warnings.take(12).forEach { Text("• " + it.resolve(lang), style = MaterialTheme.typography.bodySmall) }
                        if (result.warnings.size > 12) Text("…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onOpen(result.id) }) { Text(stringResource(R.string.open)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Notes after an export: what Apple Shortcuts will not understand. */
@Composable
fun ExportNotesDialog(notes: List<com.daisychain.engine.spec.LocalizedText>, onDismiss: () -> Unit) {
    val lang = currentLanguage()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_done_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.export_unsigned_note), style = MaterialTheme.typography.bodySmall)
                if (notes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.export_warnings_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    notes.take(12).forEach { Text("\u2022 " + it.resolve(lang), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
