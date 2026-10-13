package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.HttpRequest
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.RepeatableParams
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.ShowWhen
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Turns values into JSON text (dictionaries become objects, lists become arrays). */
object RunJson {
    private fun any(v: RunValue): Any = when (v) {
        is RunValue.Text -> v.value
        is RunValue.Number -> if (v.value % 1.0 == 0.0 && Math.abs(v.value) < 1e15) v.value.toLong() else v.value
        is RunValue.Bool -> v.value
        is RunValue.Date -> v.epochMillis
        is RunValue.Items -> JSONArray(v.values.map { any(it) })
        is RunValue.Dict -> JSONObject(v.entries.mapValues { any(it.value) })
        is RunValue.Image -> "[image]"
        is RunValue.File -> v.name
        RunValue.Nothing -> JSONObject.NULL
    }

    fun toJson(v: RunValue): String = when (val x = any(v)) {
        is JSONObject -> x.toString()
        is JSONArray -> x.toString()
        else -> JSONObject.quote(x.toString())
    }
}

private val bodyMethods = setOf("POST", "PUT", "PATCH", "DELETE")

@AutoService(ActionDefinition::class)
class GetContentsOfUrlAction : ActionDefinition() {
    override val id = "is.workflow.actions.downloadurl"
    override val category = Category.Web
    override val title = t("Get contents of URL", "Obtenir le contenu de l'URL")
    override val iconKey = "download"
    override val colorKey = "blue"
    override val accepts = setOf(ContentType.Url, ContentType.Text)
    override val produces = ContentType.Any
    override val outputLabel = t("Contents of URL", "Contenu de l'URL")
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("http", "api", "download", "télécharger", "web", "image", "post", "request", "requête", "json")

    /** Left empty, the URL comes from the previous block. Headers and body are usually built with a Dictionary block. */
    override val params = listOf(
        ParamSpec("WFURL", t("URL", "URL"), ParamType.Text(), fallsBackToInput = true),
        ParamSpec(
            "Method", t("Method", "Méthode"),
            ParamType.Choice(listOf("GET", "POST", "PUT", "PATCH", "DELETE").map { ParamType.Option(it, t(it)) }),
            default = ParamValue.Choice("GET"),
        ),
        ParamSpec(
            "Headers", t("Headers", "En-têtes"), ParamType.Input, variableTypes = setOf(ContentType.Dictionary, ContentType.Any),
            help = t(
                "Optional: a dictionary of headers, for example Authorization. Build it with a Dictionary block.",
                "Facultatif : un dictionnaire d'en-têtes, par exemple Authorization. Construisez-le avec un bloc Dictionnaire.",
            ),
        ),
        ParamSpec(
            "Body", t("Body", "Corps"), ParamType.Text(multiline = true), showWhen = ShowWhen("Method", bodyMethods),
            help = t(
                "What to send. Pick a Dictionary variable to send its entries, or type your own text.",
                "Ce qu'il faut envoyer. Choisissez une variable Dictionnaire pour envoyer ses entrées, ou tapez votre texte.",
            ),
        ),
        ParamSpec(
            "BodyFormat", t("Body format", "Format du corps"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("json", t("JSON", "JSON")),
                    ParamType.Option("form", t("Form (key=value)", "Formulaire (clé=valeur)")),
                    ParamType.Option("text", t("Plain text", "Texte brut")),
                ),
            ),
            default = ParamValue.Choice("json"), showWhen = ShowWhen("Method", bodyMethods),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.valueOrInput("WFURL").display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No URL to download", "Aucune URL à télécharger"))
        val method = ctx.choice("Method") ?: "GET"
        val headers = (ctx.value("Headers") as? RunValue.Dict)?.entries?.mapValues { it.value.display() }.orEmpty()
        val format = ctx.choice("BodyFormat") ?: "json"
        val body = if (method in bodyMethods) ctx.value("Body")?.takeIf { it != RunValue.Nothing } else null

        var contentType: String? = null
        val bytes: ByteArray? = when {
            body == null -> null
            body is RunValue.Dict && format == "form" -> {
                contentType = "application/x-www-form-urlencoded"
                body.entries.entries.joinToString("&") { (k, v) ->
                    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v.display(), "UTF-8")
                }.toByteArray()
            }
            body is RunValue.Dict || body is RunValue.Items -> {
                contentType = "application/json"
                RunJson.toJson(body).toByteArray()
            }
            else -> {
                contentType = when (format) {
                    "json" -> "application/json"
                    "form" -> "application/x-www-form-urlencoded"
                    else -> "text/plain; charset=utf-8"
                }
                body.display().toByteArray()
            }
        }
        val response = ctx.device.httpRequest(
            HttpRequest(method, if (url.contains("://")) url else "https://$url", headers, bytes, contentType),
        )
        val type = response.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return if (type.startsWith("image/")) RunValue.Image(response.bytes, type) else RunValue.Text(String(response.bytes, Charsets.UTF_8))
    }
}

