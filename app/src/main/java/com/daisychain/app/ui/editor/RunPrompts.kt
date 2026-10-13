package com.daisychain.app.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.daisychain.app.R
import com.daisychain.app.run.RunPrompt
import com.daisychain.engine.run.RunValue

/** Shows the question a running shortcut is waiting on. Dismissing a question that needs an answer stops the run. */
@Composable
fun RunPromptDialog(prompt: RunPrompt) {
    when (prompt) {
        is RunPrompt.Result -> AlertDialog(
            onDismissRequest = { prompt.done.complete(Unit) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    prompt.image?.let { ResultImage(it) }
                    if (prompt.text.isNotEmpty() || prompt.image == null) Text(prompt.text.ifEmpty { "-" })
                }
            },
            confirmButton = { TextButton(onClick = { prompt.done.complete(Unit) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = if (prompt.allowCopy && prompt.text.isNotEmpty()) {
                {
                    val context = LocalContext.current
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Daisychain", prompt.text))
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.copy)) }
                }
            } else null,
        )
        is RunPrompt.Alert -> AlertDialog(
            // Tapping outside counts as Cancel when there is a Cancel button, so nothing happens by accident.
            onDismissRequest = { prompt.confirmed.complete(!prompt.allowCancel) },
            title = { Text(prompt.title) },
            text = { Text(prompt.message) },
            confirmButton = { TextButton(onClick = { prompt.confirmed.complete(true) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = if (prompt.allowCancel) {
                { TextButton(onClick = { prompt.confirmed.complete(false) }) { Text(stringResource(R.string.cancel)) } }
            } else null,
        )
        is RunPrompt.Choose -> AlertDialog(
            onDismissRequest = { prompt.choice.cancel() },
            title = { Text(prompt.prompt.ifEmpty { stringResource(R.string.prompt_choose_title) }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    prompt.items.forEachIndexed { index, item ->
                        TextButton(onClick = { prompt.choice.complete(index) }, modifier = Modifier.fillMaxWidth()) { Text(item) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { prompt.choice.cancel() }) { Text(stringResource(R.string.cancel)) } },
        )
        is RunPrompt.Recording -> RecordingDialog(prompt)
        is RunPrompt.WebPage -> WebPageDialog(prompt)
        // System screens (pickers, camera) are opened by SystemRequestHost; nothing to draw here.
        is RunPrompt.PickFile, is RunPrompt.SaveFile, is RunPrompt.PickPhoto, is RunPrompt.TakePhoto, is RunPrompt.TakeVideo -> Unit
        is RunPrompt.Ask -> {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { prompt.value.cancel() },
                title = { Text(prompt.prompt.ifEmpty { stringResource(R.string.prompt_ask_title) }) },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { prompt.value.complete(text) }) { Text(stringResource(R.string.ok)) } },
                dismissButton = { TextButton(onClick = { prompt.value.cancel() }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

/** The picture, scaled down while decoding so a large photo cannot exhaust memory. */
@Composable
private fun ResultImage(image: RunValue.Image) {
    val bitmap = remember(image) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}
