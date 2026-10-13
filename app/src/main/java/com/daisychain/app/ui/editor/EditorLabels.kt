package com.daisychain.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.daisychain.app.R
import com.daisychain.app.ui.library.shortcutIcons
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.ShowWhen
import kotlin.math.roundToInt

/** Icons for blocks: the shortcut icons plus a few that only make sense on a block. */
private val actionIcons: Map<String, ImageVector> = shortcutIcons + mapOf(
    "branch" to Icons.Outlined.CallSplit,
    "repeat" to Icons.Outlined.Repeat,
    "list" to Icons.Outlined.FormatListBulleted,
    "battery" to Icons.Outlined.BatteryFull,
    "volume" to Icons.Outlined.VolumeUp,
    "sun" to Icons.Outlined.WbSunny,
    "apps" to Icons.Outlined.GridView,
    "text" to Icons.Outlined.TextFields,
    "number" to Icons.Outlined.Pin,
    "variable" to Icons.Outlined.DataObject,
    "comment" to Icons.Outlined.ChatBubbleOutline,
    "eye" to Icons.Outlined.Visibility,
    "warning" to Icons.Outlined.WarningAmber,
    "stop" to Icons.Outlined.StopCircle,
    "link" to Icons.Outlined.Link,
    "speak" to Icons.Outlined.RecordVoiceOver,
    "vibrate" to Icons.Outlined.Vibration,
    "flashlight" to Icons.Outlined.FlashlightOn,
    "calendar" to Icons.Outlined.CalendarMonth,
    "calc" to Icons.Outlined.Calculate,
    "ask" to Icons.Outlined.QuestionAnswer,
    "share" to Icons.Outlined.Share,
    "dice" to Icons.Outlined.Casino,
    "clipboard" to Icons.Outlined.ContentPaste,
    "case" to Icons.Outlined.TextFormat,
    "replace" to Icons.Outlined.FindReplace,
    "split" to Icons.Outlined.ContentCut,
    "combine" to Icons.Outlined.MergeType,
    "count" to Icons.Outlined.Tag,
    "item" to Icons.Outlined.FormatListNumbered,
    "format" to Icons.Outlined.EditCalendar,
    "download" to Icons.Outlined.CloudDownload,
    "save" to Icons.Outlined.SaveAlt,
    "morse" to Icons.Outlined.Code,
    "lock" to Icons.Outlined.Lock,
    "swap" to Icons.Outlined.SwapHoriz,
)

fun actionIconFor(key: String): ImageVector = actionIcons[key] ?: Icons.Outlined.Bolt

@Composable
fun currentLanguage(): String = LocalConfiguration.current.locales[0].language

/** What a parameter looks like as a chip on a block. */
data class ChipData(
    val text: String,
    val isVariable: Boolean = false,
    val isPlaceholder: Boolean = false,
    /** A joining word such as "and" / "or". */
    val isKeyword: Boolean = false,
    /** The automatic value (what the previous block gives), shown until the user picks something else. */
    val isDefault: Boolean = false,
)

@Composable
fun variableLabel(ref: VariableRef, actions: List<ActionInstance>): String {
    val lang = currentLanguage()
    return when (val source = ref.source) {
        is VariableSource.Named -> source.name
        is VariableSource.ActionOutput -> {
            val action = actions.firstOrNull { it.uuid == source.actionUuid }
            if (action == null) stringResource(R.string.variable_missing) else {
                val def = ActionRegistry.find(action.identifier)
action.outputName ?: def?.let { d ->
                    val base = (d.outputLabel ?: d.title).resolve(lang)
                    // Two blocks that would both be called "Dictionary" become "Dictionary" and "Dictionary 2".
                    val same = actions.filter { a ->
                        a.outputName == null && ActionRegistry.find(a.identifier)?.let { (it.outputLabel ?: it.title).resolve(lang) } == base
                    }
                    if (same.size > 1) base + " " + (same.indexOfFirst { it.uuid == action.uuid } + 1) else base
                } ?: action.identifier
            }
        }
        VariableSource.ShortcutInput -> stringResource(R.string.variable_shortcut_input)
        VariableSource.Clipboard -> stringResource(R.string.variable_clipboard)
        VariableSource.CurrentDate -> stringResource(R.string.variable_current_date)
        VariableSource.AskWhenRun -> stringResource(R.string.variable_ask)
        VariableSource.Empty -> stringResource(R.string.variable_empty)
        VariableSource.RepeatItem -> stringResource(R.string.variable_repeat_item)
        VariableSource.RepeatIndex -> stringResource(R.string.variable_repeat_index)
        is VariableSource.Device -> source.key
    }
}