@AutoService(ActionDefinition::class)
class ExpandUrlAction : ActionDefinition() {
    override val id = "is.workflow.actions.url.expand"
    override val category = Category.Web
    override val title = t("Expand URL", "Développer l'URL")
    override val iconKey = "link"
    override val colorKey = "blue"
    override val input = InputSpec(t("URL", "URL"))
    override val produces = ContentType.Url
    override val outputLabel = t("Expanded URL", "URL développée")
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("short", "bit.ly", "raccourcie", "redirect")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.input.display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No URL to expand", "Aucune URL à développer"))
        val response = ctx.device.httpRequest(HttpRequest("GET", if (url.contains("://")) url else "https://$url"))
        return RunValue.Text(response.finalUrl ?: url)
    }
}

@AutoService(ActionDefinition::class)
class GetUrlsFromTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.detect.link"
    override val category = Category.Web
    override val title = t("Get URLs from input", "Obtenir les URL de l'entrée")
    override val iconKey = "link"
    override val colorKey = "blue"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.List
    override val outputLabel = t("URLs", "URL")

    override suspend fun run(ctx: ActionRunContext): RunValue =
        RunValue.Items(Regex("https?://[^\\s<>\"']+").findAll(ctx.input.display()).map { RunValue.Text(it.value.trimEnd('.', ',', ')', ';')) }.toList())
}

@AutoService(ActionDefinition::class)
class ReadRssFeedAction : ActionDefinition() {
    override val id = "is.workflow.actions.rss"
    override val category = Category.Web
    override val title = t("Read RSS feed", "Lire un flux RSS")
    override val iconKey = "list"
    override val colorKey = "blue"
    override val produces = ContentType.List
    override val outputLabel = t("RSS Items", "Éléments du flux")
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("atom", "news", "actualités", "feed", "blog")
    override val params = listOf(
        ParamSpec("WFRSSFeedURL", t("Feed URL", "URL du flux"), ParamType.Text(), fallsBackToInput = true),
        ParamSpec(
            "Limit", t("Items", "Éléments"), ParamType.Number(min = 1.0, max = 100.0), default = ParamValue.Number(10.0),
            help = t("Each item is a dictionary with title, link, date and summary.", "Chaque élément est un dictionnaire avec title, link, date et summary."),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.valueOrInput("WFRSSFeedURL").display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No feed URL", "Aucune URL de flux"))
        val response = ctx.device.httpRequest(HttpRequest("GET", if (url.contains("://")) url else "https://$url"))
        val limit = (ctx.number("Limit") ?: 10.0).toInt().coerceIn(1, 100)
        return RunValue.Items(Rss.parse(response.bytes).take(limit))
    }
}

/** Reads RSS and Atom feeds into dictionaries (title, link, date, summary). */
object Rss {
    fun parse(xml: ByteArray): List<RunValue> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            // A feed is untrusted input: no external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = try {
            factory.newDocumentBuilder().parse(java.io.ByteArrayInputStream(xml))
        } catch (e: Exception) {
            throw ActionFailed(t("This is not a valid RSS feed", "Ce n'est pas un flux RSS valide"))
        }
        val nodes = document.getElementsByTagName("item").let { if (it.length > 0) it else document.getElementsByTagName("entry") }
        return (0 until nodes.length).map { i ->
            val node = nodes.item(i) as org.w3c.dom.Element
            fun child(vararg names: String): String = names.firstNotNullOfOrNull { name ->
                node.getElementsByTagName(name).let { list -> if (list.length > 0) list.item(0) else null }
            }?.let { it.textContent.trim().ifEmpty { (it as? org.w3c.dom.Element)?.getAttribute("href").orEmpty() } }.orEmpty()
            RunValue.Dict(
                mapOf(
                    "title" to RunValue.Text(child("title")),
                    "link" to RunValue.Text(child("link")),
                    "date" to RunValue.Text(child("pubDate", "published", "updated")),
                    "summary" to RunValue.Text(child("description", "summary", "content")),
                ),
            )
        }
    }
}

