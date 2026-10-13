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
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService

private val shizukuHelp = t(
    "Android only lets apps do this with the help of the free Shizuku app: Settings → Advanced control explains how to set it up (once).",
    "Android ne laisse faire cela aux applications qu'avec l'aide de l'application gratuite Shizuku : Réglages → Contrôle avancé explique comment la mettre en place (une seule fois).",
)

private val stateChoice = ParamType.Choice(
    listOf(
        ParamType.Option("on", t("Turn on", "Activer")),
        ParamType.Option("off", t("Turn off", "Désactiver")),
        ParamType.Option("toggle", t("Switch", "Inverser")),
    ),
)

/** Common shape of "turn X on / off / switch" blocks. */
abstract class RadioToggleAction(private val radio: String) : ActionDefinition() {
    override val category = Category.Device
    override val colorKey = "teal"
    override val requirements = setOf(Requirement.Shizuku)
    override val params = listOf(
        ParamSpec("operation", t("Action", "Action"), stateChoice, default = ParamValue.Choice("on"), help = shizukuHelp),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val on = when (ctx.choice("operation")) {
            "off" -> false
            "toggle" -> null
            else -> true
        }
        ctx.device.setRadio(radio, on)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class SetWifiAction : RadioToggleAction("wifi") {
    override val id = "is.workflow.actions.wifi.set"
    override val title = t("Set Wi-Fi", "Régler le Wi-Fi")
    override val iconKey = "wifi"
    override val keywords = listOf("wifi", "wi-fi", "wlan", "réseau", "network")
}

@AutoService(ActionDefinition::class)
class SetBluetoothAction : RadioToggleAction("bluetooth") {
    override val id = "is.workflow.actions.bluetooth.set"
    override val title = t("Set Bluetooth", "Régler le Bluetooth")
    override val iconKey = "bolt"
    override val keywords = listOf("bluetooth", "bt")
}

@AutoService(ActionDefinition::class)
class SetAirplaneModeAction : RadioToggleAction("airplane") {
    override val id = "is.workflow.actions.airplanemode.set"
    override val title = t("Set airplane mode", "Régler le mode avion")
    override val iconKey = "bolt"
    override val keywords = listOf("airplane", "avion", "flight", "vol")
}

@AutoService(ActionDefinition::class)
class SetMobileDataAction : RadioToggleAction("mobile_data") {
    override val id = "is.workflow.actions.cellulardata.set"
    override val title = t("Set mobile data", "Régler les données mobiles")
    override val iconKey = "bolt"
    override val keywords = listOf("4g", "5g", "lte", "cellular", "cellulaire", "data", "données", "mobile")
}

@AutoService(ActionDefinition::class)
class SetRingerModeAction : ActionDefinition() {
    override val id = "app.daisychain.sound.ringer"
    override val category = Category.Device
    override val title = t("Set ringer mode", "Régler le mode sonnerie")
    override val iconKey = "bell"
    override val colorKey = "amber"
    override val keywords = listOf("silent", "silencieux", "vibrate", "vibreur", "sound", "sons", "ring", "sonnerie")
    override val params = listOf(
        ParamSpec(
            "Mode", t("Mode", "Mode"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("sound", t("Sound", "Sonnerie")),
                    ParamType.Option("vibrate", t("Vibrate", "Vibreur")),
                    ParamType.Option("silent", t("Silent", "Silencieux")),
                ),
            ),
            default = ParamValue.Choice("vibrate"),
            help = t(
                "Silent also needs the Do Not Disturb permission: Android asks the first time.",
                "Silencieux demande aussi l'autorisation Ne pas déranger : Android la demande la première fois.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setRingerMode(ctx.choice("Mode") ?: "vibrate")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenHotspotAction : ActionDefinition() {
    override val id = "app.daisychain.system.hotspot"
    override val category = Category.Device
    override val title = t("Open hotspot settings", "Ouvrir le partage de connexion")
    override val iconKey = "wifi"
    override val colorKey = "teal"
    override val keywords = listOf("hotspot", "tethering", "partage", "connexion", "4g", "modem")
    override val description = t(
        "Android gives apps no way to switch the hotspot on: this opens its screen, one tap away.",
        "Android ne permet pas aux applications d'activer le partage de connexion : ce bloc ouvre son écran, à un toucher près.",
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.openSettings("hotspot")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class GetHotspotDetailsAction : ActionDefinition() {
    override val id = "app.daisychain.system.hotspot.details"
    override val category = Category.Device
    override val title = t("Get hotspot details", "Obtenir les infos du partage de connexion")
    override val iconKey = "wifi"
    override val colorKey = "teal"
    override val produces = ContentType.Dictionary
    override val outputLabel = t("Hotspot details", "Infos du partage")
    override val requirements = setOf(Requirement.Shizuku)
    override val keywords = listOf("hotspot", "qr", "password", "mot de passe", "ssid", "partage")
    override val description = t(
        "A dictionary with ssid, password, security and qr (text for a Wi-Fi QR code: give it to Make QR code). Needs Shizuku. Some phones hide the password: then it is empty.",
        "Un dictionnaire avec ssid, password, security et qr (le texte d'un QR code Wi-Fi : donnez-le à Créer un QR code). Nécessite Shizuku. Certains téléphones cachent le mot de passe : il est alors vide.",
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val info = ctx.device.hotspotDetails()
        val ssid = info["ssid"].orEmpty()
        val password = info["password"].orEmpty()
        if (ssid.isEmpty()) throw ActionFailed(t("The hotspot name could not be read", "Le nom du partage n'a pas pu être lu"))
        val security = info["security"].orEmpty().ifEmpty { if (password.isEmpty()) "nopass" else "WPA" }
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        val qr = "WIFI:T:$security;S:${esc(ssid)};" + (if (password.isNotEmpty()) "P:${esc(password)};" else "") + ";"
        return RunValue.Dict(
            mapOf(
                "ssid" to RunValue.Text(ssid),
                "password" to RunValue.Text(password),
                "security" to RunValue.Text(security),
                "qr" to RunValue.Text(qr),
            ),
        )
    }
}

@AutoService(ActionDefinition::class)
class RunSystemCommandAction : ActionDefinition() {
    override val id = "app.daisychain.system.command"
    override val category = Category.Device
    override val title = t("Run system command (Shizuku)", "Exécuter une commande système (Shizuku)")
    override val iconKey = "bolt"
    override val colorKey = "slate"
    override val produces = ContentType.Text
    override val outputLabel = t("Command output", "Sortie de la commande")
    override val requirements = setOf(Requirement.Shizuku)
    override val keywords = listOf("adb", "shell", "svc", "settings", "advanced", "avancé", "commande")
    override val description = t(
        "For advanced users: runs the same command as `adb shell`, for example `settings put global ...`. Needs Shizuku.",
        "Pour les utilisateurs avancés : exécute la même commande que `adb shell`, par exemple `settings put global ...`. Nécessite Shizuku.",
    )
    override val params = listOf(
        ParamSpec("Command", t("Command", "Commande"), ParamType.Text(multiline = true), help = shizukuHelp),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val command = ctx.text("Command").trim()
        if (command.isEmpty()) throw ActionFailed(t("Type a command", "Indiquez une commande"))
        return RunValue.Text(ctx.device.runPrivileged(command).trim())
    }
}