/** The value to show: what the user set, else the parameter's default. */
fun effectiveValue(spec: ParamSpec, params: Map<String, ParamValue>): ParamValue? = params[spec.key] ?: spec.default

/** Parameters of a block that apply right now (some only matter for certain choices). */
fun visibleParams(specs: List<ParamSpec>, params: Map<String, ParamValue>): List<ParamSpec> =
    specs.filter { spec -> spec.showWhen?.let { matches(it, specs, params) } ?: true }

private fun matches(rule: ShowWhen, specs: List<ParamSpec>, params: Map<String, ParamValue>): Boolean {
    val current = specs.firstOrNull { it.key == rule.paramKey }?.let { effectiveValue(it, params) }
    return (current as? ParamValue.Choice)?.optionId in rule.optionIds && (rule.also?.let { matches(it, specs, params) } ?: true)
}

private fun formatNumber(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

@Composable
fun chipsFor(
    spec: ParamSpec,
    params: Map<String, ParamValue>,
    actions: List<ActionInstance>,
    /** Name of what the previous block gives, for parameters left on "automatic". */
    defaultInput: String? = null,
): List<ChipData> {
    val lang = currentLanguage()
    // The card shows the parameter's name next to its chip, so an empty value is just "\u2026".
    val placeholder = ChipData("\u2026", isPlaceholder = true)
    if (spec.fallsBackToInput && effectiveValue(spec, params) == null && defaultInput != null) {
        return listOf(ChipData(defaultInput, isVariable = true, isDefault = true))
    }
    val value = effectiveValue(spec, params) ?: return listOf(placeholder)
    return when (value) {
        is ParamValue.Text -> if (spec.type is ParamType.App) {
            val pm = LocalContext.current.packageManager
            val name = value.value.parts.filterIsInstance<TokenString.Part.Literal>().joinToString("") { it.text }.trim()
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString() }.getOrDefault(name)
            listOf(ChipData(label.ifEmpty { "\u2026" }))
        } else value.value.parts.mapNotNull { part ->
            when (part) {
                is TokenString.Part.Literal -> part.text.takeIf { it.isNotBlank() }?.let { ChipData(it) }
                is TokenString.Part.Variable -> ChipData(variableLabel(part.ref, actions), isVariable = true)
            }
        }.ifEmpty { listOf(placeholder) }
        is ParamValue.Number -> {
            // Sliders are whole numbers: never show 5.4108262...
            val shown = if (spec.type is ParamType.Slider) value.value.roundToInt().toDouble() else value.value
            val unit = when (val t = spec.type) {
                is ParamType.Number -> t.unit
                is ParamType.Slider -> t.unit
                else -> null
            }
            listOf(ChipData(formatNumber(shown) + (unit?.let { if (it == "%") " %" else " $it" } ?: "")))
        }
        is ParamValue.Bool -> listOf(ChipData(stringResource(if (value.value) R.string.param_on else R.string.param_off)))
        is ParamValue.Variable -> listOf(ChipData(variableLabel(value.ref, actions), isVariable = true))
        is ParamValue.Choice -> {
            val options = (spec.type as? ParamType.Choice)?.options.orEmpty()
            listOf(ChipData(options.firstOrNull { it.id == value.optionId }?.label?.resolve(lang) ?: value.optionId))
        }
        is ParamValue.Dict, is ParamValue.Array, is ParamValue.Raw -> listOf(ChipData("…"))
    }
}

fun definitionOf(action: ActionInstance): ActionDefinition? = ActionRegistry.find(action.identifier)