/** Reads values out of dictionaries, lists and JSON text; "a.b.0.c" goes through names and positions (from 0). */
object JsonPath {
    private fun fromJson(node: Any?): RunValue = when (node) {
        null, JSONObject.NULL -> RunValue.Nothing
        is String -> RunValue.Text(node)
        is Boolean -> RunValue.Bool(node)
        is Number -> RunValue.Number(node.toDouble())
        is JSONObject -> RunValue.Dict(node.keys().asSequence().associateWith { fromJson(node.get(it)) })
        is JSONArray -> RunValue.Items((0 until node.length()).map { fromJson(node.get(it)) })
        else -> RunValue.Text(node.toString())
    }

    /** JSON text becomes dictionaries and lists; dictionaries and lists are used as they are. */
    fun parse(input: RunValue): RunValue = when (input) {
        is RunValue.Dict, is RunValue.Items -> input
        else -> try {
            fromJson(JSONTokener(input.display().trim()).nextValue())
        } catch (e: Exception) {
            throw ActionFailed(t("The input is not a dictionary or valid JSON", "L'entrée n'est pas un dictionnaire ni du JSON valide"))
        }
    }

    private fun notFound(key: String, container: RunValue): com.daisychain.engine.spec.LocalizedText {
        val keys = (container as? RunValue.Dict)?.entries?.keys?.take(12)?.joinToString(", ").orEmpty()
        return if (keys.isEmpty()) t("Key not found: $key", "Clé introuvable : $key")
        else t("Key not found: $key (the keys are: $keys)", "Clé introuvable : $key (les clés sont : $keys)")
    }

    fun read(input: RunValue, path: String): RunValue {
        var node = parse(input)
        for (key in path.split('.').filter { it.isNotEmpty() }) {
            val current = node
            node = when (current) {
                is RunValue.Dict -> current.entries[key]
                is RunValue.Items -> key.toIntOrNull()?.let { current.values.getOrNull(it) }
                else -> null
            } ?: throw ActionFailed(notFound(key, current))
        }
        return node
    }
}

@AutoService(ActionDefinition::class)
class DictionaryAction : ActionDefinition() {
    override val id = "is.workflow.actions.dictionary"
    override val category = Category.Scripting
    override val title = t("Dictionary", "Dictionnaire")
    override val iconKey = "dictionary"
    override val colorKey = "amber"
    override val produces = ContentType.Dictionary
    override val outputLabel = t("Dictionary", "Dictionnaire")
    override val keywords = listOf("key", "value", "clé", "valeur", "json", "table")
    override val params = listOf(
        ParamSpec(
            "Key", t("Key", "Clé"), ParamType.Text(),
            help = t("The name of the entry, for example the letter A.", "Le nom de l'entrée, par exemple la lettre A."),
        ),
        ParamSpec(
            "Value", t("Value", "Valeur"), ParamType.Text(),
            help = t("What is stored under that key, for example .-", "Ce qui est rangé sous cette clé, par exemple .-"),
        ),
    )

    /** More key / value pairs, one per extra row. */
    override val repeatable = RepeatableParams(
        storageKey = "Items",
        addLabel = t("Add entry", "Ajouter une entrée"),
        removeLabel = t("Remove entry", "Enlever une entrée"),
        joinOptions = emptyList(),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val entries = LinkedHashMap<String, RunValue>()
        for (row in listOf(ctx) + ctx.rows()) {
            val key = row.text("Key")
            if (key.isNotEmpty()) entries[key] = row.value("Value") ?: RunValue.Text("")
        }
        return RunValue.Dict(entries)
    }
}

