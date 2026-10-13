package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val mimeByExtension = mapOf(
    "txt" to "text/plain", "csv" to "text/csv", "json" to "application/json", "xml" to "application/xml", "html" to "text/html",
    "md" to "text/markdown", "pdf" to "application/pdf", "zip" to "application/zip",
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav", "mp4" to "video/mp4",
)

fun mimeFor(name: String, fallback: String = "application/octet-stream"): String =
    mimeByExtension[name.substringAfterLast('.', "").lowercase()] ?: fallback

private fun extensionFor(mime: String): String = when (mime) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "audio/mp4" -> "m4a"
    "video/mp4" -> "mp4"
    "application/json" -> "json"
    else -> "txt"
}

/** The content of a value as bytes: a file's content, an image's bytes, or the text of anything else. */
suspend fun bytesOf(ctx: ActionRunContext, v: RunValue): ByteArray = when (v) {
    is RunValue.File -> v.bytes ?: v.uri?.let { ctx.device.readUri(it) } ?: ByteArray(0)
    is RunValue.Image -> v.bytes
    is RunValue.Dict, is RunValue.Items -> RunJson.toJson(v).toByteArray(Charsets.UTF_8)
    else -> v.display().toByteArray(Charsets.UTF_8)
}

private fun isText(mime: String) = mime.startsWith("text/") || mime in setOf("application/json", "application/xml", "application/javascript")

@AutoService(ActionDefinition::class)
class SetNameAction : ActionDefinition() {
    override val id = "is.workflow.actions.setitemname"
    override val category = Category.Files
    override val title = t("Set name", "Définir le nom")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val input = InputSpec(
        t("Item", "Élément"),
        t("What to name: a text, an image or a file. It becomes a file with that name.", "Ce qu'il faut nommer : un texte, une image ou un fichier. Il devient un fichier portant ce nom."),
    )
    override val produces = ContentType.File
    override val outputLabel = t("Named File", "Fichier nommé")
    override val keywords = listOf("rename", "renommer", "filename", "nom de fichier")
    override val params = listOf(ParamSpec("Name", t("Name", "Nom"), ParamType.Text()))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("Name").trim()
        if (name.isEmpty()) throw ActionFailed(t("Type a name", "Indiquez un nom"))
        val item = ctx.input
        val mime = when (item) {
            is RunValue.Image -> item.mime
            is RunValue.File -> item.mime
            else -> mimeFor(name, "text/plain")
        }
        return RunValue.File(name, mime, bytesOf(ctx, item))
    }
}

@AutoService(ActionDefinition::class)
class GetNameAction : ActionDefinition() {
    override val id = "is.workflow.actions.getitemname"
    override val category = Category.Files
    override val title = t("Get name", "Obtenir le nom")
    override val iconKey = "text"
    override val colorKey = "slate"
    override val input = InputSpec(t("Item", "Élément"))
    override val produces = ContentType.Text
    override val outputLabel = t("Name", "Nom")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Text((ctx.input as? RunValue.File)?.name ?: ctx.input.display())
}

