package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.StopShortcut
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import kotlinx.coroutines.delay

@AutoService(ActionDefinition::class)
class CommentAction : ActionDefinition() {
    override val id = "is.workflow.actions.comment"
    override val category = Category.Scripting
    override val title = t("Comment", "Commentaire")
    override val iconKey = "comment"
    override val colorKey = "amber"
    override val params = listOf(ParamSpec("WFCommentActionText", t("Text", "Texte"), ParamType.Text(multiline = true), acceptsVariables = false))

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Nothing
}

@AutoService(ActionDefinition::class)
class TextAction : ActionDefinition() {
    override val id = "is.workflow.actions.gettext"
    override val category = Category.Text
    override val title = t("Text", "Texte")
    override val iconKey = "text"
    override val colorKey = "plum"
    override val produces = ContentType.Text
    override val outputLabel = t("Text", "Texte")
    override val params = listOf(ParamSpec("WFTextActionText", t("Text", "Texte"), ParamType.Text(multiline = true)))

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.text("WFTextActionText"))
}

@AutoService(ActionDefinition::class)
class NumberAction : ActionDefinition() {
    override val id = "is.workflow.actions.number"
    override val category = Category.Numbers
    override val title = t("Number", "Nombre")
    override val iconKey = "number"
    override val colorKey = "teal"
    override val produces = ContentType.Number
    override val outputLabel = t("Number", "Nombre")
    override val params = listOf(ParamSpec("WFNumberActionNumber", t("Number", "Nombre"), ParamType.Number()))

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Number(ctx.number("WFNumberActionNumber") ?: 0.0)
}

@AutoService(ActionDefinition::class)
class SetVariableAction : ActionDefinition() {
    override val id = "is.workflow.actions.setvariable"
    override val category = Category.Scripting
    override val title = t("Set variable", "Définir la variable")
    override val iconKey = "variable"
    override val colorKey = "amber"
    override val input = InputSpec(
        t("Value", "Valeur"),
        t("What to store. Automatic: the previous block's output.", "Ce qu'il faut enregistrer. Automatique : la sortie du bloc précédent."),
    )
    override val accepts = setOf(ContentType.Any)
    override val params = listOf(ParamSpec("WFVariableName", t("Variable", "Variable"), ParamType.VariableName, acceptsVariables = false))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("WFVariableName").trim()
        if (name.isNotEmpty()) ctx.session.variables[name] = ctx.input
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class GetVariableAction : ActionDefinition() {
    override val id = "is.workflow.actions.getvariable"
    override val category = Category.Scripting
    override val title = t("Get variable", "Obtenir la variable")
    override val iconKey = "variable"
    override val colorKey = "amber"
    override val produces = ContentType.Any
    override val params = listOf(ParamSpec("WFVariable", t("Variable", "Variable"), ParamType.VariableName, acceptsVariables = false))

    override suspend fun run(ctx: ActionRunContext): RunValue =
        ctx.session.variables[ctx.text("WFVariable").trim()] ?: RunValue.Nothing
}

@AutoService(ActionDefinition::class)
class AddToVariableAction : ActionDefinition() {
    override val id = "is.workflow.actions.appendvariable"
    override val category = Category.Scripting
    override val title = t("Add to variable", "Ajouter à la variable")
    override val iconKey = "variable"
    override val colorKey = "amber"
    override val input = InputSpec(
        t("Value", "Valeur"),
        t("What to store. Automatic: the previous block's output.", "Ce qu'il faut enregistrer. Automatique : la sortie du bloc précédent."),
    )
    override val accepts = setOf(ContentType.Any)
    override val params = listOf(ParamSpec("WFVariableName", t("Variable", "Variable"), ParamType.VariableName, acceptsVariables = false))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("WFVariableName").trim()
        if (name.isEmpty()) return RunValue.Nothing
        val existing = when (val v = ctx.session.variables[name]) {
            null, RunValue.Nothing -> emptyList()
            is RunValue.Items -> v.values
            else -> listOf(v)
        }
        ctx.session.variables[name] = RunValue.Items(existing + ctx.input)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class WaitAction : ActionDefinition() {
    override val id = "is.workflow.actions.delay"
    override val category = Category.Scripting
    override val title = t("Wait", "Attendre")
    override val iconKey = "clock"
    override val colorKey = "slate"
    override val params = listOf(
        ParamSpec("WFDelayTime", t("Duration", "Durée"), ParamType.Number(min = 0.0), default = ParamValue.Number(1.0)),
        ParamSpec(
            "Unit", t("Unit", "Unité"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("ms", t("milliseconds", "millisecondes")),
                    ParamType.Option("s", t("seconds", "secondes")),
                    ParamType.Option("min", t("minutes", "minutes")),
                ),
            ),
            default = ParamValue.Choice("s"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val factor = when (ctx.choice("Unit")) {
            "ms" -> 1.0
            "min" -> 60_000.0
            else -> 1000.0
        }
        delay(((ctx.number("WFDelayTime") ?: 0.0) * factor).toLong())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class ShowResultAction : ActionDefinition() {
    override val id = "is.workflow.actions.showresult"
    override val category = Category.Scripting
    override val title = t("Show result", "Afficher le résultat")
    override val iconKey = "eye"
    override val colorKey = "amber"
    override val accepts = setOf(ContentType.Any)
    override val params = listOf(
        ParamSpec("Text", t("Text", "Texte"), ParamType.Text(), fallsBackToInput = true),
        ParamSpec(
            "ShowCopy", t("Copy button", "Bouton Copier"), ParamType.Toggle, default = ParamValue.Bool(true),
            help = t("Shows a button to copy the result to the clipboard.", "Affiche un bouton pour copier le résultat dans le presse-papiers."),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val allowCopy = ctx.text("ShowCopy") != "false"
        when (val shown = ctx.valueOrInput("Text")) {
            is RunValue.Image -> ctx.ui.showResult("", shown, allowCopy = false)
            else -> ctx.ui.showResult(shown.display(), allowCopy = allowCopy)
        }
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class ShowAlertAction : ActionDefinition() {
    override val id = "is.workflow.actions.alert"
    override val category = Category.Scripting
    override val title = t("Show alert", "Afficher l'alerte")
    override val iconKey = "warning"
    override val colorKey = "amber"
    override val params = listOf(
        ParamSpec("WFAlertActionTitle", t("Title", "Titre"), ParamType.Text()),
        ParamSpec("WFAlertActionMessage", t("Message", "Message"), ParamType.Text(multiline = true)),
        ParamSpec(
            "WFAlertActionCancelButtonShown", t("Show Cancel button", "Afficher le bouton Annuler"),
            ParamType.Toggle, default = ParamValue.Bool(true),
        ),
    )

    /** Like on iOS, pressing Cancel stops the shortcut: put what needs a "yes" after this block. */
    override suspend fun run(ctx: ActionRunContext): RunValue {
        val allowCancel = ctx.text("WFAlertActionCancelButtonShown") != "false"
        val confirmed = ctx.ui.alert(ctx.text("WFAlertActionTitle"), ctx.text("WFAlertActionMessage"), allowCancel)
        if (!confirmed) throw StopShortcut()
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class StopShortcutAction : ActionDefinition() {
    override val id = "is.workflow.actions.exit"
    override val category = Category.Scripting
    override val title = t("Stop this shortcut", "Arrêter ce raccourci")
    override val iconKey = "stop"
    override val colorKey = "rose"
    override val keywords = listOf("exit", "quit", "stop")

    override suspend fun run(ctx: ActionRunContext): RunValue = throw StopShortcut()
}
