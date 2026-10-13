package com.daisychain.app.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.daisychain.app.R
import com.daisychain.app.run.RunPrompt
import com.daisychain.engine.run.PickedFile
import java.io.File

/**
 * Opens the system screens a running shortcut asks for (file picker, gallery, camera) and reports what the user chose.
 * It draws nothing itself; the question that triggered it is [prompt].
 */
@Composable
fun SystemRequestHost(prompt: RunPrompt?) {
    val context = LocalContext.current
    var active by remember { mutableStateOf<RunPrompt?>(null) }
    var captured by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    fun finish(uri: Uri?) {
        val result = uri?.let { describe(context, it) }
        when (val p = active) {
            is RunPrompt.PickFile -> p.result.complete(result)
            is RunPrompt.SaveFile -> p.result.complete(result)
            is RunPrompt.PickPhoto -> p.result.complete(result)
            else -> Unit
        }
    }

    fun finishCapture(success: Boolean) {
        val (uri, name) = captured ?: return
        val file = PickedFile(uri.toString(), name, if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg")
        when (val p = active) {
            is RunPrompt.TakePhoto -> p.result.complete(if (success) file else null)
            is RunPrompt.TakeVideo -> p.result.complete(if (success) file else null)
            else -> Unit
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { finish(it) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { finish(it) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { finish(it) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { finishCapture(it) }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { finishCapture(it) }

    LaunchedEffect(prompt) {
        active = prompt
        when (prompt) {
            is RunPrompt.PickFile -> openDocument.launch(prompt.mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray())
            is RunPrompt.SaveFile -> createDocument.launch(prompt.name)
            is RunPrompt.PickPhoto -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            is RunPrompt.TakePhoto -> {
                val (uri, name) = captureTarget(context, "jpg")
                captured = uri to name
                takePicture.launch(uri)
            }
            is RunPrompt.TakeVideo -> {
                val (uri, name) = captureTarget(context, "mp4")
                captured = uri to name
                captureVideo.launch(uri)
            }
            else -> Unit
        }
    }
}

/** A new empty file in the app's cache that the camera app can write to. */
private fun captureTarget(context: Context, extension: String): Pair<Uri, String> {
    val folder = File(context.cacheDir, "captures").apply { mkdirs() }
    val name = "daisychain_${System.currentTimeMillis()}.$extension"
    val file = File(folder, name)
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file) to name
}

private fun describe(context: Context, uri: Uri): PickedFile {
    var name = uri.lastPathSegment.orEmpty()
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it }
    }
    return PickedFile(uri.toString(), name, context.contentResolver.getType(uri) ?: "application/octet-stream")
}

/** "Recording…" with a Stop button. */
@Composable
fun RecordingDialog(prompt: RunPrompt.Recording) {
    AlertDialog(
        onDismissRequest = { prompt.done.complete(Unit) },
        title = { Text(stringResource(R.string.recording_title)) },
        text = { Text(stringResource(R.string.recording_hint)) },
        confirmButton = { TextButton(onClick = { prompt.done.complete(Unit) }) { Text(stringResource(R.string.editor_stop)) } },
    )
}

/** A web page shown inside the app. */
@Composable
fun WebPageDialog(prompt: RunPrompt.WebPage) {
    Dialog(onDismissRequest = { prompt.done.complete(Unit) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    IconButton(onClick = { prompt.done.complete(Unit) }) { Icon(Icons.Filled.Close, stringResource(R.string.webpage_close)) }
                }
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(prompt.url)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
