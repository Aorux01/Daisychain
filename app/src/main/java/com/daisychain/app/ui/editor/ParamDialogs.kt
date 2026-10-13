package com.daisychain.app.ui.editor

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.daisychain.app.ui.theme.Indigo
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.daisychain.app.R
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.edit.VariableOption
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType

/** Edits one parameter of one block. [onSave] gets null to clear the value. */
@Composable
fun ParamEditDialog(
    action: ActionInstance,
    spec: ParamSpec,
    actions: List<ActionInstance>,
    variableNames: List<String>,
    /** Name of what the previous block gives (shown for parameters left on "automatic"). */
    defaultInput: String?,
    /** Creates a variable with this name (a "Set variable" block just before this block). */
    onCreateVariable: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ParamValue?) -> Unit,
) {
    val lang = currentLanguage()
    val newVariable: (String) -> VariableRef = { name ->
        onCreateVariable(name)
        VariableRef(VariableSource.Named(name))
    }
    val variables = remember(actions, action.uuid) { ActionOps.availableVariables(actions, action.uuid) }
    val initial = effectiveValue(spec, action.params)
    var result by remember { mutableStateOf<() -> ParamValue?>({ initial }) }

    CompositionLocalProvider(LocalNewVariable provides newVariable) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.label.resolve(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val help = spec.help?.resolve(lang) ?: if (spec.fallsBackToInput) stringResource(R.string.help_input) else null
                help?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                }
                when (val type = spec.type) {
                    is ParamType.Text -> TextEditor(initial, type.multiline, spec.acceptsVariables, variables, actions) { result = it }
                    is ParamType.Number -> NumberEditor(initial, type, spec.acceptsVariables, variables, actions) { result = it }
                    is ParamType.Slider -> SliderEditor(initial, type) { result = it }
                    ParamType.Toggle -> ToggleEditor(initial) { result = it }
                    is ParamType.Choice -> ChoiceEditor(initial, type, lang) { result = it }
                    ParamType.Name -> NameEditor(initial) { result = it }
                    ParamType.VariableName -> VariableNameEditor(initial, variableNames) { result = it }
                    ParamType.App -> AppPickerEditor(initial) { result = it }
                    ParamType.Input -> InputEditor(
                        initial,
                        spec.variableTypes?.let { types -> variables.filter { it.type in types } } ?: variables,
                        actions,
                        if (spec.fallsBackToInput) defaultInput else null,
                    ) { result = it }
                }
                if (spec.fallsBackToInput && spec.type !is ParamType.Input && defaultInput != null) {
                    Text(
                        stringResource(R.string.input_empty_hint, defaultInput),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    }
}

/** Creates a named variable and returns a reference to it. Provided by [ParamEditDialog]. */
private val LocalNewVariable = staticCompositionLocalOf<((String) -> VariableRef)?> { null }

@Composable
private fun NewVariableDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_variable_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.variable_name_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun VariableMenu(variables: List<VariableOption>, actions: List<ActionInstance>, onPick: (VariableRef) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val newVariable = LocalNewVariable.current
    Column {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.add_variable)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (newVariable != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_variable), color = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; naming = true },
                )
            }
            variables.forEach { v ->
                DropdownMenuItem(text = { Text(variableLabel(v.ref, actions)) }, onClick = { open = false; onPick(v.ref) })
            }
        }
    }
    if (naming && newVariable != null) {
        NewVariableDialog(onDismiss = { naming = false }) { name -> naming = false; onPick(newVariable(name)) }
    }
}

/** Pick a variable that already exists or type a new name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableNameEditor(initial: ParamValue?, existing: List<String>, publish: (() -> ParamValue?) -> Unit) {
    var text by remember {
        mutableStateOf(((initial as? ParamValue.Text)?.value?.parts?.firstOrNull() as? TokenString.Part.Literal)?.text.orEmpty())
    }
    publish { text.trim().takeIf { it.isNotEmpty() }?.let { ParamValue.Text(TokenString.of(it)) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.variable_name_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (existing.isNotEmpty()) {
            Text(stringResource(R.string.existing_variables), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                existing.forEach { name -> AssistChip(onClick = { text = name }, label = { Text(name) }) }
            }
        }
    }
}

@Composable
private fun VariableChip(ref: VariableRef, actions: List<ActionInstance>, onRemove: () -> Unit) {
    AssistChip(
        onClick = onRemove,
        label = { Text(variableLabel(ref, actions)) },
        trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.padding(0.dp)) },
    )
}

/** Stands for a variable inside the text; shown as a chip with the variable's name. */
private const val PLACEHOLDER = '\uFFFC'

