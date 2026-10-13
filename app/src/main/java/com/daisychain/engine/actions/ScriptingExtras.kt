package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.StopShortcut
import com.daisychain.engine.run.runNested
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

@AutoService(ActionDefinition::class)
class NothingAction : ActionDefinition() {
    override val id = "is.workflow.actions.nothing"
    override val category = Category.Scripting
    override val title = t("Nothing", "Rien")
    override val iconKey = "none"
    override val colorKey = "slate"
    override val keywords = listOf("empty", "vide", "placeholder")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Nothing
}

@AutoService(ActionDefinition::class)
class StopAndOutputAction : ActionDefinition() {
    override val id = "is.workflow.actions.output"
    override val category = Category.Scripting
    override val title = t("Stop and output", "Arrêter et renvoyer")
    override val iconKey = "output"
    override val colorKey = "rose"
    override val accepts = setOf(ContentType.Any)
    override val keywords = listOf("return", "result", "résultat")

    /** The value this shortcut hands back to the one that ran it; left empty, the previous block's output. */
    override val params = listOf(ParamSpec("WFOutput", t("Output", "Sortie"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue = throw StopShortcut(ctx.valueOrInput("WFOutput"))
}

@AutoService(ActionDefinition::class)
class RunShortcutAction : ActionDefinition() {
    override val id = "is.workflow.actions.runworkflow"
    override val category = Category.Scripting
    override val title = t("Run shortcut", "Exécuter un raccourci")
    override val iconKey = "play"
    override val colorKey = "slate"
    override val input = InputSpec(t("Input", "Entrée"))
    override val accepts = setOf(ContentType.Any)
    override val produces = ContentType.Any
    override val outputLabel = t("Shortcut Result", "Résultat du raccourci")
    override val keywords = listOf("call", "function", "fonction", "sub", "appeler")

    /** The previous block's output is passed to the other shortcut as its "Shortcut Input". */
    override val params = listOf(ParamSpec("WFWorkflowName", t("Shortcut name", "Nom du raccourci"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("WFWorkflowName").trim()
        if (name.isEmpty()) throw ActionFailed(t("Choose a shortcut to run", "Choisissez un raccourci à exécuter"))
        return ctx.session.runNested(name, ctx.input)
    }
}

/** Evaluates things like `3+4*(2-1)^2`. Accepts × ÷, a decimal comma, sqrt abs sin cos tan ln log floor ceil round, pi and e. */
object Expression {
    fun eval(text: String): Double {
        val parser = Parser(text.replace('×', '*').replace('÷', '/').replace(',', '.').replace(" ", ""))
        val value = parser.expression()
        if (!parser.atEnd()) throw ActionFailed(t("Unexpected \"${parser.rest()}\"", "Caractère inattendu « ${parser.rest()} »"))
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length
        fun rest() = s.substring(i).take(8)
        private fun peek() = s.getOrNull(i)
        private fun eat(c: Char): Boolean = if (peek() == c) { i++; true } else false

        fun expression(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    eat('*') -> v * unary()
                    eat('/') -> {
                        val d = unary()
                        if (d == 0.0) throw ActionFailed(t("Cannot divide by zero", "Division par zéro impossible"))
                        v / d
                    }
                    eat('%') -> {
                        val d = unary()
                        if (d == 0.0) throw ActionFailed(t("Cannot divide by zero", "Division par zéro impossible"))
                        v % d
                    }
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }

        private fun power(): Double {
            val base = primary()
            return if (eat('^')) Math.pow(base, unary()) else base
        }

        private fun primary(): Double {
            if (eat('(')) {
                val v = expression()
                if (!eat(')')) throw ActionFailed(t("Missing )", "Parenthèse fermante manquante"))
                return v
            }
            val start = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            if (i > start) return s.substring(start, i).toDoubleOrNull() ?: throw ActionFailed(t("Invalid number", "Nombre invalide"))
            while (peek()?.isLetter() == true) i++
            val name = s.substring(start, i)
            if (name.isEmpty()) throw ActionFailed(t("Unexpected \"${rest()}\"", "Caractère inattendu « ${rest()} »"))
            when (name.lowercase()) {
                "pi", "π" -> return Math.PI
                "e" -> return Math.E
            }
            if (!eat('(')) throw ActionFailed(t("Unknown word: $name", "Mot inconnu : $name"))
            val arg = expression()
            if (!eat(')')) throw ActionFailed(t("Missing )", "Parenthèse fermante manquante"))
            return when (name.lowercase()) {
                "sqrt" -> if (arg < 0) throw ActionFailed(t("No square root of a negative number", "Pas de racine carrée d'un nombre négatif")) else Math.sqrt(arg)
                "abs" -> Math.abs(arg)
                "sin" -> Math.sin(arg)
                "cos" -> Math.cos(arg)
                "tan" -> Math.tan(arg)
                "ln" -> Math.log(arg)
                "log" -> Math.log10(arg)
                "floor" -> Math.floor(arg)
                "ceil" -> Math.ceil(arg)
                "round" -> Math.round(arg).toDouble()
                else -> throw ActionFailed(t("Unknown function: $name", "Fonction inconnue : $name"))
            }
        }
    }
}

@AutoService(ActionDefinition::class)
class CalculateExpressionAction : ActionDefinition() {
    override val id = "is.workflow.actions.calculateexpression"
    override val category = Category.Numbers
    override val title = t("Calculate expression", "Calculer l'expression")
    override val iconKey = "functions"
    override val colorKey = "teal"
    override val accepts = setOf(ContentType.Text)
    override val produces = ContentType.Number
    override val outputLabel = t("Expression Result", "Résultat de l'expression")
    override val keywords = listOf("formula", "formule", "evaluate", "calcul")

    /** Write the calculation, e.g. 3+4*2 or sqrt(16)/2; variables can be inserted. Left empty, it is the previous output. */
    override val params = listOf(ParamSpec("Input", t("Expression", "Expression"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Number(Expression.eval(ctx.valueOrInput("Input").display()))
}

@AutoService(ActionDefinition::class)
class FormatNumberAction : ActionDefinition() {
    override val id = "is.workflow.actions.format.number"
    override val category = Category.Numbers
    override val title = t("Format number", "Formater le nombre")
    override val iconKey = "number"
    override val colorKey = "teal"
    override val accepts = setOf(ContentType.Number)
    override val produces = ContentType.Text
    override val outputLabel = t("Formatted Number", "Nombre formaté")
    override val keywords = listOf("decimal", "décimales", "thousands", "milliers")
    override val params = listOf(
        ParamSpec("WFNumberFormatDecimalPlaces", t("Decimals", "Décimales"), ParamType.Number(min = 0.0, max = 10.0), default = ParamValue.Number(2.0)),
        ParamSpec("Grouping", t("Thousands separator", "Séparateur de milliers"), ParamType.Toggle, default = ParamValue.Bool(true)),
        ParamSpec("Number", t("Number", "Nombre"), ParamType.Number(), fallsBackToInput = true),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val number = ctx.number("Number") ?: (ctx.input as? RunValue.Number)?.value ?: ctx.input.display().replace(',', '.').toDoubleOrNull()
            ?: throw ActionFailed(t("The input is not a number", "L'entrée n'est pas un nombre"))
        val decimals = (ctx.number("WFNumberFormatDecimalPlaces") ?: 2.0).toInt().coerceIn(0, 10)
        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = ctx.text("Grouping") != "false"
        }
        return RunValue.Text(format.format(number))
    }
}

@AutoService(ActionDefinition::class)
class UrlEncodeAction : ActionDefinition() {
    override val id = "is.workflow.actions.urlencode"
    override val category = Category.Web
    override val title = t("URL encode", "Encoder l'URL")
    override val iconKey = "link"
    override val colorKey = "blue"
    override val accepts = setOf(ContentType.Text)
    override val produces = ContentType.Text
    override val outputLabel = t("URL Encoded Text", "Texte encodé")
    override val keywords = listOf("percent", "decode", "décoder", "query")
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
        ParamSpec("Text", t("Text", "Texte"), ParamType.Text(), fallsBackToInput = true),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val text = ctx.valueOrInput("Text").display()
        return RunValue.Text(
            if (ctx.choice("WFEncodeMode") == "Decode") {
                try {
                    URLDecoder.decode(text, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    throw ActionFailed(t("This text cannot be decoded", "Ce texte ne peut pas être décodé"))
                }
            } else {
                URLEncoder.encode(text, "UTF-8").replace("+", "%20")
            },
        )
    }
}
