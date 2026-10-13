package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService

@AutoService(ActionDefinition::class)
class GetBatteryLevelAction : ActionDefinition() {
    override val id = "is.workflow.actions.getbatterylevel"
    override val category = Category.Device
    override val title = t("Get battery level", "Obtenir l'état de la batterie")
    override val iconKey = "battery"
    override val colorKey = "teal"
    override val produces = ContentType.Number
    override val outputLabel = t("Battery Level", "Niveau de batterie")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Number(ctx.device.batteryPercent().toDouble())
}

@AutoService(ActionDefinition::class)
class SetVolumeAction : ActionDefinition() {
    override val id = "is.workflow.actions.setvolume"
    override val category = Category.Device
    override val title = t("Set volume", "Régler le volume")
    override val iconKey = "volume"
    override val colorKey = "teal"
    override val params = listOf(ParamSpec("WFVolume", t("Volume", "Volume"), ParamType.Slider(0.0, 100.0, "%")))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setVolumePercent((ctx.number("WFVolume") ?: 50.0).toInt().coerceIn(0, 100))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class SetBrightnessAction : ActionDefinition() {
    override val id = "is.workflow.actions.setbrightness"
    override val category = Category.Device
    override val title = t("Set brightness", "Régler la luminosité")
    override val iconKey = "sun"
    override val colorKey = "amber"
    override val requirements = setOf(Requirement.WriteSettings)
    override val params = listOf(ParamSpec("WFBrightness", t("Brightness", "Luminosité"), ParamType.Slider(0.0, 100.0, "%")))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setBrightnessPercent((ctx.number("WFBrightness") ?: 50.0).toInt().coerceIn(0, 100))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class ShowNotificationAction : ActionDefinition() {
    override val id = "is.workflow.actions.notification"
    override val category = Category.Notifications
    override val title = t("Show notification", "Afficher une notification")
    override val iconKey = "bell"
    override val colorKey = "amber"
    override val requirements = setOf(Requirement.Notifications)
    override val params = listOf(
        ParamSpec("WFNotificationActionTitle", t("Title", "Titre"), ParamType.Text()),
        ParamSpec("WFNotificationActionBody", t("Body", "Corps"), ParamType.Text(multiline = true)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.showNotification(ctx.text("WFNotificationActionTitle"), ctx.text("WFNotificationActionBody"))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenAppAction : ActionDefinition() {
    override val id = "is.workflow.actions.openapp"
    override val category = Category.Apps
    override val title = t("Open app", "Ouvrir l'application")
    override val iconKey = "apps"
    override val colorKey = "indigo"

    /** On Android this is a package name; the iOS bundle id is translated on import. */
    override val params = listOf(ParamSpec("WFAppIdentifier", t("App", "Application"), ParamType.App, acceptsVariables = false))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.openApp(ctx.text("WFAppIdentifier").trim())
        return RunValue.Nothing
    }
}
