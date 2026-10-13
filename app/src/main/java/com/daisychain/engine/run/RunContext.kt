package com.daisychain.engine.run

import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.t

/** Everything that lives for the duration of one run. */
class RunSession(
    val device: DeviceApi,
    val ui: RunUi,
    val shortcutInput: RunValue = RunValue.Nothing,
    val library: ShortcutLibrary? = null,
    val depth: Int = 0,
) {
    /** Current item and position (from 1) of the innermost repeat. */
    var repeatItem: RunValue = RunValue.Nothing
    var repeatIndex: Int = 0

    /** The most recent dictionary a block produced: the default one for "Get dictionary value". */
    var lastDictionary: RunValue.Dict? = null

    val variables = mutableMapOf<String, RunValue>()
    val outputs = mutableMapOf<String, RunValue>()

    /** Output of the previous block: the implicit input of the next one (the first block gets the shortcut input, as on iOS). */
    var lastOutput: RunValue = shortcutInput

    suspend fun resolve(ref: VariableRef): RunValue = when (val source = ref.source) {
        is VariableSource.Named -> variables[source.name] ?: RunValue.Nothing
        is VariableSource.ActionOutput -> outputs[source.actionUuid] ?: RunValue.Nothing
        VariableSource.ShortcutInput -> shortcutInput
        VariableSource.Clipboard -> RunValue.Text(device.clipboardText())
        VariableSource.CurrentDate -> RunValue.Date(System.currentTimeMillis())
        VariableSource.AskWhenRun -> RunValue.Text(ui.ask(""))
        VariableSource.Empty -> RunValue.Nothing
        VariableSource.RepeatItem -> repeatItem
        VariableSource.RepeatIndex -> RunValue.Number(repeatIndex.toDouble())
        is VariableSource.Device -> RunValue.Nothing
    }

    suspend fun resolve(text: TokenString): String = buildString {
        text.parts.forEach { part ->
            when (part) {
                is TokenString.Part.Literal -> append(part.text)
                is TokenString.Part.Variable -> append(resolve(part.ref).display())
            }
        }
    }
}

/** Reads the parameters of the block being run, with variables resolved. */
open class ActionRunContext(
    val session: RunSession,
    val action: ActionInstance,
    private val specs: List<ParamSpec>,
    /** The input the user pointed this block at; null = the previous block's output. */
    private val inputOverride: RunValue? = null,
) {
    val device get() = session.device
    val ui get() = session.ui

    /** The previous block's output. */
    val input: RunValue get() = inputOverride ?: session.lastOutput

    private fun raw(key: String): ParamValue? = action.params[key] ?: specs.firstOrNull { it.key == key }?.default

    suspend fun value(key: String): RunValue? = when (val v = raw(key)) {
        null -> null
        is ParamValue.Text -> {
            // A field that holds just one variable gives that variable's value as it is (a list stays a list, an image an image).
            val only = v.value.parts.singleOrNull() as? TokenString.Part.Variable
            if (only != null) session.resolve(only.ref) else RunValue.Text(session.resolve(v.value))
        }
        is ParamValue.Number -> RunValue.Number(v.value)
        is ParamValue.Bool -> RunValue.Bool(v.value)
        is ParamValue.Variable -> session.resolve(v.ref)
        is ParamValue.Choice -> RunValue.Text(v.optionId)
        else -> null
    }

    suspend fun text(key: String): String = value(key)?.display().orEmpty()

    suspend fun number(key: String): Double? = when (val v = value(key)) {
        is RunValue.Number -> v.value
        is RunValue.Text -> v.value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }

    /** Whether the user set this parameter (as opposed to leaving it on its default). */
    fun isSet(key: String): Boolean = action.params.containsKey(key)

    fun choice(key: String): String? = (raw(key) as? ParamValue.Choice)?.optionId

    /** One context per extra row of a [com.daisychain.engine.spec.RepeatableParams] block, reading that row's values. */
    fun rows(): List<ActionRunContext> = ActionOps.rowsOf(action).map { row ->
        ActionRunContext(session, action.copy(params = row), specs, inputOverride)
    }

    /** The parameter if the user set one, else the previous block's output. */
    suspend fun valueOrInput(key: String): RunValue = value(key)?.takeIf { it != RunValue.Nothing } ?: input
}

/** Context for control-flow blocks (If, Repeat, Menu): they decide which segments run, and how often. */
class FlowRunContext(
    session: RunSession,
    action: ActionInstance,
    specs: List<ParamSpec>,
    val segmentCount: Int,
    private val segmentHeaders: List<ActionInstance>,
    private val runSegmentBody: suspend (Int) -> Unit,
    inputOverride: RunValue? = null,
) : ActionRunContext(session, action, specs, inputOverride) {
    /** Runs the blocks under segment [index] (0 = the start block, 1 = Otherwise / first menu item...). */
    suspend fun runSegment(index: Int) = runSegmentBody(index)

    /** A text parameter of another segment's header, e.g. a menu item's title. */
    suspend fun segmentText(index: Int, key: String): String =
        (segmentHeaders.getOrNull(index)?.params?.get(key) as? ParamValue.Text)?.let { session.resolve(it.value) }.orEmpty()
}

/** Runs another shortcut (by name) and returns what it outputs. */
suspend fun RunSession.runNested(name: String, input: RunValue): RunValue {
    val lib = library ?: throw ActionFailed(t("Shortcuts cannot be run from here", "Les raccourcis ne peuvent pas être lancés d'ici"))
    if (depth >= 8) throw ActionFailed(t("Too many shortcuts calling each other", "Trop de raccourcis s'appellent entre eux"))
    val actions = lib.find(name) ?: throw ActionFailed(t("Shortcut not found: $name", "Raccourci introuvable : $name"))
    return ShortcutRunner(device, ui, stepDelayMs = 0, library = lib, depth = depth + 1)
        .run(actions, object : RunProgress {
            override fun onStart(uuid: String) {}
            override fun onFinish(uuid: String) {}
        }, input)
}

/** A block that no longer exists in this version of the app (it was removed or comes from a missing plugin). */
val missingText = t("This block no longer exists", "Ce bloc n'existe plus")

val unsupportedText = t("This action is not supported on Android", "Cette action n'est pas prise en charge sur Android")

fun unsupported(): Nothing = throw ActionFailed(unsupportedText)
