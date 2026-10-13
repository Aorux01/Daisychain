package com.daisychain.app.run

import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.run.DeviceApi
import com.daisychain.engine.run.RunFailure
import com.daisychain.engine.run.PickedFile
import com.daisychain.engine.run.RunProgress
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.RunUi
import com.daisychain.engine.run.ShortcutLibrary
import com.daisychain.engine.run.ShortcutRunner
import com.daisychain.engine.spec.LocalizedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which blocks are lit while a shortcut runs. */
data class RunState(
    val running: Boolean = false,
    val active: String? = null,
    val done: Set<String> = emptySet(),
    val failed: String? = null,
)

/** A question the running shortcut asks the user; the run waits for the answer. */
sealed interface RunPrompt {
    data class Result(val text: String, val image: RunValue.Image?, val allowCopy: Boolean, val done: CompletableDeferred<Unit>) : RunPrompt
    data class Alert(val title: String, val message: String, val allowCancel: Boolean, val confirmed: CompletableDeferred<Boolean>) : RunPrompt
    data class Choose(val prompt: String, val items: List<String>, val choice: CompletableDeferred<Int>) : RunPrompt
    data class Ask(val prompt: String, val value: CompletableDeferred<String>) : RunPrompt

    // Handled by the system: the screen shows nothing of its own, it opens the picker / camera and reports back.
    data class PickFile(val mimeTypes: List<String>, val result: CompletableDeferred<PickedFile?>) : RunPrompt
    data class SaveFile(val name: String, val mime: String, val result: CompletableDeferred<PickedFile?>) : RunPrompt
    data class PickPhoto(val result: CompletableDeferred<PickedFile?>) : RunPrompt
    data class TakePhoto(val result: CompletableDeferred<PickedFile?>) : RunPrompt
    data class TakeVideo(val result: CompletableDeferred<PickedFile?>) : RunPrompt
    data class Recording(val done: CompletableDeferred<Unit>) : RunPrompt
    data class WebPage(val url: String, val done: CompletableDeferred<Unit>) : RunPrompt
}

/**
 * Runs a shortcut and exposes what the screen needs to show: which block is lit, the question being asked,
 * the error. Shared by the editor and by the screen that runs a shortcut from the home screen.
 */
class RunHost(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<RunPrompt?>(null)
    val prompt: StateFlow<RunPrompt?> = _prompt.asStateFlow()

    private val _error = MutableStateFlow<LocalizedText?>(null)
    val error: StateFlow<LocalizedText?> = _error.asStateFlow()

    private var runJob: Job? = null
    private var clearJob: Job? = null

    private val ui = object : RunUi {
        private suspend fun <T> waitFor(prompt: (CompletableDeferred<T>) -> RunPrompt): T {
            val answer = CompletableDeferred<T>()
            _prompt.value = prompt(answer)
            try {
                return answer.await()
            } finally {
                _prompt.value = null
            }
        }

        override suspend fun showResult(text: String, image: RunValue.Image?, allowCopy: Boolean) = waitFor<Unit> { RunPrompt.Result(text, image, allowCopy, it) }
        override suspend fun alert(title: String, message: String, allowCancel: Boolean) =
            waitFor<Boolean> { RunPrompt.Alert(title, message, allowCancel, it) }
        override suspend fun choose(prompt: String, items: List<String>) = waitFor<Int> { RunPrompt.Choose(prompt, items, it) }
        override suspend fun ask(prompt: String) = waitFor<String> { RunPrompt.Ask(prompt, it) }
        override suspend fun pickFile(mimeTypes: List<String>) = waitFor<PickedFile?> { RunPrompt.PickFile(mimeTypes, it) }
        override suspend fun saveFile(name: String, mime: String) = waitFor<PickedFile?> { RunPrompt.SaveFile(name, mime, it) }
        override suspend fun pickPhoto() = waitFor<PickedFile?> { RunPrompt.PickPhoto(it) }
        override suspend fun takePhoto() = waitFor<PickedFile?> { RunPrompt.TakePhoto(it) }
        override suspend fun takeVideo() = waitFor<PickedFile?> { RunPrompt.TakeVideo(it) }
        override suspend fun waitWhileRecording() = waitFor<Unit> { RunPrompt.Recording(it) }
        override suspend fun showWebPage(url: String) = waitFor<Unit> { RunPrompt.WebPage(url, it) }
    }

    private val progress = object : RunProgress {
        override fun onStart(uuid: String) = _state.update { it.copy(active = uuid) }
        override fun onFinish(uuid: String) = _state.update {
            it.copy(active = it.active.takeIf { a -> a != uuid }, done = it.done + uuid)
        }
    }

    /** Starts a run (ignored when one is already running). The returned job ends when the run does. */
    fun run(actions: List<ActionInstance>, device: DeviceApi, stepDelayMs: Long = 300, library: ShortcutLibrary? = null, shortcutInput: RunValue = RunValue.Nothing): Job {
        runJob?.takeIf { it.isActive }?.let { return it }
        clearJob?.cancel()
        val job = scope.launch {
            _state.value = RunState(running = true)
            try {
                ShortcutRunner(device, ui, stepDelayMs, library).run(actions, progress, shortcutInput)
            } catch (e: RunFailure) {
                _state.update { it.copy(failed = e.uuid) }
                _error.value = e.text
            } catch (e: CancellationException) {
                throw e
            } finally {
                _state.update { it.copy(running = false, active = null) }
                scheduleClear()
            }
        }
        runJob = job
        return job
    }

    fun stop() { runJob?.cancel() }

    fun clearError() { _error.value = null }

    /** Leaves the lit blocks visible for a moment after the run, then turns them off. */
    private fun scheduleClear() {
        clearJob = scope.launch {
            delay(1500)
            if (!_state.value.running) _state.value = RunState()
        }
    }
}
