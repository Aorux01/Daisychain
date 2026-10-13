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
import com.daisychain.engine.spec.RepeatableParams
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Calendar
import java.util.Locale
import java.util.regex.PatternSyntaxException

private fun itemsOf(v: RunValue): List<RunValue> = when (v) {
    is RunValue.Items -> v.values
    RunValue.Nothing -> emptyList()
    else -> listOf(v)
}

/** A date from a date value, or from a text such as 2026-10-07, 2026-10-07 18:30 or 07/10/2026. */
fun dateOf(v: RunValue): Long? = when (v) {
    is RunValue.Date -> v.epochMillis
    is RunValue.Text -> parseDate(v.value)
    else -> null
}

private val datePatterns = listOf("yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy")

private fun parseDate(text: String): Long? {
    val trimmed = text.trim()
    for (pattern in datePatterns) {
        val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        runCatching { format.parse(trimmed) }.getOrNull()?.let { return it.time }
    }
    return null
}

private val notADate = t("The input is not a date", "L'entrée n'est pas une date")

@AutoService(ActionDefinition::class)
class ListAction : ActionDefinition() {
    override val id = "is.workflow.actions.list"
    override val category = Category.Scripting
    override val title = t("List", "Liste")
    override val iconKey = "list"
    override val colorKey = "amber"
    override val produces = ContentType.List
    override val outputLabel = t("List", "Liste")
    override val keywords = listOf("items", "array", "éléments", "tableau")
    override val params = listOf(ParamSpec("Item", t("Item", "Élément"), ParamType.Text()))

    override val repeatable = RepeatableParams(
        storageKey = "Items",
        addLabel = t("Add item", "Ajouter un élément"),
        removeLabel = t("Remove item", "Enlever un élément"),
        joinOptions = emptyList(),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val items = mutableListOf<RunValue>()
        for (row in listOf(ctx) + ctx.rows()) {
            val v = row.value("Item") ?: continue
            if (v == RunValue.Nothing || (v is RunValue.Text && v.value.isEmpty())) continue
            items += v
        }
        return RunValue.Items(items)
    }
}

