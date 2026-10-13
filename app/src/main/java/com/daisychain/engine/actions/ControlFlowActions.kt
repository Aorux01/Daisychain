package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.FlowRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.FlowRole
import com.daisychain.engine.spec.FlowSpec
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.RepeatableParams
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.ShowWhen
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService

private val endRole = FlowRole(t("End", "Fin"))

@AutoService(ActionDefinition::class)
class IfAction : ActionDefinition() {
    override val id = "is.workflow.actions.conditional"
    override val category = Category.Control
    override val title = t("If", "Si")
    override val iconKey = "branch"
    override val colorKey = "slate"
    override val accepts = setOf(ContentType.Any)
    override val keywords = listOf("condition", "else", "sinon")
    override val flow = FlowSpec(listOf(FlowRole(t("If", "Si")), FlowRole(t("Otherwise", "Sinon")), endRole))
    override val params = listOf(
        ParamSpec(
            "WFInput", t("Input", "Entrée"), ParamType.Input, fallsBackToInput = true,
            help = t("The value to test. Automatic: the previous block's output.", "La valeur à tester. Automatique : la sortie du bloc précédent."),
        ),
        ParamSpec(
            "WFCondition", t("Condition", "Condition"), ParamType.Choice(Conditions.options),
            default = ParamValue.Choice("4"),
        ),
        ParamSpec(
            "WFConditionalActionString", t("Value", "Valeur"), ParamType.Text(),
            showWhen = ShowWhen("WFCondition", Conditions.TEXT_OPERATORS),
        ),
        ParamSpec(
            "WFNumberValue", t("Number", "Nombre"), ParamType.Number(),
            showWhen = ShowWhen("WFCondition", Conditions.NUMBER_OPERATORS),
        ),
    )

    /** More conditions, each joined with "and" or "or". "and" binds tighter than "or", as in usual logic. */
    override val repeatable = RepeatableParams(
        storageKey = "WFExtraConditions",
        addLabel = t("Add condition", "Ajouter une condition"),
        removeLabel = t("Remove condition", "Enlever une condition"),
        joinOptions = listOf(ParamType.Option("and", t("and", "et")), ParamType.Option("or", t("or", "ou"))),
    )

    override suspend fun runFlow(ctx: FlowRunContext) {
        suspend fun check(c: ActionRunContext) = Conditions.evaluate(
            conditionId = c.choice("WFCondition") ?: "4",
            input = c.valueOrInput("WFInput"),
            text = c.text("WFConditionalActionString"),
            number = c.number("WFNumberValue"),
        )

        // Groups of conditions joined by "and"; the groups are joined by "or".
        var currentGroup = mutableListOf(check(ctx))
        val groupResults = mutableListOf<Boolean>()
        for (row in ctx.rows()) {
            val result = check(row)
            if (row.choice(RepeatableParams.JOIN_KEY) == "or") {
                groupResults += currentGroup.all { it }
                currentGroup = mutableListOf(result)
            } else {
                currentGroup += result
            }
        }
        groupResults += currentGroup.all { it }
        ctx.runSegment(if (groupResults.any { it }) 0 else 1)
    }
}

/** The list of what each turn of a repeat ended with: the output of the whole group, like "End Repeat" on iOS. */
private fun publishResults(ctx: FlowRunContext, results: List<RunValue>) {
    val all = RunValue.Items(results)
    ctx.session.outputs[ctx.action.uuid] = all
    ctx.session.lastOutput = all
}

/** Comparison operators of "If". Ids match what the iOS plist stores. */
object Conditions {
    val options = listOf(
        ParamType.Option("4", t("is", "est")),
        ParamType.Option("5", t("is not", "n'est pas")),
        ParamType.Option("99", t("contains", "contient")),
        ParamType.Option("999", t("does not contain", "ne contient pas")),
        ParamType.Option("8", t("begins with", "commence par")),
        ParamType.Option("9", t("ends with", "finit par")),
        ParamType.Option("2", t("is greater than", "est supérieur à")),
        ParamType.Option("3", t("is greater than or equal to", "est supérieur ou égal à")),
        ParamType.Option("0", t("is less than", "est inférieur à")),
        ParamType.Option("1", t("is less than or equal to", "est inférieur ou égal à")),
        ParamType.Option("100", t("has any value", "a une valeur")),
        ParamType.Option("101", t("does not have any value", "n'a pas de valeur")),
    )

    val TEXT_OPERATORS = setOf("4", "5", "99", "999", "8", "9")
    val NUMBER_OPERATORS = setOf("2", "3", "0", "1")

    private fun numeric(v: RunValue): Double? = when (v) {
        is RunValue.Number -> v.value
        is RunValue.Text -> v.value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }

