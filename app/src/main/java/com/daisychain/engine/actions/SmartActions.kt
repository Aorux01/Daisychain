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
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService

/** Languages offered for dictation and translation (BCP-47 codes). */
private val languages = listOf(
    "en" to t("English", "Anglais"), "fr" to t("French", "Français"), "it" to t("Italian", "Italien"), "de" to t("German", "Allemand"),
    "es" to t("Spanish", "Espagnol"), "pt" to t("Portuguese", "Portugais"), "nl" to t("Dutch", "Néerlandais"), "pl" to t("Polish", "Polonais"),
    "ru" to t("Russian", "Russe"), "ja" to t("Japanese", "Japonais"), "zh" to t("Chinese", "Chinois"), "ko" to t("Korean", "Coréen"),
    "ar" to t("Arabic", "Arabe"), "tr" to t("Turkish", "Turc"), "sv" to t("Swedish", "Suédois"),
)

private fun languageChoice(withAuto: Boolean, autoLabel: com.daisychain.engine.spec.LocalizedText) = ParamType.Choice(
    (if (withAuto) listOf(ParamType.Option("auto", autoLabel)) else emptyList()) + languages.map { (code, name) -> ParamType.Option(code, name) },
)

@AutoService(ActionDefinition::class)
class ExtractTextFromImageAction : ActionDefinition() {
    override val id = "is.workflow.actions.extracttextfromimage"
    override val category = Category.Text
    override val title = t("Extract text from image", "Extraire le texte d'une image")
    override val iconKey = "text"
    override val colorKey = "plum"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Text
    override val outputLabel = t("Text from Image", "Texte de l'image")
    override val keywords = listOf("ocr", "scan", "read", "lire", "recognize", "reconnaître")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = when (val v = ctx.input) {
            is RunValue.Image -> v.bytes
            is RunValue.File -> if (v.mime.startsWith("image/")) bytesOf(ctx, v) else throw ActionFailed(t("The input is not an image", "L'entrée n'est pas une image"))
            else -> throw ActionFailed(t("The input is not an image", "L'entrée n'est pas une image"))
        }
        return RunValue.Text(ctx.device.recognizeText(image))
    }
}

@AutoService(ActionDefinition::class)
class DictateTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.dictatetext"
    override val category = Category.Text
    override val title = t("Dictate text", "Dicter du texte")
    override val iconKey = "speak"
    override val colorKey = "plum"
    override val produces = ContentType.Text
    override val outputLabel = t("Dictated Text", "Texte dicté")
    override val requirements = setOf(Requirement.Microphone)
    override val keywords = listOf("speech", "voice", "voix", "microphone", "parler")
    override val params = listOf(
        ParamSpec(
            "Language", t("Language", "Langue"), languageChoice(true, t("Phone language", "Langue du téléphone")),
            default = ParamValue.Choice("auto"),
            help = t("Speak after the beep. The phone's speech service does the listening.", "Parlez après le bip. C'est le service vocal du téléphone qui écoute."),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.device.dictate(ctx.choice("Language") ?: "auto"))
}