/** Draws each placeholder as a coloured chip with the variable's name, right in the text. */
private class VariableChipsTransformation(
    private val labels: List<String>,
    private val background: Color,
    private val content: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        val toTransformed = IntArray(text.length + 1)
        val toOriginal = ArrayList<Int>()
        var shown = 0
        var variable = 0
        for (i in text.indices) {
            toTransformed[i] = shown
            if (text[i] == PLACEHOLDER) {
                val label = " " + (labels.getOrNull(variable++) ?: "?") + " "
                builder.withStyle(SpanStyle(background = background, color = content, fontWeight = FontWeight.Medium)) { append(label) }
                repeat(label.length) { toOriginal.add(i) }
                shown += label.length
            } else {
                builder.append(text[i])
                toOriginal.add(i)
                shown++
            }
        }
        toTransformed[text.length] = shown
        toOriginal.add(text.length)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toTransformed[offset.coerceIn(0, text.length)]
            override fun transformedToOriginal(offset: Int) = toOriginal[offset.coerceIn(0, toOriginal.size - 1)]
        }
        return TransformedText(builder.toAnnotatedString(), mapping)
    }
}

/**
 * One text field where variables sit inside the text as chips: you can write before, between and after them,
 * and the "Add variable" button inserts one at the cursor.
 */
@Composable
private fun TextEditor(
    initial: ParamValue?,
    multiline: Boolean,
    acceptsVariables: Boolean,
    variables: List<VariableOption>,
    actions: List<ActionInstance>,
    publish: (() -> ParamValue?) -> Unit,
) {
    // The variables, in the order their placeholders appear in the text.
    val vars = remember { mutableStateListOf<VariableRef>() }
    var value by remember {
        val text = StringBuilder()
        val parts = when (initial) {
            is ParamValue.Text -> initial.value.parts
            is ParamValue.Variable -> listOf(TokenString.Part.Variable(initial.ref))
            else -> emptyList()
        }
        parts.forEach { part ->
            when (part) {
                is TokenString.Part.Literal -> text.append(part.text.replace(PLACEHOLDER.toString(), ""))
                is TokenString.Part.Variable -> { text.append(PLACEHOLDER); vars.add(part.ref) }
            }
        }
        mutableStateOf(TextFieldValue(text.toString(), TextRange(text.length)))
    }

    publish {
        val parts = mutableListOf<TokenString.Part>()
        val literal = StringBuilder()
        var next = 0
        for (c in value.text) {
            if (c == PLACEHOLDER) {
                if (literal.isNotEmpty()) { parts += TokenString.Part.Literal(literal.toString()); literal.clear() }
                vars.getOrNull(next++)?.let { parts += TokenString.Part.Variable(it) }
            } else literal.append(c)
        }
        if (literal.isNotEmpty()) parts += TokenString.Part.Literal(literal.toString())
        if (parts.isEmpty()) null else ParamValue.Text(TokenString(parts))
    }

    // A deleted placeholder takes its variable with it; placeholders cannot be typed or pasted in.
    fun change(new: TextFieldValue) {
        val old = value.text
        val text = new.text
        var prefix = 0
        while (prefix < old.length && prefix < text.length && old[prefix] == text[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < text.length - prefix && old[old.length - 1 - suffix] == text[text.length - 1 - suffix]) suffix++
        val before = old.substring(0, prefix).count { it == PLACEHOLDER }
        repeat(old.substring(prefix, old.length - suffix).count { it == PLACEHOLDER }) { vars.removeAt(before) }
        val typed = text.substring(prefix, text.length - suffix)
        if (PLACEHOLDER in typed) {
            val cleaned = text.substring(0, prefix) + typed.replace(PLACEHOLDER.toString(), "") + text.substring(text.length - suffix)
            value = TextFieldValue(cleaned, TextRange(minOf(new.selection.end, cleaned.length)))
        } else {
            value = new
        }
    }

    fun insert(ref: VariableRef) {
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
        val before = value.text.substring(0, start).count { it == PLACEHOLDER }
        repeat(value.text.substring(start, end).count { it == PLACEHOLDER }) { vars.removeAt(before) }
        vars.add(before, ref)
        value = TextFieldValue(value.text.substring(0, start) + PLACEHOLDER + value.text.substring(end), TextRange(start + 1))
    }

    fun insertText(text: String) {
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
        change(TextFieldValue(value.text.substring(0, start) + text + value.text.substring(end), TextRange(start + text.length)))
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labels = vars.map { variableLabel(it, actions) }
    val transformation = remember(labels, dark) {
        VariableChipsTransformation(
            labels = labels,
            background = if (dark) Indigo.copy(alpha = 0.35f) else Color(0xFFE8E6F8),
            content = if (dark) Color(0xFFD9DAFF) else Color(0xFF34378F),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = ::change,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = if (multiline) 3 else 1,
            maxLines = 8,
            visualTransformation = transformation,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (acceptsVariables) VariableMenu(variables, actions) { insert(it) }
            TextButton(onClick = { insertText("\n") }) { Text(stringResource(R.string.new_line)) }
        }
    }
}

@Composable
private fun NumberEditor(
    initial: ParamValue?,
    type: ParamType.Number,
    acceptsVariables: Boolean,
    variables: List<VariableOption>,
    actions: List<ActionInstance>,
    publish: (() -> ParamValue?) -> Unit,
) {
    var text by remember { mutableStateOf((initial as? ParamValue.Number)?.value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var variable by remember { mutableStateOf((initial as? ParamValue.Variable)?.ref) }
    publish {
        variable?.let { ParamValue.Variable(it) } ?: text.replace(',', '.').toDoubleOrNull()?.let { value ->
            ParamValue.Number(value.coerceIn(type.min ?: -Double.MAX_VALUE, type.max ?: Double.MAX_VALUE))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val v = variable
        if (v != null) {
            VariableChip(v, actions) { variable = null }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = type.unit?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (acceptsVariables) VariableMenu(variables, actions) { variable = it }
        }
    }
}

@Composable
private fun SliderEditor(initial: ParamValue?, type: ParamType.Slider, publish: (() -> ParamValue?) -> Unit) {
    var value by remember {
        mutableFloatStateOf(((initial as? ParamValue.Number)?.value ?: ((type.min + type.max) / 2)).toFloat())
    }
    publish { ParamValue.Number(value.roundToInt().toDouble()) }
    Column {
        Text(
            "${value.roundToInt()}" + (type.unit?.let { if (it == "%") " %" else " $it" } ?: ""),
            style = MaterialTheme.typography.headlineSmall,
        )
        Slider(value = value, onValueChange = { value = it.roundToInt().toFloat() }, valueRange = type.min.toFloat()..type.max.toFloat())
    }
}

@Composable
private fun ToggleEditor(initial: ParamValue?, publish: (() -> ParamValue?) -> Unit) {
    var on by remember { mutableStateOf((initial as? ParamValue.Bool)?.value ?: false) }
    publish { ParamValue.Bool(on) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (on) R.string.param_on else R.string.param_off), Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { on = it })
    }
}

@Composable
private fun ChoiceEditor(initial: ParamValue?, type: ParamType.Choice, lang: String, publish: (() -> ParamValue?) -> Unit) {
    var selected by remember { mutableStateOf((initial as? ParamValue.Choice)?.optionId) }
    publish { selected?.let { ParamValue.Choice(it) } }
    Column {
        type.options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected == option.id, role = Role.RadioButton) { selected = option.id },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == option.id, onClick = null, modifier = Modifier.padding(12.dp))
                Text(option.label.resolve(lang))
            }
        }
    }
}