@AutoService(ActionDefinition::class)
class PickFileAction : ActionDefinition() {
    override val id = "is.workflow.actions.documentpicker.open"
    override val category = Category.Files
    override val title = t("Choose file", "Choisir un fichier")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val produces = ContentType.File
    override val outputLabel = t("Chosen File", "Fichier choisi")
    override val keywords = listOf("open", "ouvrir", "document", "pick")
    override val params = listOf(
        ParamSpec(
            "Kind", t("Kind", "Type"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("any", t("Any file", "N'importe quel fichier")),
                    ParamType.Option("text", t("Text", "Texte")),
                    ParamType.Option("image", t("Image", "Image")),
                    ParamType.Option("pdf", t("PDF", "PDF")),
                    ParamType.Option("audio", t("Audio", "Audio")),
                    ParamType.Option("video", t("Video", "Vidéo")),
                ),
            ),
            default = ParamValue.Choice("any"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val mimes = when (ctx.choice("Kind")) {
            "text" -> listOf("text/*", "application/json")
            "image" -> listOf("image/*")
            "pdf" -> listOf("application/pdf")
            "audio" -> listOf("audio/*")
            "video" -> listOf("video/*")
            else -> listOf("*/*")
        }
        val picked = ctx.ui.pickFile(mimes) ?: throw StopShortcut()
        return RunValue.File(picked.name, picked.mime, uri = picked.uri)
    }
}

@AutoService(ActionDefinition::class)
class SaveFileAction : ActionDefinition() {
    override val id = "is.workflow.actions.documentpicker.save"
    override val category = Category.Files
    override val title = t("Save file", "Enregistrer un fichier")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val input = InputSpec(t("Content", "Contenu"))
    override val produces = ContentType.File
    override val outputLabel = t("Saved File", "Fichier enregistré")
    override val keywords = listOf("write", "écrire", "export", "download", "télécharger")
    override val params = listOf(
        ParamSpec(
            "FileName", t("File name", "Nom du fichier"), ParamType.Text(),
            help = t(
                "Left empty, the name of the item is used (see Set name). Android asks where to save it.",
                "Laissé vide, le nom de l'élément est utilisé (voir Définir le nom). Android demande où l'enregistrer.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val item = ctx.input
        val bytes = bytesOf(ctx, item)
        val mime = when (item) {
            is RunValue.File -> item.mime
            is RunValue.Image -> item.mime
            else -> "text/plain"
        }
        val name = ctx.text("FileName").trim().ifEmpty { (item as? RunValue.File)?.name ?: "file." + extensionFor(mime) }
        val target = ctx.ui.saveFile(name, mimeFor(name, mime)) ?: throw StopShortcut()
        ctx.device.writeUri(target.uri, bytes)
        return RunValue.File(target.name, target.mime, uri = target.uri)
    }
}

@AutoService(ActionDefinition::class)
class ReadFileAction : ActionDefinition() {
    override val id = "app.daisychain.file.read"
    override val category = Category.Files
    override val title = t("Read file", "Lire un fichier")
    override val iconKey = "text"
    override val colorKey = "slate"
    override val input = InputSpec(t("File", "Fichier"))
    override val produces = ContentType.Any
    override val outputLabel = t("File Content", "Contenu du fichier")
    override val keywords = listOf("open", "content", "contenu", "text", "texte")

    /** A text file gives its text, an image file gives the image. */
    override suspend fun run(ctx: ActionRunContext): RunValue {
        val file = ctx.input as? RunValue.File ?: return ctx.input
        val bytes = bytesOf(ctx, file)
        return when {
            file.mime.startsWith("image/") -> RunValue.Image(bytes, file.mime)
            isText(file.mime) -> RunValue.Text(String(bytes, Charsets.UTF_8))
            else -> throw ActionFailed(t("This kind of file cannot be read as text", "Ce type de fichier ne peut pas être lu comme du texte"))
        }
    }
}

@AutoService(ActionDefinition::class)
class FileDetailsAction : ActionDefinition() {
    override val id = "is.workflow.actions.properties.files"
    override val category = Category.Files
    override val title = t("Get file details", "Obtenir les détails d'un fichier")
    override val iconKey = "text"
    override val colorKey = "slate"
    override val input = InputSpec(t("File", "Fichier"))
    override val produces = ContentType.Any
    override val outputLabel = t("File Detail", "Détail du fichier")
    override val params = listOf(
        ParamSpec(
            "Detail", t("Detail", "Détail"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("name", t("Name", "Nom")),
                    ParamType.Option("extension", t("Extension", "Extension")),
                    ParamType.Option("type", t("Type", "Type")),
                    ParamType.Option("size", t("Size (bytes)", "Taille (octets)")),
                ),
            ),
            default = ParamValue.Choice("name"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val file = ctx.input as? RunValue.File ?: throw ActionFailed(t("The input is not a file", "L'entrée n'est pas un fichier"))
        return when (ctx.choice("Detail")) {
            "extension" -> RunValue.Text(file.name.substringAfterLast('.', ""))
            "type" -> RunValue.Text(file.mime)
            "size" -> RunValue.Number(bytesOf(ctx, file).size.toDouble())
            else -> RunValue.Text(file.name)
        }
    }
}

// ---- the app's own temporary folder: no permission, no picker

private val tempFileName = ParamSpec("Name", t("File name", "Nom du fichier"), ParamType.Text())

@AutoService(ActionDefinition::class)
class TempFileWriteAction : ActionDefinition() {
    override val id = "app.daisychain.tempfile.write"
    override val category = Category.Files
    override val title = t("Write to temporary file", "Écrire dans un fichier temporaire")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val input = InputSpec(
        t("Content", "Contenu"),
        t("What to write. The temporary folder belongs to Daisychain and can be cleared by Android when space is short.", "Ce qu'il faut écrire. Le dossier temporaire appartient à Daisychain et Android peut le vider quand la place manque."),
    )
    override val keywords = listOf("cache", "scratch", "brouillon", "append", "ajouter")
    override val params = listOf(
        tempFileName,
        ParamSpec(
            "Mode", t("Mode", "Mode"),
            ParamType.Choice(listOf(ParamType.Option("overwrite", t("Replace", "Remplacer")), ParamType.Option("append", t("Add at the end", "Ajouter à la fin")))),
            default = ParamValue.Choice("overwrite"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("Name").trim()
        if (name.isEmpty()) throw ActionFailed(t("Type a file name", "Indiquez un nom de fichier"))
        ctx.device.tempWrite(name, bytesOf(ctx, ctx.input), append = ctx.choice("Mode") == "append")
        return RunValue.Nothing
    }
}

@AutoService(ActionDefinition::class)
class TempFileReadAction : ActionDefinition() {
    override val id = "app.daisychain.tempfile.read"
    override val category = Category.Files
    override val title = t("Read temporary file", "Lire un fichier temporaire")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val produces = ContentType.File
    override val outputLabel = t("Temporary File", "Fichier temporaire")
    override val params = listOf(tempFileName)

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val name = ctx.text("Name").trim()
        val bytes = ctx.device.tempRead(name) ?: throw ActionFailed(t("No temporary file named $name", "Aucun fichier temporaire nommé $name"))
        return RunValue.File(name, mimeFor(name, "text/plain"), bytes)
    }
}

@AutoService(ActionDefinition::class)
class TempFilesListAction : ActionDefinition() {
    override val id = "app.daisychain.tempfile.list"
    override val category = Category.Files
    override val title = t("List temporary files", "Lister les fichiers temporaires")
    override val iconKey = "list"
    override val colorKey = "slate"
    override val produces = ContentType.List
    override val outputLabel = t("Temporary Files", "Fichiers temporaires")

    override suspend fun run(ctx: ActionRunContext): RunValue = RunValue.Items(ctx.device.tempList().map { RunValue.Text(it) })
}

@AutoService(ActionDefinition::class)
class TempFileDeleteAction : ActionDefinition() {
    override val id = "app.daisychain.tempfile.delete"
    override val category = Category.Files
    override val title = t("Delete temporary file", "Supprimer un fichier temporaire")
    override val iconKey = "stop"
    override val colorKey = "rose"
    override val params = listOf(tempFileName)

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.tempDelete(ctx.text("Name").trim())
        return RunValue.Nothing
    }
}

// ---- archives

@AutoService(ActionDefinition::class)
class MakeArchiveAction : ActionDefinition() {
    override val id = "is.workflow.actions.makezip"
    override val category = Category.Files
    override val title = t("Make archive", "Créer une archive")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val input = InputSpec(t("Files", "Fichiers"))
    override val produces = ContentType.File
    override val outputLabel = t("Archive", "Archive")
    override val keywords = listOf("zip", "compress", "compresser")
    override val params = listOf(ParamSpec("ArchiveName", t("Archive name", "Nom de l'archive"), ParamType.Text(), default = ParamValue.Text(com.daisychain.engine.model.TokenString.of("archive.zip"))))

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val items = when (val v = ctx.input) {
            is RunValue.Items -> v.values
            RunValue.Nothing -> emptyList()
            else -> listOf(v)
        }
        if (items.isEmpty()) throw ActionFailed(t("There is nothing to put in the archive", "Il n'y a rien à mettre dans l'archive"))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val used = mutableSetOf<String>()
            items.forEachIndexed { index, item ->
                var name = (item as? RunValue.File)?.name ?: "item-${index + 1}." + extensionFor((item as? RunValue.Image)?.mime ?: "text/plain")
                while (!used.add(name)) name = "${index + 1}-$name"
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytesOf(ctx, item))
                zip.closeEntry()
            }
        }
        val name = ctx.text("ArchiveName").trim().ifEmpty { "archive.zip" }
        return RunValue.File(name, "application/zip", out.toByteArray())
    }
}

@AutoService(ActionDefinition::class)
class ExtractArchiveAction : ActionDefinition() {
    override val id = "is.workflow.actions.unzip"
    override val category = Category.Files
    override val title = t("Extract archive", "Extraire une archive")
    override val iconKey = "save"
    override val colorKey = "slate"
    override val input = InputSpec(t("Archive", "Archive"))
    override val produces = ContentType.List
    override val outputLabel = t("Extracted Files", "Fichiers extraits")
    override val keywords = listOf("unzip", "décompresser")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val archive = bytesOf(ctx, ctx.input)
        val files = mutableListOf<RunValue>()
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val bytes = zip.readBytes()
                    total += bytes.size
                    // Guard against "zip bombs": an archive that unpacks to something enormous.
                    if (total > 200L * 1024 * 1024) throw ActionFailed(t("The archive is too large", "L'archive est trop volumineuse"))
                    val name = entry.name.substringAfterLast('/')
                    files += RunValue.File(name, mimeFor(name), bytes)
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw ActionFailed(t("This is not a valid archive", "Ce n'est pas une archive valide"))
        }
        return RunValue.Items(files)
    }
}
