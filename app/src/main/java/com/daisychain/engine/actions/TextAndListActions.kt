package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.ShowWhen
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun items(v: RunValue): List<RunValue> = when (v) {
    is RunValue.Items -> v.values
    RunValue.Nothing -> emptyList()
    else -> listOf(v)
}

private val separators = ParamType.Choice(
    listOf(
        ParamType.Option("New Lines", t("New lines", "Retours à la ligne")),
        ParamType.Option("Spaces", t("Spaces", "Espaces")),
        ParamType.Option("Custom", t("Custom", "Personnalisé")),
    ),
)

private val splitSeparators = ParamType.Choice(
    listOf(
        ParamType.Option("New Lines", t("New lines", "Retours à la ligne")),
        ParamType.Option("Spaces", t("Spaces", "Espaces")),
        ParamType.Option("Every Character", t("Every character", "Chaque caractère")),
        ParamType.Option("Custom", t("Custom", "Personnalisé")),
    ),
)

private fun separatorOf(id: String?, custom: String): String = when (id) {
    "Spaces" -> " "
    "Every Character" -> ""
    "Custom" -> custom
    else -> "\n"
}

@AutoService(ActionDefinition::class)
class ChangeCaseAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.changecase"
    override val category = Category.Text
    override val title = t("Change case", "Changer la casse")
    override val iconKey = "case"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val accepts = setOf(ContentType.Text)
    override val produces = ContentType.Text
    override val outputLabel = t("Changed Text", "Texte modifié")
    override val keywords = listOf("upper", "lower", "majuscule", "minuscule")
    override val params = listOf(
        ParamSpec(
            "WFCaseType", t("Case", "Casse"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("UPPERCASE", t("UPPERCASE", "MAJUSCULES")),
                    ParamType.Option("lowercase", t("lowercase", "minuscules")),
                    ParamType.Option("Capitalize Every Word", t("Capitalize every word", "Majuscule à chaque mot")),
                    ParamType.Option("Capitalize with sentence case", t("Sentence case", "Majuscule en début de phrase")),
                ),
            ),
            default = ParamValue.Choice("UPPERCASE"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val text = ctx.input.display()
        return RunValue.Text(
            when (ctx.choice("WFCaseType")) {
                "lowercase" -> text.lowercase()
                "Capitalize Every Word" -> text.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
                "Capitalize with sentence case" -> text.lowercase().replaceFirstChar { it.uppercase() }
                else -> text.uppercase()
            },
        )
    }
}

@AutoService(ActionDefinition::class)
class ReplaceTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.replace"
    override val category = Category.Text
    override val title = t("Replace text", "Remplacer le texte")
    override val iconKey = "replace"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val accepts = setOf(ContentType.Text)
    override val produces = ContentType.Text
    override val outputLabel = t("Updated Text", "Texte mis à jour")
    override val keywords = listOf("find", "regex", "chercher")
    override val params = listOf(
        ParamSpec("WFReplaceTextFind", t("Find", "Chercher"), ParamType.Text()),
        ParamSpec("WFReplaceTextReplace", t("Replace with", "Remplacer par"), ParamType.Text()),
        ParamSpec("WFReplaceTextCaseSensitive", t("Match case", "Respecter la casse"), ParamType.Toggle, default = ParamValue.Bool(false)),
        ParamSpec("WFReplaceTextRegularExpression", t("Regular expression", "Expression régulière"), ParamType.Toggle, default = ParamValue.Bool(false)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val find = ctx.text("WFReplaceTextFind")
        if (find.isEmpty()) return RunValue.Text(ctx.input.display())
        val replacement = ctx.text("WFReplaceTextReplace")
        val caseSensitive = ctx.text("WFReplaceTextCaseSensitive") == "true"
        val useRegex = ctx.text("WFReplaceTextRegularExpression") == "true"
        val text = ctx.input.display()
        val result = if (useRegex) {
            val regex = try {
                Regex(find, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            } catch (e: IllegalArgumentException) {
                throw ActionFailed(t("Invalid regular expression", "Expression régulière invalide"))
            }
            text.replace(regex, replacement)
        } else {
            text.replace(find, replacement, ignoreCase = !caseSensitive)
        }
        return RunValue.Text(result)
    }
}

@AutoService(ActionDefinition::class)
class SplitTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.split"
    override val category = Category.Text
    override val title = t("Split text", "Séparer le texte")
    override val iconKey = "split"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val accepts = setOf(ContentType.Text)
    override val produces = ContentType.List
    override val outputLabel = t("Split Text", "Texte séparé")
    override val params = listOf(
        ParamSpec("WFTextSeparator", t("Separator", "Séparateur"), splitSeparators, default = ParamValue.Choice("New Lines")),
        ParamSpec("WFTextCustomSeparator", t("Custom separator", "Séparateur"), ParamType.Text(), showWhen = ShowWhen("WFTextSeparator", setOf("Custom"))),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val separator = separatorOf(ctx.choice("WFTextSeparator"), ctx.text("WFTextCustomSeparator"))
        val text = ctx.input.display()
        val parts = if (separator.isEmpty()) {
            // One entry per character (a character outside the basic alphabet may be two Kotlin chars).
            buildList {
                var i = 0
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    add(String(Character.toChars(cp)))
                    i += Character.charCount(cp)
                }
            }
        } else text.split(separator)
        return RunValue.Items(parts.map { RunValue.Text(it) })
    }
}