@AutoService(ActionDefinition::class)
class GetDictionaryValueAction : ActionDefinition() {
    override val id = "is.workflow.actions.getvalueforkey"
    override val category = Category.Scripting
    override val title = t("Get dictionary value", "Obtenir la valeur du dictionnaire")
    override val iconKey = "dictionary"
    override val colorKey = "amber"
    override val accepts = setOf(ContentType.Dictionary, ContentType.Text)
    override val produces = ContentType.Any
    override val outputLabel = t("Dictionary Value", "Valeur du dictionnaire")
    override val keywords = listOf("json", "key", "clé", "get value")

    /** The dictionary (or JSON text) is the previous block's output unless you pick another variable. */
    override val params = listOf(
        ParamSpec(
            "WFInput", t("In dictionary", "Dans le dictionnaire"), ParamType.Input, fallsBackToInput = true,
            defaultFromType = ContentType.Dictionary,
            variableTypes = setOf(ContentType.Dictionary, ContentType.Any),
            help = t(
                "Which dictionary to look in. Automatic: the latest Dictionary block above. " +
                    "With several dictionaries, pick the one you want.",
                "Dans quel dictionnaire chercher. Automatique : le dernier bloc Dictionnaire au-dessus. " +
                    "Avec plusieurs dictionnaires, choisissez celui que vous voulez.",
            ),
        ),
        ParamSpec(
            "WFDictionaryKey", t("Key (what to look for)", "Clé (le mot à chercher)"), ParamType.Text(), fallsBackToInput = true,
            help = t(
                "The entry to look up, like a name in a phone book. Left empty, it is the value received from the previous block " +
                    "(for example the current letter inside a repeat). \"a.b.0\" goes deeper.",
                "L'entrée à chercher, comme un nom dans un annuaire. Laissée vide, c'est la valeur reçue du bloc précédent " +
                    "(par exemple la lettre en cours dans une répétition). « a.b.0 » permet d'aller plus loin.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val explicit = ctx.isSet("WFInput")
        val incoming = ctx.input
        // Which dictionary: the one chosen, else the incoming value if it is one, else the latest Dictionary block above.
        val dictionary: RunValue = when {
            explicit -> ctx.valueOrInput("WFInput")
            incoming is RunValue.Dict -> incoming
            ctx.session.lastDictionary != null -> ctx.session.lastDictionary!!
            else -> incoming
        }
        val looksLikeJson = dictionary.display().trim().let { it.startsWith("{") || it.startsWith("[") }
        if (dictionary !is RunValue.Dict && dictionary !is RunValue.Items && !looksLikeJson) {
            throw ActionFailed(
                t(
                    "No dictionary found: add a Dictionary block before this one, or choose one",
                    "Aucun dictionnaire : ajoutez un bloc Dictionnaire avant celui-ci, ou choisissez-en un",
                ),
            )
        }
        val typed = ctx.text("WFDictionaryKey").trim()
        // Left empty, the key is the incoming value (for example the current letter), unless that is the dictionary itself.
        val key = typed.ifEmpty { if (incoming === dictionary) "" else incoming.display().trim() }
        if (key.isEmpty()) throw ActionFailed(t("Type the key to look for", "Indiquez la clé à chercher"))
        return JsonPath.read(dictionary, key)
    }
}

@AutoService(ActionDefinition::class)
class SaveToPhotosAction : ActionDefinition() {
    override val id = "is.workflow.actions.savetocameraroll"
    override val category = Category.Sharing
    override val title = t("Save to gallery", "Enregistrer dans la galerie")
    override val iconKey = "save"
    override val colorKey = "blue"
    override val input = InputSpec(t("Image", "Image"))
    override val accepts = setOf(ContentType.Image)
    override val keywords = listOf("photos", "photo", "image", "save")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = ctx.input as? RunValue.Image
            ?: throw ActionFailed(t("There is no image to save", "Il n'y a aucune image à enregistrer"))
        ctx.device.saveImage(image.bytes, image.mime)
        return RunValue.Nothing
    }
}
