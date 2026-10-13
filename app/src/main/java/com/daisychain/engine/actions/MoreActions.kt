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
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import kotlin.random.Random

@AutoService(ActionDefinition::class)
class GetClipboardAction : ActionDefinition() {
    override val id = "is.workflow.actions.getclipboard"
    override val category = Category.Text
    override val title = t("Get clipboard", "Obtenir le presse-papiers")
    override val iconKey = "clipboard"
    override val colorKey = "plum"
    override val produces = ContentType.Text
    override val outputLabel = t("Clipboard", "Presse-papiers")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.device.clipboardText())
}

@AutoService(ActionDefinition::class)
class CopyToClipboardAction : ActionDefinition() {
    override val id = "is.workflow.actions.setclipboard"
    override val category = Category.Text
    override val title = t("Copy to clipboard", "Copier dans le presse-papiers")
    override val iconKey = "clipboard"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte")) 
    override val accepts = setOf(ContentType.Any)

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setClipboardText(ctx.input.display())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenUrlAction : ActionDefinition() {
    override val id = "is.workflow.actions.openurl"
    override val category = Category.Web
    override val title = t("Open URL", "Ouvrir l'URL")
    override val iconKey = "link"
    override val colorKey = "blue"
    override val accepts = setOf(ContentType.Url, ContentType.Text)
    override val params = listOf(ParamSpec("WFInput", t("URL", "URL"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.valueOrInput("WFInput").display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No URL to open", "Aucune URL à ouvrir"))
        ctx.device.openUrl(url)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class SpeakTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.speaktext"
    override val category = Category.Text
    override val title = t("Speak text", "Prononcer le texte")
    override val iconKey = "speak"
    override val colorKey = "plum"
    override val accepts = setOf(ContentType.Text)
    override val params = listOf(ParamSpec("WFText", t("Text", "Texte"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.speak(ctx.valueOrInput("WFText").display())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class VibrateAction : ActionDefinition() {
    override val id = "is.workflow.actions.vibrate"
    override val category = Category.Device
    override val title = t("Vibrate", "Faire vibrer")
    override val iconKey = "vibrate"
    override val colorKey = "teal"
    override val params = listOf(
        ParamSpec("WFDuration", t("Seconds", "Secondes"), ParamType.Number(min = 0.0, max = 5.0, unit = "s"), default = ParamValue.Number(0.5)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.vibrate(((ctx.number("WFDuration") ?: 0.5).coerceIn(0.0, 5.0) * 1000).toLong())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class FlashlightAction : ActionDefinition() {
    override val id = "is.workflow.actions.flashlight"
    override val category = Category.Device
    override val title = t("Set flashlight", "Régler la lampe torche")
    override val iconKey = "flashlight"
    override val colorKey = "amber"
    override val keywords = listOf("torch", "lampe", "lumière")
    override val params = listOf(
        ParamSpec(
            "WFFlashlightSetting", t("Mode", "Mode"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("Toggle", t("Toggle", "Basculer")),
                    ParamType.Option("Turn On", t("Turn on", "Allumer")),
                    ParamType.Option("Turn Off", t("Turn off", "Éteindre")),
                ),
            ),
            default = ParamValue.Choice("Toggle"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setFlashlight(
            when (ctx.choice("WFFlashlightSetting")) {
                "Turn On" -> true
                "Turn Off" -> false
                else -> null
            },
        )
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class CurrentDateAction : ActionDefinition() {
    override val id = "is.workflow.actions.date"
    override val category = Category.Scripting
    override val title = t("Current date", "Date actuelle")
    override val iconKey = "calendar"
    override val colorKey = "teal"
    override val produces = ContentType.Date
    override val outputLabel = t("Date", "Date")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Date(System.currentTimeMillis())
}

@AutoService(ActionDefinition::class)
class RandomNumberAction : ActionDefinition() {
    override val id = "is.workflow.actions.number.random"
    override val category = Category.Numbers
    override val title = t("Random number", "Nombre aléatoire")
    override val iconKey = "dice"
    override val colorKey = "teal"
    override val produces = ContentType.Number
    override val outputLabel = t("Random Number", "Nombre aléatoire")
    override val params = listOf(
        ParamSpec("WFRandomNumberMinimum", t("Minimum", "Minimum"), ParamType.Number(), default = ParamValue.Number(0.0)),
        ParamSpec("WFRandomNumberMaximum", t("Maximum", "Maximum"), ParamType.Number(), default = ParamValue.Number(100.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val a = (ctx.number("WFRandomNumberMinimum") ?: 0.0).toLong()
        val b = (ctx.number("WFRandomNumberMaximum") ?: 100.0).toLong()
        return RunValue.Number(Random.nextLong(minOf(a, b), maxOf(a, b) + 1).toDouble())
    }
}

@AutoService(ActionDefinition::class)
class AskForInputAction : ActionDefinition() {
    override val id = "is.workflow.actions.ask"
    override val category = Category.Scripting
    override val title = t("Ask for input", "Demander une saisie")
    override val iconKey = "ask"
    override val colorKey = "plum"
    override val produces = ContentType.Text
    override val outputLabel = t("Provided Input", "Saisie fournie")
    override val params = listOf(ParamSpec("WFAskActionPrompt", t("Question", "Question"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.ui.ask(ctx.text("WFAskActionPrompt")))
}

@AutoService(ActionDefinition::class)
class ShareAction : ActionDefinition() {
    override val id = "is.workflow.actions.share"
    override val category = Category.Sharing
    override val title = t("Share", "Partager")
    override val iconKey = "share"
    override val colorKey = "blue"
    override val input = InputSpec(t("Content", "Contenu"))
    override val accepts = setOf(ContentType.Any)

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.shareText(ctx.input.display())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class CallAction : ActionDefinition() {
    override val id = "is.workflow.actions.call"
    override val category = Category.Sharing
    override val title = t("Call", "Appeler")
    override val iconKey = "call"
    override val colorKey = "green"
    override val params = listOf(ParamSpec("WFPhoneNumber", t("Phone number", "Numéro de téléphone"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val number = ctx.text("WFPhoneNumber").trim()
        if (number.isEmpty()) throw ActionFailed(t("No phone number", "Aucun numéro de téléphone"))
        ctx.device.dial(number)
        return RunValue.Nothing
    }
}