@AutoService(ActionDefinition::class)
class CombineTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.combine"
    override val category = Category.Text
    override val title = t("Combine text", "Assembler le texte")
    override val iconKey = "combine"
    override val colorKey = "plum"
    override val input = InputSpec(t("List", "Liste"))
    override val accepts = setOf(ContentType.List)
    override val produces = ContentType.Text
    override val outputLabel = t("Combined Text", "Texte assemblé")
    override val params = listOf(
        ParamSpec("WFTextSeparator", t("Separator", "Séparateur"), separators, default = ParamValue.Choice("New Lines")),
        ParamSpec("WFTextCustomSeparator", t("Custom separator", "Séparateur"), ParamType.Text(), showWhen = ShowWhen("WFTextSeparator", setOf("Custom"))),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val separator = separatorOf(ctx.choice("WFTextSeparator"), ctx.text("WFTextCustomSeparator"))
        return RunValue.Text(items(ctx.input).joinToString(separator) { it.display() })
    }
}

@AutoService(ActionDefinition::class)
class GetItemFromListAction : ActionDefinition() {
    override val id = "is.workflow.actions.getitemfromlist"
    override val category = Category.Scripting
    override val title = t("Get item from list", "Obtenir un élément de la liste")
    override val iconKey = "item"
    override val colorKey = "amber"
    override val input = InputSpec(t("List", "Liste"))
    override val accepts = setOf(ContentType.List)
    override val produces = ContentType.Any
    override val outputLabel = t("Item from List", "Élément de la liste")
    override val params = listOf(
        ParamSpec(
            "WFItemSpecifier", t("Get", "Obtenir"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("First Item", t("First item", "Premier élément")),
                    ParamType.Option("Last Item", t("Last item", "Dernier élément")),
                    ParamType.Option("Random Item", t("Random item", "Élément aléatoire")),
                    ParamType.Option("Item At Index", t("Item at index", "Élément à la position")),
                ),
            ),
            default = ParamValue.Choice("First Item"),
        ),
        ParamSpec(
            "WFItemIndex", t("Index", "Position"), ParamType.Number(min = 1.0), default = ParamValue.Number(1.0),
            showWhen = ShowWhen("WFItemSpecifier", setOf("Item At Index")),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val list = items(ctx.input)
        if (list.isEmpty()) throw ActionFailed(t("The list is empty", "La liste est vide"))
        return when (ctx.choice("WFItemSpecifier")) {
            "Last Item" -> list.last()
            "Random Item" -> list.random()
            "Item At Index" -> {
                val index = (ctx.number("WFItemIndex") ?: 1.0).toInt()
                list.getOrNull(index - 1) ?: throw ActionFailed(t("No item at position $index", "Aucun élément à la position $index"))
            }
            else -> list.first()
        }
    }
}

@AutoService(ActionDefinition::class)
class FormatDateAction : ActionDefinition() {
    override val id = "is.workflow.actions.format.date"
    override val category = Category.Text
    override val title = t("Format date", "Formater la date")
    override val iconKey = "format"
    override val colorKey = "teal"
    override val input = InputSpec(t("Date", "Date"))
    override val accepts = setOf(ContentType.Date)
    override val produces = ContentType.Text
    override val outputLabel = t("Formatted Date", "Date formatée")
    override val keywords = listOf("time", "heure", "hour")

    private val styles = listOf(
        ParamType.Option("None", t("None", "Aucune")),
        ParamType.Option("Short", t("Short", "Courte")),
        ParamType.Option("Medium", t("Medium", "Moyenne")),
        ParamType.Option("Long", t("Long", "Longue")),
    )

    override val params = listOf(
        ParamSpec(
            "WFDateFormatStyle", t("Date", "Date"),
            ParamType.Choice(styles + ParamType.Option("Custom", t("Custom", "Personnalisé"))),
            default = ParamValue.Choice("Medium"),
        ),
        ParamSpec(
            "WFTimeFormatStyle", t("Time", "Heure"), ParamType.Choice(styles), default = ParamValue.Choice("Short"),
            showWhen = ShowWhen("WFDateFormatStyle", setOf("None", "Short", "Medium", "Long")),
        ),
        ParamSpec(
            "WFDateFormat", t("Format", "Format"), ParamType.Text(), default = ParamValue.Text(com.daisychain.engine.model.TokenString.of("HH:mm")),
            showWhen = ShowWhen("WFDateFormatStyle", setOf("Custom")),
        ),
    )

    private fun style(id: String?): Int? = when (id) {
        "Short" -> DateFormat.SHORT
        "Medium" -> DateFormat.MEDIUM
        "Long" -> DateFormat.LONG
        else -> null
    }

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val millis = (ctx.input as? RunValue.Date)?.epochMillis ?: System.currentTimeMillis()
        val date = Date(millis)
        val dateStyle = ctx.choice("WFDateFormatStyle")
        if (dateStyle == "Custom") {
            val pattern = ctx.text("WFDateFormat").ifBlank { "HH:mm" }
            val formatter = try {
                SimpleDateFormat(pattern, Locale.getDefault())
            } catch (e: IllegalArgumentException) {
                throw ActionFailed(t("Invalid date format", "Format de date invalide"))
            }
            return RunValue.Text(formatter.format(date))
        }
        val d = style(dateStyle)
        val tm = style(ctx.choice("WFTimeFormatStyle"))
        val formatter = when {
            d != null && tm != null -> DateFormat.getDateTimeInstance(d, tm)
            d != null -> DateFormat.getDateInstance(d)
            tm != null -> DateFormat.getTimeInstance(tm)
            else -> DateFormat.getDateTimeInstance()
        }
        return RunValue.Text(formatter.format(date))
    }
}