@Composable
private fun NameEditor(initial: ParamValue?, publish: (() -> ParamValue?) -> Unit) {
    var text by remember {
        mutableStateOf(((initial as? ParamValue.Text)?.value?.parts?.firstOrNull() as? TokenString.Part.Literal)?.text.orEmpty())
    }
    publish { text.trim().takeIf { it.isNotEmpty() }?.let { ParamValue.Text(TokenString.of(it)) } }
    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun InputEditor(
    initial: ParamValue?,
    variables: List<VariableOption>,
    actions: List<ActionInstance>,
    /** Name of the previous block's output when "automatic" is allowed, else null. */
    automatic: String?,
    publish: (() -> ParamValue?) -> Unit,
) {
    var selected by remember { mutableStateOf((initial as? ParamValue.Variable)?.ref) }
    var naming by remember { mutableStateOf(false) }
    val newVariable = LocalNewVariable.current
    publish { selected?.let { ParamValue.Variable(it) } }
    if (naming && newVariable != null) {
        NewVariableDialog(onDismiss = { naming = false }) { name -> naming = false; selected = newVariable(name) }
    }
    Column {
        if (automatic != null) {
            Row(
                Modifier.fillMaxWidth().selectable(selected == null, role = Role.RadioButton) { selected = null },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == null, onClick = null, modifier = Modifier.padding(12.dp))
                Text(stringResource(R.string.input_automatic, automatic), fontStyle = FontStyle.Italic)
            }
        }
        if (newVariable != null) {
            TextButton(onClick = { naming = true }) { Text(stringResource(R.string.new_variable)) }
        }
        variables.forEach { v ->
            Row(
                Modifier.fillMaxWidth().selectable(selected == v.ref, role = Role.RadioButton) { selected = v.ref },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == v.ref, onClick = null, modifier = Modifier.padding(12.dp))
                Text(variableLabel(v.ref, actions))
            }
        }
    }
}

/** Pick an installed app by name; its package name is what gets stored. */
@Composable
private fun AppPickerEditor(initial: ParamValue?, publish: (() -> ParamValue?) -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
    var selected by remember { mutableStateOf(((initial as? ParamValue.Text)?.value?.parts?.firstOrNull() as? TokenString.Part.Literal)?.text.orEmpty()) }
    var query by remember { mutableStateOf("") }
    publish { selected.takeIf { it.isNotBlank() }?.let { ParamValue.Text(TokenString.of(it)) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        apps.filter { query.isBlank() || it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true) }
            .take(60)
            .forEach { (name, pkg) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected == pkg, role = Role.RadioButton) { selected = pkg },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == pkg, onClick = null, modifier = Modifier.padding(8.dp))
                    Column {
                        Text(name)
                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
    }
}
