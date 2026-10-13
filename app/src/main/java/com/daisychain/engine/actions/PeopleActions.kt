package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
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
class FindEventsAction : ActionDefinition() {
    override val id = "is.workflow.actions.filter.calendarevents"
    override val category = Category.Apps
    override val title = t("Find calendar events", "Chercher des événements")
    override val iconKey = "calendar"
    override val colorKey = "indigo"
    override val produces = ContentType.List
    override val outputLabel = t("Calendar Events", "Événements")
    override val requirements = setOf(Requirement.Calendar)
    override val keywords = listOf("agenda", "meeting", "rendez-vous", "today", "aujourd'hui")
    override val params = listOf(
        ParamSpec(
            "From", t("From (date)", "À partir de (date)"), ParamType.Input, variableTypes = setOf(ContentType.Date, ContentType.Any),
            help = t("Left on automatic, it starts now.", "En automatique, la recherche commence maintenant."),
        ),
        ParamSpec("Days", t("Days ahead", "Jours à venir"), ParamType.Number(min = 1.0, max = 365.0), default = ParamValue.Number(7.0)),
        ParamSpec(
            "Limit", t("At most", "Au plus"), ParamType.Number(min = 1.0, max = 200.0), default = ParamValue.Number(20.0),
            help = t(
                "Each event is a dictionary with title, start, end, location, notes and allDay.",
                "Chaque événement est un dictionnaire avec title, start, end, location, notes et allDay.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val from = ctx.value("From")?.let { dateOf(it) ?: throw ActionFailed(t("The input is not a date", "L'entrée n'est pas une date")) }
            ?: System.currentTimeMillis()
        val to = from + ((ctx.number("Days") ?: 7.0) * 86_400_000).toLong()
        val limit = (ctx.number("Limit") ?: 20.0).toInt().coerceIn(1, 200)
        return RunValue.Items(
            ctx.device.findEvents(from, to, limit).map { e ->
                RunValue.Dict(
                    linkedMapOf(
                        "title" to RunValue.Text(e.title),
                        "start" to RunValue.Date(e.startMillis),
                        "end" to RunValue.Date(e.endMillis),
                        "location" to RunValue.Text(e.location),
                        "notes" to RunValue.Text(e.notes),
                        "allDay" to RunValue.Bool(e.allDay),
                    ),
                )
            },
        )
    }
}

@AutoService(ActionDefinition::class)
class FindContactsAction : ActionDefinition() {
    override val id = "is.workflow.actions.filter.contacts"
    override val category = Category.Apps
    override val title = t("Find contacts", "Chercher des contacts")
    override val iconKey = "call"
    override val colorKey = "indigo"
    override val produces = ContentType.List
    override val outputLabel = t("Contacts", "Contacts")
    override val requirements = setOf(Requirement.Contacts)
    override val keywords = listOf("phone", "téléphone", "number", "numéro", "email")
    override val params = listOf(
        ParamSpec("Query", t("Name contains", "Le nom contient"), ParamType.Text(), fallsBackToInput = true),
        ParamSpec(
            "Limit", t("At most", "Au plus"), ParamType.Number(min = 1.0, max = 200.0), default = ParamValue.Number(10.0),
            help = t(
                "Each contact is a dictionary with name, phone and email (the first of each).",
                "Chaque contact est un dictionnaire avec name, phone et email (le premier de chaque).",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val query = ctx.valueOrInput("Query").display().trim()
        val limit = (ctx.number("Limit") ?: 10.0).toInt().coerceIn(1, 200)
        return RunValue.Items(
            ctx.device.findContacts(query, limit).map { c ->
                RunValue.Dict(
                    linkedMapOf(
                        "name" to RunValue.Text(c.name),
                        "phone" to RunValue.Text(c.phones.firstOrNull().orEmpty()),
                        "email" to RunValue.Text(c.emails.firstOrNull().orEmpty()),
                    ),
                )
            },
        )
    }
}

@AutoService(ActionDefinition::class)
class SendSmsSilentAction : ActionDefinition() {
    override val id = "app.daisychain.sms.send"
    override val category = Category.Sharing
    override val title = t("Send SMS without opening the app", "Envoyer un SMS sans ouvrir l'application")
    override val iconKey = "mail"
    override val colorKey = "green"
    override val requirements = setOf(Requirement.SendSms)
    override val keywords = listOf("text", "texto", "silent", "automatique")
    override val params = listOf(
        ParamSpec("Recipient", t("To", "À"), ParamType.Text()),
        ParamSpec(
            "Message", t("Message", "Message"), ParamType.Text(multiline = true), fallsBackToInput = true,
            help = t(
                "Sent right away, without asking again. Android asks for the SMS permission the first time.",
                "Envoyé tout de suite, sans redemander. Android demande l'autorisation SMS la première fois.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val number = ctx.text("Recipient").trim()
        if (number.isEmpty()) throw ActionFailed(t("No phone number", "Aucun numéro de téléphone"))
        ctx.device.sendSms(number, ctx.valueOrInput("Message").display())
        return RunValue.Nothing
    }
}
