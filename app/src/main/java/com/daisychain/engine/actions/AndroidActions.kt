package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.IntentSpec
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.net.URLEncoder

private fun choiceOf(vararg pairs: Pair<String, com.daisychain.engine.spec.LocalizedText>) =
    ParamType.Choice(pairs.map { (id, label) -> ParamType.Option(id, label) })

private val timeUnitChoice = choiceOf(
    "seconds" to t("seconds", "secondes"),
    "minutes" to t("minutes", "minutes"),
    "hours" to t("hours", "heures"),
    "days" to t("days", "jours"),
)

private fun unitMillis(unit: String?): Long = when (unit) {
    "minutes" -> 60_000L
    "hours" -> 3_600_000L
    "days" -> 86_400_000L
    else -> 1_000L
}

@AutoService(ActionDefinition::class)
class GetDeviceDetailsAction : ActionDefinition() {
    override val id = "is.workflow.actions.getdevicedetails"
    override val category = Category.Device
    override val title = t("Get device details", "Obtenir les détails de l'appareil")
    override val iconKey = "apps"
    override val colorKey = "teal"
    override val produces = ContentType.Text
    override val outputLabel = t("Device Detail", "Détail de l'appareil")
    override val keywords = listOf("model", "modèle", "version", "screen", "écran", "android")
    override val params = listOf(
        ParamSpec(
            "WFDeviceDetail", t("Detail", "Détail"),
            choiceOf(
                "model" to t("Model", "Modèle"),
                "manufacturer" to t("Manufacturer", "Fabricant"),
                "name" to t("Device name", "Nom de l'appareil"),
                "androidVersion" to t("Android version", "Version d'Android"),
                "sdk" to t("Android API level", "Niveau d'API Android"),
                "screen" to t("Screen size", "Taille de l'écran"),
                "language" to t("Language", "Langue"),
                "charging" to t("Charging state", "État de charge"),
            ),
            default = ParamValue.Choice("model"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.device.deviceDetail(ctx.choice("WFDeviceDetail") ?: "model"))
}

@AutoService(ActionDefinition::class)
class GetIpAddressAction : ActionDefinition() {
    override val id = "is.workflow.actions.getipaddress"
    override val category = Category.Web
    override val title = t("Get IP address", "Obtenir l'adresse IP")
    override val iconKey = "wifi"
    override val colorKey = "blue"
    override val produces = ContentType.Text
    override val outputLabel = t("IP Address", "Adresse IP")
    override val requirements = setOf(Requirement.Network)
    override val params = listOf(
        ParamSpec(
            "WFIPAddressSourceOption", t("Address", "Adresse"),
            choiceOf("Local" to t("Local (on the Wi-Fi)", "Locale (sur le Wi-Fi)"), "External" to t("External (on the internet)", "Externe (sur Internet)")),
            default = ParamValue.Choice("Local"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        if (ctx.choice("WFIPAddressSourceOption") == "External") {
            val response = ctx.device.httpRequest(com.daisychain.engine.run.HttpRequest("GET", "https://api.ipify.org"))
            return RunValue.Text(String(response.bytes, Charsets.UTF_8).trim())
        }
        return RunValue.Text(ctx.device.localIpAddress() ?: throw ActionFailed(t("No network connection", "Aucune connexion réseau")))
    }
}

@AutoService(ActionDefinition::class)
class PlaySoundAction : ActionDefinition() {
    override val id = "is.workflow.actions.playsound"
    override val category = Category.Device
    override val title = t("Play sound", "Jouer un son")
    override val iconKey = "music"
    override val colorKey = "teal"
    override val keywords = listOf("ring", "beep", "sonnerie", "notification")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.playNotificationSound()
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class GoHomeAction : ActionDefinition() {
    override val id = "is.workflow.actions.gohome"
    override val category = Category.Apps
    override val title = t("Go to home screen", "Retourner à l'écran d'accueil")
    override val iconKey = "home"
    override val colorKey = "indigo"

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.goHome()
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenSettingsAction : ActionDefinition() {
    override val id = "app.daisychain.settings.open"
    override val category = Category.Device
    override val title = t("Open settings", "Ouvrir les réglages")
    override val iconKey = "apps"
    override val colorKey = "slate"
    override val keywords = listOf("wifi", "bluetooth", "airplane", "avion", "data", "données", "battery", "batterie", "panel", "panneau")
    override val params = listOf(
        ParamSpec(
            "Target", t("Setting", "Réglage"),
            choiceOf(
                "internet_panel" to t("Internet panel (Wi-Fi, mobile data)", "Panneau Internet (Wi-Fi, données mobiles)"),
                "wifi" to t("Wi-Fi", "Wi-Fi"),
                "bluetooth" to t("Bluetooth", "Bluetooth"),
                "airplane" to t("Airplane mode", "Mode avion"),
                "mobile_network" to t("Mobile network", "Réseau mobile"),
                "volume_panel" to t("Volume panel", "Panneau du volume"),
                "nfc_panel" to t("NFC panel", "Panneau NFC"),
                "display" to t("Display and brightness", "Affichage et luminosité"),
                "battery_saver" to t("Battery saver", "Économie d'énergie"),
                "sound" to t("Sound", "Son"),
                "location" to t("Location", "Localisation"),
                "dnd_access" to t("Do Not Disturb access", "Accès Ne pas déranger"),
                "notifications" to t("App notifications", "Notifications de l'application"),
                "apps" to t("Apps", "Applications"),
                "accessibility" to t("Accessibility", "Accessibilité"),
                "date" to t("Date and time", "Date et heure"),
                "language" to t("Language", "Langue"),
                "storage" to t("Storage", "Stockage"),
                "vpn" to t("VPN", "VPN"),
                "all" to t("All settings", "Tous les réglages"),
            ),
            default = ParamValue.Choice("internet_panel"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.openSettings(ctx.choice("Target") ?: "internet_panel")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenShortcutAction : ActionDefinition() {
    override val id = "is.workflow.actions.openworkflow"
    override val category = Category.Scripting
    override val title = t("Open shortcut", "Ouvrir un raccourci")
    override val iconKey = "play"
    override val colorKey = "slate"
    override val params = listOf(ParamSpec("WFWorkflowName", t("Shortcut name", "Nom du raccourci"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("WFWorkflowName").trim()
        val id = ctx.session.library?.idOf(name) ?: throw ActionFailed(t("Shortcut not found: $name", "Raccourci introuvable : $name"))
        ctx.device.openShortcut(id)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class GetMyShortcutsAction : ActionDefinition() {
    override val id = "is.workflow.actions.getmyworkflows"
    override val category = Category.Scripting
    override val title = t("Get my shortcuts", "Obtenir mes raccourcis")
    override val iconKey = "list"
    override val colorKey = "slate"
    override val produces = ContentType.List
    override val outputLabel = t("My Shortcuts", "Mes raccourcis")

    override suspend fun run(ctx: ActionRunContext): RunValue =
        RunValue.Items(ctx.session.library?.names().orEmpty().map { RunValue.Text(it) })
}

@AutoService(ActionDefinition::class)
class SendSmsAction : ActionDefinition() {
    override val id = "is.workflow.actions.sendmessage"
    override val category = Category.Sharing
    override val title = t("Send message (SMS)", "Envoyer un message (SMS)")
    override val iconKey = "mail"
    override val colorKey = "green"
    override val keywords = listOf("sms", "text", "texto")
    override val params = listOf(
        ParamSpec("Recipient", t("To", "À"), ParamType.Text()),
        ParamSpec(
            "Message", t("Message", "Message"), ParamType.Text(multiline = true), fallsBackToInput = true,
            help = t("It opens the messages app ready to send: you press send.", "Ouvre l'application de messages prête à envoyer : vous appuyez sur envoyer."),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.composeSms(ctx.text("Recipient").trim(), ctx.valueOrInput("Message").display())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class SendEmailAction : ActionDefinition() {
    override val id = "is.workflow.actions.sendemail"
    override val category = Category.Sharing
    override val title = t("Send email", "Envoyer un e-mail")
    override val iconKey = "mail"
    override val colorKey = "green"
    override val keywords = listOf("mail", "courriel")
    override val params = listOf(
        ParamSpec("Recipient", t("To", "À"), ParamType.Text()),
        ParamSpec("Subject", t("Subject", "Objet"), ParamType.Text()),
        ParamSpec("Body", t("Body", "Message"), ParamType.Text(multiline = true), fallsBackToInput = true),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.composeEmail(ctx.text("Recipient").trim(), ctx.text("Subject"), ctx.valueOrInput("Body").display())
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class CreateAlarmAction : ActionDefinition() {
    override val id = "is.workflow.actions.alarm.create"
    override val category = Category.Device
    override val title = t("Create alarm", "Créer une alarme")
    override val iconKey = "alarm"
    override val colorKey = "amber"
    override val keywords = listOf("clock", "horloge", "wake", "réveil")
    override val params = listOf(
        ParamSpec("Hour", t("Hour", "Heure"), ParamType.Number(min = 0.0, max = 23.0), default = ParamValue.Number(7.0)),
        ParamSpec("Minute", t("Minute", "Minute"), ParamType.Number(min = 0.0, max = 59.0), default = ParamValue.Number(0.0)),
        ParamSpec("Label", t("Label", "Nom"), ParamType.Text()),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setAlarm(
            (ctx.number("Hour") ?: 7.0).toInt().coerceIn(0, 23),
            (ctx.number("Minute") ?: 0.0).toInt().coerceIn(0, 59),
            ctx.text("Label"),
        )
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class StartTimerAction : ActionDefinition() {
    override val id = "is.workflow.actions.timer.start"
    override val category = Category.Device
    override val title = t("Start timer", "Démarrer un minuteur")
    override val iconKey = "clock"
    override val colorKey = "amber"
    override val keywords = listOf("countdown", "compte à rebours")
    override val params = listOf(
        ParamSpec("Duration", t("Duration", "Durée"), ParamType.Number(min = 1.0), default = ParamValue.Number(5.0)),
        ParamSpec("Unit", t("Unit", "Unité"), choiceOf("seconds" to t("seconds", "secondes"), "minutes" to t("minutes", "minutes"), "hours" to t("hours", "heures")), default = ParamValue.Choice("minutes")),
        ParamSpec("Label", t("Label", "Nom"), ParamType.Text()),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val seconds = ((ctx.number("Duration") ?: 1.0) * unitMillis(ctx.choice("Unit")) / 1000).toInt().coerceAtLeast(1)
        ctx.device.startTimer(seconds, ctx.text("Label"))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class ScheduleNotificationAction : ActionDefinition() {
    override val id = "app.daisychain.notification.schedule"
    override val category = Category.Notifications
    override val title = t("Schedule notification", "Planifier une notification")
    override val iconKey = "bell"
    override val colorKey = "amber"
    override val requirements = setOf(Requirement.Notifications)
    override val keywords = listOf("reminder", "rappel", "later", "plus tard")
    override val params = listOf(
        ParamSpec("Title", t("Title", "Titre"), ParamType.Text()),
        ParamSpec("Body", t("Body", "Corps"), ParamType.Text(multiline = true)),
        ParamSpec("Delay", t("In", "Dans"), ParamType.Number(min = 0.0), default = ParamValue.Number(10.0)),
        ParamSpec("Unit", t("Unit", "Unité"), timeUnitChoice, default = ParamValue.Choice("minutes")),
        ParamSpec(
            "At", t("At (date)", "À (date)"), ParamType.Input,
            variableTypes = setOf(ContentType.Date, ContentType.Any),
            help = t("Optional: a date to notify at, instead of the delay.", "Facultatif : une date à laquelle notifier, à la place du délai."),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val at = ctx.value("At")?.let { dateOf(it) ?: throw ActionFailed(t("The input is not a date", "L'entrée n'est pas une date")) }
            ?: (System.currentTimeMillis() + ((ctx.number("Delay") ?: 0.0) * unitMillis(ctx.choice("Unit"))).toLong())
        ctx.device.scheduleNotification(ctx.text("Title"), ctx.text("Body"), at)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class OpenInMapsAction : ActionDefinition() {
    override val id = "is.workflow.actions.searchmaps"
    override val category = Category.Apps
    override val title = t("Open in Maps", "Ouvrir dans Maps")
    override val iconKey = "location"
    override val colorKey = "blue"
    override val keywords = listOf("waze", "google maps", "address", "adresse", "navigate")
    override val params = listOf(ParamSpec("Query", t("Place or address", "Lieu ou adresse"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val query = ctx.valueOrInput("Query").display().trim()
        if (query.isEmpty()) throw ActionFailed(t("No place to search", "Aucun lieu à chercher"))
        ctx.device.openMaps(query)
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class GetMapsUrlAction : ActionDefinition() {
    override val id = "is.workflow.actions.getmapsurl"
    override val category = Category.Web
    override val title = t("Get Maps URL", "Obtenir l'URL de carte")
    override val iconKey = "location"
    override val colorKey = "blue"
    override val produces = ContentType.Url
    override val outputLabel = t("Maps URL", "URL de carte")
    override val params = listOf(ParamSpec("Query", t("Place or address", "Lieu ou adresse"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue =
        RunValue.Text("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(ctx.valueOrInput("Query").display().trim(), "UTF-8"))
}

@AutoService(ActionDefinition::class)
class AddCalendarEventAction : ActionDefinition() {
    override val id = "is.workflow.actions.addnewevent"
    override val category = Category.Apps
    override val title = t("Add calendar event", "Ajouter un événement")
    override val iconKey = "calendar"
    override val colorKey = "indigo"
    override val keywords = listOf("agenda", "rendez-vous", "meeting")
    override val params = listOf(
        ParamSpec("Title", t("Title", "Titre"), ParamType.Text()),
        ParamSpec("Start", t("Start (date)", "Début (date)"), ParamType.Input, variableTypes = setOf(ContentType.Date, ContentType.Any)),
        ParamSpec("Minutes", t("Duration (min)", "Durée (min)"), ParamType.Number(min = 1.0), default = ParamValue.Number(60.0)),
        ParamSpec("Location", t("Location", "Lieu"), ParamType.Text()),
        ParamSpec("Notes", t("Notes", "Notes"), ParamType.Text(multiline = true)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val start = ctx.value("Start")?.let { dateOf(it) ?: throw ActionFailed(t("The input is not a date", "L'entrée n'est pas une date")) } ?: System.currentTimeMillis()
        val end = start + ((ctx.number("Minutes") ?: 60.0) * 60_000).toLong()
        ctx.device.addCalendarEvent(ctx.text("Title"), start, end, ctx.text("Location"), ctx.text("Notes"))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class AddContactAction : ActionDefinition() {
    override val id = "is.workflow.actions.addnewcontact"
    override val category = Category.Apps
    override val title = t("Add contact", "Ajouter un contact")
    override val iconKey = "call"
    override val colorKey = "indigo"
    override val params = listOf(
        ParamSpec("Name", t("Name", "Nom"), ParamType.Text()),
        ParamSpec("Phone", t("Phone", "Téléphone"), ParamType.Text()),
        ParamSpec("Email", t("Email", "E-mail"), ParamType.Text()),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.addContact(ctx.text("Name"), ctx.text("Phone"), ctx.text("Email"))
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class MediaControlAction : ActionDefinition() {
    override val id = "app.daisychain.media.control"
    override val category = Category.Device
    override val title = t("Control media", "Contrôler la lecture")
    override val iconKey = "music"
    override val colorKey = "teal"
    override val keywords = listOf("play", "pause", "next", "previous", "spotify", "musique")
    override val params = listOf(
        ParamSpec(
            "Action", t("Action", "Action"),
            choiceOf(
                "playpause" to t("Play / pause", "Lecture / pause"),
                "next" to t("Next", "Suivant"),
                "previous" to t("Previous", "Précédent"),
                "stop" to t("Stop", "Stop"),
            ),
            default = ParamValue.Choice("playpause"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.mediaControl(ctx.choice("Action") ?: "playpause")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class SetWallpaperAction : ActionDefinition() {
    override val id = "app.daisychain.wallpaper.set"
    override val category = Category.Device
    override val title = t("Set wallpaper", "Définir le fond d'écran")
    override val iconKey = "camera"
    override val colorKey = "amber"
    override val input = InputSpec(t("Image", "Image"))
    override val keywords = listOf("background", "arrière-plan")
    override val params = listOf(
        ParamSpec(
            "Target", t("On", "Sur"),
            choiceOf("both" to t("Home and lock screen", "Accueil et verrouillage"), "home" to t("Home screen", "Écran d'accueil"), "lock" to t("Lock screen", "Écran de verrouillage")),
            default = ParamValue.Choice("both"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = ctx.input as? RunValue.Image ?: throw ActionFailed(t("There is no image to use", "Il n'y a aucune image à utiliser"))
        ctx.device.setWallpaper(image.bytes, ctx.choice("Target") ?: "both")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class InstalledAppsAction : ActionDefinition() {
    override val id = "app.daisychain.apps.installed"
    override val category = Category.Apps
    override val title = t("Get installed apps", "Obtenir les applications installées")
    override val iconKey = "apps"
    override val colorKey = "indigo"
    override val produces = ContentType.List
    override val outputLabel = t("Apps", "Applications")

    /** A list of dictionaries with the keys "name" and "package". */
    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Items(
        ctx.device.installedApps().map { RunValue.Dict(mapOf("name" to RunValue.Text(it.name), "package" to RunValue.Text(it.packageName))) },
    )
}

@AutoService(ActionDefinition::class)
class SendIntentAction : ActionDefinition() {
    override val id = "app.daisychain.intent.send"
    override val category = Category.Apps
    override val title = t("Send Android intent", "Envoyer un Intent Android")
    override val iconKey = "bolt"
    override val colorKey = "slate"
    override val keywords = listOf("tasker", "broadcast", "extras", "advanced", "avancé")
    override val params = listOf(
        ParamSpec(
            "Action", t("Action", "Action"), ParamType.Text(),
            help = t(
                "For example android.intent.action.VIEW. This block talks to other apps directly: for advanced use.",
                "Par exemple android.intent.action.VIEW. Ce bloc parle directement aux autres applications : usage avancé.",
            ),
        ),
        ParamSpec("Data", t("Data (URI)", "Données (URI)"), ParamType.Text()),
        ParamSpec("Package", t("App (package)", "Application (paquet)"), ParamType.App),
        ParamSpec("Mime", t("Type (MIME)", "Type (MIME)"), ParamType.Text()),
        ParamSpec(
            "Extras", t("Extras", "Extras"), ParamType.Input, variableTypes = setOf(ContentType.Dictionary, ContentType.Any),
            help = t("A dictionary: each key becomes an extra of the intent.", "Un dictionnaire : chaque clé devient un extra de l'intent."),
        ),
        ParamSpec(
            "Kind", t("Send as", "Envoyer comme"),
            choiceOf("activity" to t("Screen (activity)", "Écran (activité)"), "broadcast" to t("Broadcast", "Diffusion (broadcast)")),
            default = ParamValue.Choice("activity"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val action = ctx.text("Action").trim()
        if (action.isEmpty()) throw ActionFailed(t("Type the intent action", "Indiquez l'action de l'intent"))
        val extras = (ctx.value("Extras") as? RunValue.Dict)?.entries?.mapValues { it.value.display() }.orEmpty()
        ctx.device.sendIntent(
            IntentSpec(
                action = action,
                data = ctx.text("Data").trim(),
                packageName = ctx.text("Package").trim(),
                mimeType = ctx.text("Mime").trim(),
                extras = extras,
                broadcast = ctx.choice("Kind") == "broadcast",
            ),
        )
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class DoNotDisturbAction : ActionDefinition() {
    override val id = "is.workflow.actions.dnd.set"
    override val category = Category.Device
    override val title = t("Set Do Not Disturb", "Régler Ne pas déranger")
    override val iconKey = "bell"
    override val colorKey = "slate"
    override val keywords = listOf("focus", "silence", "dnd", "concentration")
    override val params = listOf(
        ParamSpec(
            "Mode", t("Mode", "Mode"),
            choiceOf(
                "off" to t("Off", "Désactivé"),
                "priority" to t("Priority only", "Prioritaires seulement"),
                "alarms" to t("Alarms only", "Alarmes seulement"),
                "silence" to t("Total silence", "Silence total"),
            ),
            default = ParamValue.Choice("priority"),
            help = t(
                "The first time, Android asks you to allow Daisychain to control Do Not Disturb.",
                "La première fois, Android vous demande d'autoriser Daisychain à contrôler Ne pas déranger.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.setDoNotDisturb(ctx.choice("Mode") ?: "priority")
        return RunValue.Nothing
    }
}