@AutoService(ActionDefinition::class)
class TranslateTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.text.translate"
    override val category = Category.Text
    override val title = t("Translate text", "Traduire le texte")
    override val iconKey = "speak"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.Text
    override val outputLabel = t("Translated Text", "Texte traduit")
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("language", "langue", "traduction")
    override val params = listOf(
        ParamSpec("From", t("From", "De"), languageChoice(true, t("Detect automatically", "Détecter automatiquement")), default = ParamValue.Choice("auto")),
        ParamSpec(
            "To", t("To", "Vers"), languageChoice(false, t("", "")), default = ParamValue.Choice("en"),
            help = t(
                "Translation works on the phone. The first time for a language, its data is downloaded (about 30 MB).",
                "La traduction se fait sur le téléphone. La première fois pour une langue, ses données sont téléchargées (environ 30 Mo).",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val text = ctx.input.display()
        if (text.isBlank()) return RunValue.Text("")
        val from = ctx.choice("From")?.takeIf { it != "auto" }
        return RunValue.Text(ctx.device.translate(text, from, ctx.choice("To") ?: "en"))
    }
}

@AutoService(ActionDefinition::class)
class DetectLanguageAction : ActionDefinition() {
    override val id = "is.workflow.actions.detectlanguage"
    override val category = Category.Text
    override val title = t("Detect language", "Détecter la langue")
    override val iconKey = "speak"
    override val colorKey = "plum"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.Text
    override val outputLabel = t("Language", "Langue")
    override val keywords = listOf("language", "langue")

    /** The language code, for example fr or en ("und" when it cannot tell). */
    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text(ctx.device.detectLanguage(ctx.input.display()))
}

@AutoService(ActionDefinition::class)
class MakeQrCodeAction : ActionDefinition() {
    override val id = "is.workflow.actions.generatebarcode"
    override val category = Category.Media
    override val title = t("Make QR code", "Créer un QR code")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Text", "Texte"))
    override val produces = ContentType.Image
    override val outputLabel = t("QR Code", "QR code")
    override val keywords = listOf("barcode", "code-barres", "wifi", "link", "lien")
    override val params = listOf(
        ParamSpec("Size", t("Size (px)", "Taille (px)"), ParamType.Number(min = 100.0, max = 2000.0), default = ParamValue.Number(600.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val text = ctx.input.display()
        if (text.isEmpty()) throw ActionFailed(t("There is nothing to put in the QR code", "Il n'y a rien à mettre dans le QR code"))
        return RunValue.Image(ctx.device.makeQrCode(text, (ctx.number("Size") ?: 600.0).toInt().coerceIn(100, 2000)), "image/png")
    }
}

@AutoService(ActionDefinition::class)
class ScanQrCodeAction : ActionDefinition() {
    override val id = "is.workflow.actions.scanbarcode"
    override val category = Category.Media
    override val title = t("Read QR code from image", "Lire un QR code dans une image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"), t("Take a photo or choose one first, then give it to this block.", "Prenez ou choisissez d'abord une photo, puis donnez-la à ce bloc."))
    override val produces = ContentType.Text
    override val outputLabel = t("QR Code Content", "Contenu du QR code")
    override val keywords = listOf("scan", "scanner", "barcode", "code-barres")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val bytes = when (val v = ctx.input) {
            is RunValue.Image -> v.bytes
            is RunValue.File -> bytesOf(ctx, v)
            else -> throw ActionFailed(t("The input is not an image", "L'entrée n'est pas une image"))
        }
        val codes = ctx.device.scanCodes(bytes)
        if (codes.isEmpty()) throw ActionFailed(t("No QR code found in the image", "Aucun QR code trouvé dans l'image"))
        return RunValue.Text(codes.first())
    }
}

@AutoService(ActionDefinition::class)
class ShowWebPageAction : ActionDefinition() {
    override val id = "is.workflow.actions.showwebpage"
    override val category = Category.Web
    override val title = t("Show web page", "Afficher une page web")
    override val iconKey = "link"
    override val colorKey = "blue"
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("browser", "navigateur", "open", "site")
    override val params = listOf(ParamSpec("WFURL", t("URL", "URL"), ParamType.Text(), fallsBackToInput = true))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.valueOrInput("WFURL").display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No URL to show", "Aucune URL à afficher"))
        ctx.ui.showWebPage(if (url.contains("://")) url else "https://$url")
        return RunValue.Nothing
    }
}

/** Turns a web page into readable text. */
object Html {
    fun toText(html: String): String {
        var s = html
        s = Regex("(?is)<(script|style|noscript|head|svg)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>").replace(s, "\n")
        s = Regex("(?s)<[^>]+>").replace(s, " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        return s.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun title(html: String): String = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let { toText(it) }.orEmpty()
}

@AutoService(ActionDefinition::class)
class GetWebPageTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.getarticle"
    override val category = Category.Web
    override val title = t("Get text from web page", "Obtenir le texte d'une page web")
    override val iconKey = "text"
    override val colorKey = "blue"
    override val produces = ContentType.Text
    override val outputLabel = t("Page Text", "Texte de la page")
    override val requirements = setOf(Requirement.Network)
    override val keywords = listOf("article", "scrape", "html", "read", "lire")
    override val params = listOf(
        ParamSpec("WFURL", t("URL", "URL"), ParamType.Text(), fallsBackToInput = true),
        ParamSpec(
            "Part", t("Get", "Obtenir"),
            ParamType.Choice(listOf(ParamType.Option("text", t("The text", "Le texte")), ParamType.Option("title", t("The title", "Le titre")))),
            default = ParamValue.Choice("text"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val url = ctx.valueOrInput("WFURL").display().trim()
        if (url.isEmpty()) throw ActionFailed(t("No URL to read", "Aucune URL à lire"))
        val html = String(ctx.device.httpRequest(HttpRequest("GET", if (url.contains("://")) url else "https://$url")).bytes, Charsets.UTF_8)
        return RunValue.Text(if (ctx.choice("Part") == "title") Html.title(html) else Html.toText(html))
    }
}