@AutoService(ActionDefinition::class)
class ChooseFromListAction : ActionDefinition() {
    override val id = "is.workflow.actions.choosefromlist"
    override val category = Category.Scripting
    override val title = t("Choose from list", "Choisir dans la liste")
    override val iconKey = "list"
    override val colorKey = "slate"
    override val input = InputSpec(t("List", "Liste"))
    override val produces = ContentType.Any
    override val outputLabel = t("Chosen Item", "Élément choisi")
    override val params = listOf(ParamSpec("Prompt", t("Prompt", "Invite"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val items = itemsOf(ctx.input)
        if (items.isEmpty()) throw ActionFailed(t("The list is empty", "La liste est vide"))
        return items[ctx.ui.choose(ctx.text("Prompt"), items.map { it.display() })]
    }
}

@AutoService(ActionDefinition::class)
class GetNumbersAction : ActionDefinition() {
    override val id = "is.workflow.actions.detect.number"
    override val category = Category.Numbers
    override val title = t("Get numbers from input", "Obtenir les nombres de l'entrée")
    override val iconKey = "number"
    override val colorKey = "teal"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.List
    override val outputLabel = t("Numbers", "Nombres")

    override suspend fun run(ctx: ActionRunContext): RunValue =
        RunValue.Items(Regex("-?\\d+(?:[.,]\\d+)?").findAll(ctx.input.display()).map { RunValue.Number(it.value.replace(',', '.').toDouble()) }.toList())
}

@AutoService(ActionDefinition::class)
class GetTypeAction : ActionDefinition() {
    override val id = "is.workflow.actions.getitemtype"
    override val category = Category.Scripting
    override val title = t("Get type", "Obtenir le type")
    override val iconKey = "variable"
    override val colorKey = "amber"
    override val input = InputSpec(t("Value", "Valeur"))
    override val produces = ContentType.Text
    override val outputLabel = t("Type", "Type")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(
        when (ctx.input) {
            is RunValue.Text -> "Text"
            is RunValue.Number -> "Number"
            is RunValue.Bool -> "Boolean"
            is RunValue.Date -> "Date"
            is RunValue.Items -> "List"
            is RunValue.Dict -> "Dictionary"
            is RunValue.Image -> "Image"
            is RunValue.File -> "File"
            RunValue.Nothing -> "Nothing"
        },
    )
}

@AutoService(ActionDefinition::class)
class SearchInTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.match"
    override val category = Category.Text
    override val title = t("Search in text", "Chercher dans le texte")
    override val iconKey = "replace"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.List
    override val outputLabel = t("Matches", "Correspondances")
    override val keywords = listOf("regex", "regular expression", "expression régulière", "match", "find")
    override val params = listOf(
        ParamSpec(
            "Pattern", t("Pattern", "Motif"), ParamType.Text(),
            help = t(
                "A regular expression, for example \\d+ for numbers or (\\w+)@(\\w+) to capture parts.",
                "Une expression régulière, par exemple \\d+ pour les nombres ou (\\w+)@(\\w+) pour capturer des parties.",
            ),
        ),
        ParamSpec(
            "Group", t("Group", "Groupe"), ParamType.Number(min = 0.0),
            default = ParamValue.Number(0.0),
            help = t("0 gives the whole match; 1, 2\u2026 give the parts in parentheses.", "0 donne toute la correspondance ; 1, 2\u2026 donnent les parties entre parenthèses."),
        ),
        ParamSpec("CaseSensitive", t("Match case", "Respecter la casse"), ParamType.Toggle, default = ParamValue.Bool(false)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val pattern = ctx.text("Pattern")
        if (pattern.isEmpty()) throw ActionFailed(t("Type a pattern to search for", "Indiquez un motif à chercher"))
        val regex = try {
            Regex(pattern, if (ctx.text("CaseSensitive") == "true") emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: PatternSyntaxException) {
            throw ActionFailed(t("Invalid regular expression", "Expression régulière invalide"))
        }
        val group = (ctx.number("Group") ?: 0.0).toInt()
        return RunValue.Items(
            regex.findAll(ctx.input.display()).mapNotNull { m -> if (group in 0 until m.groups.size) m.groups[group]?.value?.let { RunValue.Text(it) } else null }.toList(),
        )
    }
}

@AutoService(ActionDefinition::class)
class Base64Action : ActionDefinition() {
    override val id = "is.workflow.actions.base64encode"
    override val category = Category.Text
    override val title = t("Base64", "Base64")
    override val iconKey = "lock"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.Text
    override val outputLabel = t("Base64 Result", "Résultat Base64")
    override val params = listOf(
        ParamSpec(
            "WFEncodeMode", t("Mode", "Mode"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("Encode", t("Encode", "Encoder")),
                    ParamType.Option("Decode", t("Decode", "Décoder")),
                ),
            ),
            default = ParamValue.Choice("Encode"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val text = ctx.input.display()
        return RunValue.Text(
            if (ctx.choice("WFEncodeMode") == "Decode") {
                try {
                    String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    throw ActionFailed(t("This text is not valid Base64", "Ce texte n'est pas du Base64 valide"))
                }
            } else {
                Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
            },
        )
    }
}

@AutoService(ActionDefinition::class)
class HashAction : ActionDefinition() {
    override val id = "is.workflow.actions.hash"
    override val category = Category.Text
    override val title = t("Hash", "Empreinte (hash)")
    override val iconKey = "lock"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.Text
    override val outputLabel = t("Hash", "Empreinte")
    override val keywords = listOf("md5", "sha", "checksum")
    override val params = listOf(
        ParamSpec(
            "WFHashType", t("Type", "Type"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("MD5", t("MD5", "MD5")),
                    ParamType.Option("SHA1", t("SHA-1", "SHA-1")),
                    ParamType.Option("SHA256", t("SHA-256", "SHA-256")),
                    ParamType.Option("SHA512", t("SHA-512", "SHA-512")),
                ),
            ),
            default = ParamValue.Choice("SHA256"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val algorithm = when (ctx.choice("WFHashType")) {
            "MD5" -> "MD5"
            "SHA1" -> "SHA-1"
            "SHA512" -> "SHA-512"
            else -> "SHA-256"
        }
        val digest = MessageDigest.getInstance(algorithm).digest(ctx.input.display().toByteArray(Charsets.UTF_8))
        return RunValue.Text(digest.joinToString("") { "%02x".format(it) })
    }
}

private val timeUnits = ParamType.Choice(
    listOf(
        ParamType.Option("seconds", t("seconds", "secondes")),
        ParamType.Option("minutes", t("minutes", "minutes")),
        ParamType.Option("hours", t("hours", "heures")),
        ParamType.Option("days", t("days", "jours")),
        ParamType.Option("weeks", t("weeks", "semaines")),
        ParamType.Option("months", t("months", "mois")),
        ParamType.Option("years", t("years", "années")),
    ),
)

@AutoService(ActionDefinition::class)
class AdjustDateAction : ActionDefinition() {
    override val id = "is.workflow.actions.adjustdate"
    override val category = Category.Text
    override val title = t("Adjust date", "Ajuster une date")
    override val iconKey = "calendar"
    override val colorKey = "teal"
    override val input = InputSpec(t("Date", "Date"))
    override val produces = ContentType.Date
    override val outputLabel = t("Adjusted Date", "Date ajustée")
    override val keywords = listOf("add", "subtract", "ajouter", "retirer", "tomorrow", "demain")
    override val params = listOf(
        ParamSpec(
            "WFDateOperation", t("Operation", "Opération"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("add", t("Add", "Ajouter")),
                    ParamType.Option("subtract", t("Subtract", "Retirer")),
                ),
            ),
            default = ParamValue.Choice("add"),
        ),
        ParamSpec("Amount", t("Amount", "Quantité"), ParamType.Number(), default = ParamValue.Number(1.0)),
        ParamSpec("Unit", t("Unit", "Unité"), timeUnits, default = ParamValue.Choice("days")),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val start = dateOf(ctx.input) ?: throw ActionFailed(notADate)
        val sign = if (ctx.choice("WFDateOperation") == "subtract") -1 else 1
        val amount = (ctx.number("Amount") ?: 0.0).toInt() * sign
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        when (ctx.choice("Unit") ?: "days") {
            "seconds" -> calendar.add(Calendar.SECOND, amount)
            "minutes" -> calendar.add(Calendar.MINUTE, amount)
            "hours" -> calendar.add(Calendar.HOUR_OF_DAY, amount)
            "weeks" -> calendar.add(Calendar.DAY_OF_YEAR, amount * 7)
            "months" -> calendar.add(Calendar.MONTH, amount)
            "years" -> calendar.add(Calendar.YEAR, amount)
            else -> calendar.add(Calendar.DAY_OF_YEAR, amount)
        }
        return RunValue.Date(calendar.timeInMillis)
    }
}

@AutoService(ActionDefinition::class)
class TimeBetweenDatesAction : ActionDefinition() {
    override val id = "is.workflow.actions.gettimebetweendates"
    override val category = Category.Text
    override val title = t("Time between dates", "Temps entre deux dates")
    override val iconKey = "calendar"
    override val colorKey = "teal"
    override val input = InputSpec(t("From", "De"))
    override val produces = ContentType.Number
    override val outputLabel = t("Time Between Dates", "Temps entre les dates")
    override val keywords = listOf("stopwatch", "chronomètre", "duration", "durée", "elapsed", "écoulé")
    override val params = listOf(
        ParamSpec(
            "To", t("To", "Jusqu'à"), ParamType.Input,
            variableTypes = setOf(ContentType.Date, ContentType.Any),
            help = t("The later date. Left on automatic, it is now: handy for a stopwatch.", "La date de fin. En automatique, c'est maintenant : pratique pour un chronomètre."),
        ),
        ParamSpec("Unit", t("In", "En"), timeUnits, default = ParamValue.Choice("seconds")),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val from = dateOf(ctx.input) ?: throw ActionFailed(notADate)
        val to = ctx.value("To")?.let { dateOf(it) ?: throw ActionFailed(notADate) } ?: System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val a = Instant.ofEpochMilli(from).atZone(zone)
        val b = Instant.ofEpochMilli(to).atZone(zone)
        val millis = (to - from).toDouble()
        return RunValue.Number(
            when (ctx.choice("Unit") ?: "seconds") {
                "minutes" -> millis / 60_000
                "hours" -> millis / 3_600_000
                "days" -> millis / 86_400_000
                "weeks" -> millis / 604_800_000
                "months" -> ChronoUnit.MONTHS.between(a, b).toDouble()
                "years" -> ChronoUnit.YEARS.between(a, b).toDouble()
                else -> millis / 1000
            },
        )
    }
}

@AutoService(ActionDefinition::class)
class GetDatesFromTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.detect.date"
    override val category = Category.Text
    override val title = t("Get dates from input", "Obtenir les dates de l'entrée")
    override val iconKey = "calendar"
    override val colorKey = "teal"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.List
    override val outputLabel = t("Dates", "Dates")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val found = Regex("\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2})?|\\d{1,2}/\\d{1,2}/\\d{4}(?: \\d{2}:\\d{2})?").findAll(ctx.input.display())
        return RunValue.Items(found.mapNotNull { m -> parseDate(m.value)?.let { RunValue.Date(it) } }.toList())
    }
}