    fun evaluate(conditionId: String, input: RunValue, text: String, number: Double?): Boolean {
        val shown = input.display()
        val inputNumber = numeric(input)
        val target = number ?: text.trim().replace(',', '.').toDoubleOrNull()
        return when (conditionId) {
            "4" -> if (inputNumber != null && target != null) inputNumber == target else shown.equals(text, ignoreCase = true)
            "5" -> !evaluate("4", input, text, number)
            "99" -> shown.contains(text, ignoreCase = true)
            "999" -> !shown.contains(text, ignoreCase = true)
            "8" -> shown.startsWith(text, ignoreCase = true)
            "9" -> shown.endsWith(text, ignoreCase = true)
            "2" -> inputNumber != null && target != null && inputNumber > target
            "3" -> inputNumber != null && target != null && inputNumber >= target
            "0" -> inputNumber != null && target != null && inputNumber < target
            "1" -> inputNumber != null && target != null && inputNumber <= target
            "100" -> input != RunValue.Nothing && shown.isNotEmpty()
            "101" -> input == RunValue.Nothing || shown.isEmpty()
            else -> false
        }
    }
}

@AutoService(ActionDefinition::class)
class RepeatAction : ActionDefinition() {
    override val id = "is.workflow.actions.repeat.count"
    override val category = Category.Control
    override val title = t("Repeat", "Répéter")
    override val iconKey = "repeat"
    override val colorKey = "slate"
    override val keywords = listOf("loop", "boucle")
    override val produces = ContentType.List
    override val outputLabel = t("Repeat Results", "Résultats de la répétition")
    override val flow = FlowSpec(listOf(FlowRole(t("Repeat", "Répéter")), endRole))
    override val params = listOf(
        ParamSpec("WFRepeatCount", t("Times", "Fois"), ParamType.Number(min = 0.0), default = ParamValue.Number(3.0)),
    )

    override suspend fun runFlow(ctx: FlowRunContext) {
        val saved = ctx.session.repeatIndex
        val start = ctx.input
        val results = mutableListOf<RunValue>()
        try {
            repeat((ctx.number("WFRepeatCount") ?: 0.0).toInt()) { i ->
                ctx.session.repeatIndex = i + 1
                ctx.session.lastOutput = start
                ctx.runSegment(0)
                results += ctx.session.lastOutput
            }
        } finally {
            ctx.session.repeatIndex = saved
        }
        publishResults(ctx, results)
    }
}

@AutoService(ActionDefinition::class)
class RepeatWithEachAction : ActionDefinition() {
    override val id = "is.workflow.actions.repeat.each"
    override val category = Category.Control
    override val title = t("Repeat with each", "Répéter avec chaque élément")
    override val iconKey = "repeat"
    override val colorKey = "slate"
    override val accepts = setOf(ContentType.List)
    override val keywords = listOf("loop", "for each", "boucle")
    override val input = InputSpec(
        t("List", "Liste"),
        t("The items to go through, one by one. Automatic: the previous block's output.", "Les éléments à parcourir, un par un. Automatique : la sortie du bloc précédent."),
    )
    override val produces = ContentType.List
    override val outputLabel = t("Repeat Results", "Résultats de la répétition")
    override val flow = FlowSpec(listOf(FlowRole(t("Repeat with each", "Répéter avec chaque")), endRole))

    override suspend fun runFlow(ctx: FlowRunContext) {
        val items = when (val v = ctx.input) {
            is RunValue.Items -> v.values
            RunValue.Nothing -> emptyList()
            else -> listOf(v)
        }
        val savedItem = ctx.session.repeatItem
        val savedIndex = ctx.session.repeatIndex
        val results = mutableListOf<RunValue>()
        try {
            items.forEachIndexed { i, item ->
                ctx.session.lastOutput = item
                ctx.session.repeatItem = item
                ctx.session.repeatIndex = i + 1
                ctx.runSegment(0)
                results += ctx.session.lastOutput
            }
        } finally {
            ctx.session.repeatItem = savedItem
            ctx.session.repeatIndex = savedIndex
        }
        publishResults(ctx, results)
    }
}

@AutoService(ActionDefinition::class)
class ChooseFromMenuAction : ActionDefinition() {
    override val id = "is.workflow.actions.choosefrommenu"
    override val category = Category.Control
    override val title = t("Choose from menu", "Choisir dans le menu")
    override val iconKey = "list"
    override val colorKey = "slate"
    override val flow = FlowSpec(
        listOf(
            FlowRole(t("Choose from menu", "Choisir dans le menu")),
            FlowRole(
                t("Choice", "Choix"), repeatable = true,
                addLabel = t("Add choice", "Ajouter un choix"),
                params = listOf(
                    ParamSpec("WFMenuItemTitle", t("Title", "Titre"), ParamType.Text(), default = ParamValue.Text(TokenString.of("Item"))),
                ),
            ),
            endRole,
        ),
        startHoldsBlocks = false,
    )
    override val params = listOf(ParamSpec("WFMenuPrompt", t("Prompt", "Invite"), ParamType.Text()))

    override suspend fun runFlow(ctx: FlowRunContext) {
        val titles = (1 until ctx.segmentCount).map { ctx.segmentText(it, "WFMenuItemTitle") }
        if (titles.isEmpty()) return
        val chosen = ctx.ui.choose(ctx.text("WFMenuPrompt"), titles)
        ctx.runSegment(chosen + 1)
    }
}
