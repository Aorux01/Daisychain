package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.ImageEdit
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.StopShortcut
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.ShowWhen
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService

private val notAnImage = t("The input is not an image", "L'entrée n'est pas une image")

private suspend fun imageOf(ctx: ActionRunContext): RunValue.Image = when (val v = ctx.input) {
    is RunValue.Image -> v
    is RunValue.File -> if (v.mime.startsWith("image/")) RunValue.Image(bytesOf(ctx, v), v.mime) else throw ActionFailed(notAnImage)
    else -> throw ActionFailed(notAnImage)
}

private fun edited(ctx: ActionRunContext, image: RunValue.Image, edit: ImageEdit): RunValue {
    val result = ctx.device.editImage(image.bytes, edit)
    return RunValue.Image(result.bytes, result.mime)
}

@AutoService(ActionDefinition::class)
class TakePhotoAction : ActionDefinition() {
    override val id = "is.workflow.actions.takephoto"
    override val category = Category.Media
    override val title = t("Take photo", "Prendre une photo")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val produces = ContentType.Image
    override val outputLabel = t("Photo", "Photo")
    override val keywords = listOf("camera", "appareil photo", "picture", "selfie")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val taken = ctx.ui.takePhoto() ?: throw StopShortcut()
        return RunValue.Image(ctx.device.readUri(taken.uri), taken.mime)
    }
}

@AutoService(ActionDefinition::class)
class TakeVideoAction : ActionDefinition() {
    override val id = "is.workflow.actions.takevideo"
    override val category = Category.Media
    override val title = t("Take video", "Prendre une vidéo")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val produces = ContentType.File
    override val outputLabel = t("Video", "Vidéo")
    override val keywords = listOf("camera", "film", "record", "filmer")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val taken = ctx.ui.takeVideo() ?: throw StopShortcut()
        return RunValue.File(taken.name, taken.mime, uri = taken.uri)
    }
}

@AutoService(ActionDefinition::class)
class ChoosePhotoAction : ActionDefinition() {
    override val id = "is.workflow.actions.selectphoto"
    override val category = Category.Media
    override val title = t("Choose photo", "Choisir une photo")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val produces = ContentType.Image
    override val outputLabel = t("Chosen Photo", "Photo choisie")
    override val keywords = listOf("gallery", "galerie", "pick", "image")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val picked = ctx.ui.pickPhoto() ?: throw StopShortcut()
        return RunValue.Image(ctx.device.readUri(picked.uri), picked.mime)
    }
}

@AutoService(ActionDefinition::class)
class LatestPhotosAction : ActionDefinition() {
    override val id = "is.workflow.actions.getlastphoto"
    override val category = Category.Media
    override val title = t("Get latest photos", "Obtenir les dernières photos")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val produces = ContentType.List
    override val outputLabel = t("Latest Photos", "Dernières photos")
    override val requirements = setOf(Requirement.Photos)
    override val params = listOf(
        ParamSpec("Count", t("How many", "Combien"), ParamType.Number(min = 1.0, max = 10.0), default = ParamValue.Number(1.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val count = (ctx.number("Count") ?: 1.0).toInt().coerceIn(1, 10)
        val photos = ctx.device.latestPhotos(count).map { RunValue.Image(ctx.device.readUri(it.uri), it.mime) }
        return if (count == 1 && photos.size == 1) photos[0] else RunValue.Items(photos)
    }
}

@AutoService(ActionDefinition::class)
class RecordAudioAction : ActionDefinition() {
    override val id = "is.workflow.actions.recordaudio"
    override val category = Category.Media
    override val title = t("Record audio", "Enregistrer un son")
    override val iconKey = "music"
    override val colorKey = "rose"
    override val produces = ContentType.File
    override val outputLabel = t("Recording", "Enregistrement")
    override val requirements = setOf(Requirement.Microphone)
    override val keywords = listOf("microphone", "voice", "voix", "sound")

    override suspend fun run(ctx: ActionRunContext): RunValue {
        ctx.device.startRecording()
        val bytes = try {
            ctx.ui.waitWhileRecording()
            ctx.device.stopRecording()
        } catch (e: Exception) {
            runCatching { ctx.device.stopRecording() }
            throw e
        }
        return RunValue.File("recording-${System.currentTimeMillis()}.m4a", "audio/mp4", bytes)
    }
}

@AutoService(ActionDefinition::class)
class ResizeImageAction : ActionDefinition() {
    override val id = "is.workflow.actions.image.resize"
    override val category = Category.Media
    override val title = t("Resize image", "Redimensionner l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Image
    override val outputLabel = t("Resized Image", "Image redimensionnée")
    override val params = listOf(
        ParamSpec(
            "Width", t("Width (px)", "Largeur (px)"), ParamType.Number(min = 1.0), default = ParamValue.Number(1024.0),
            help = t("The height follows to keep the proportions, unless you also set a height below.", "La hauteur suit pour garder les proportions, sauf si vous donnez aussi une hauteur ci-dessous."),
        ),
        ParamSpec("Height", t("Height (px)", "Hauteur (px)"), ParamType.Number(min = 1.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = imageOf(ctx)
        val values = buildMap {
            ctx.number("Width")?.let { put("width", it) }
            ctx.number("Height")?.let { put("height", it) }
        }
        return edited(ctx, image, ImageEdit("resize", values, format = formatOf(image)))
    }
}

private fun formatOf(image: RunValue.Image) = if (image.mime == "image/png") "png" else if (image.mime == "image/webp") "webp" else "jpeg"

@AutoService(ActionDefinition::class)
class RotateImageAction : ActionDefinition() {
    override val id = "is.workflow.actions.image.rotate"
    override val category = Category.Media
    override val title = t("Rotate image", "Faire pivoter l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Image
    override val outputLabel = t("Rotated Image", "Image pivotée")
    override val params = listOf(
        ParamSpec("Degrees", t("Degrees", "Degrés"), ParamType.Number(), default = ParamValue.Number(90.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = imageOf(ctx)
        return edited(ctx, image, ImageEdit("rotate", mapOf("degrees" to (ctx.number("Degrees") ?: 90.0)), format = formatOf(image)))
    }
}

@AutoService(ActionDefinition::class)
class CropImageAction : ActionDefinition() {
    override val id = "is.workflow.actions.image.crop"
    override val category = Category.Media
    override val title = t("Crop image", "Recadrer l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Image
    override val outputLabel = t("Cropped Image", "Image recadrée")
    override val params = listOf(
        ParamSpec("X", t("Left (%)", "Gauche (%)"), ParamType.Number(min = 0.0, max = 99.0), default = ParamValue.Number(0.0)),
        ParamSpec("Y", t("Top (%)", "Haut (%)"), ParamType.Number(min = 0.0, max = 99.0), default = ParamValue.Number(0.0)),
        ParamSpec("W", t("Width (%)", "Largeur (%)"), ParamType.Number(min = 1.0, max = 100.0), default = ParamValue.Number(50.0)),
        ParamSpec("H", t("Height (%)", "Hauteur (%)"), ParamType.Number(min = 1.0, max = 100.0), default = ParamValue.Number(50.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = imageOf(ctx)
        val values = mapOf(
            "x" to (ctx.number("X") ?: 0.0) / 100, "y" to (ctx.number("Y") ?: 0.0) / 100,
            "w" to (ctx.number("W") ?: 50.0) / 100, "h" to (ctx.number("H") ?: 50.0) / 100,
        )
        return edited(ctx, image, ImageEdit("crop", values, format = formatOf(image)))
    }
}

@AutoService(ActionDefinition::class)
class ConvertImageAction : ActionDefinition() {
    override val id = "is.workflow.actions.image.convert"
    override val category = Category.Media
    override val title = t("Convert image", "Convertir l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Image
    override val outputLabel = t("Converted Image", "Image convertie")
    override val keywords = listOf("jpeg", "png", "webp", "compress", "compresser", "quality", "qualité")
    override val params = listOf(
        ParamSpec(
            "Format", t("Format", "Format"),
            ParamType.Choice(listOf(ParamType.Option("jpeg", t("JPEG", "JPEG")), ParamType.Option("png", t("PNG", "PNG")), ParamType.Option("webp", t("WebP", "WebP")))),
            default = ParamValue.Choice("jpeg"),
        ),
        ParamSpec(
            "Quality", t("Quality (%)", "Qualité (%)"), ParamType.Slider(10.0, 100.0, "%"), default = ParamValue.Number(85.0),
            showWhen = ShowWhen("Format", setOf("jpeg", "webp")),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue =
        edited(ctx, imageOf(ctx), ImageEdit("convert", mapOf("quality" to (ctx.number("Quality") ?: 85.0)), format = ctx.choice("Format") ?: "jpeg"))
}

@AutoService(ActionDefinition::class)
class OverlayTextAction : ActionDefinition() {
    override val id = "is.workflow.actions.overlaytext"
    override val category = Category.Media
    override val title = t("Overlay text on image", "Écrire un texte sur l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Image
    override val outputLabel = t("Image with Text", "Image avec texte")
    override val keywords = listOf("caption", "légende", "watermark", "filigrane")
    override val params = listOf(
        ParamSpec("Text", t("Text", "Texte"), ParamType.Text()),
        ParamSpec(
            "Position", t("Position", "Position"),
            ParamType.Choice(listOf(ParamType.Option("top", t("Top", "Haut")), ParamType.Option("center", t("Center", "Centre")), ParamType.Option("bottom", t("Bottom", "Bas")))),
            default = ParamValue.Choice("bottom"),
        ),
        ParamSpec("Size", t("Size (% of height)", "Taille (% de la hauteur)"), ParamType.Number(min = 1.0, max = 50.0), default = ParamValue.Number(6.0)),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = imageOf(ctx)
        val position = when (ctx.choice("Position")) {
            "top" -> 0.0
            "center" -> 1.0
            else -> 2.0
        }
        return edited(ctx, image, ImageEdit("text", mapOf("position" to position, "size" to (ctx.number("Size") ?: 6.0)), ctx.text("Text"), formatOf(image)))
    }
}

@AutoService(ActionDefinition::class)
class ImageDetailsAction : ActionDefinition() {
    override val id = "is.workflow.actions.properties.images"
    override val category = Category.Media
    override val title = t("Get image details", "Obtenir les détails de l'image")
    override val iconKey = "camera"
    override val colorKey = "rose"
    override val input = InputSpec(t("Image", "Image"))
    override val produces = ContentType.Any
    override val outputLabel = t("Image Detail", "Détail de l'image")
    override val params = listOf(
        ParamSpec(
            "Detail", t("Detail", "Détail"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("width", t("Width", "Largeur")),
                    ParamType.Option("height", t("Height", "Hauteur")),
                    ParamType.Option("type", t("Type", "Type")),
                    ParamType.Option("size", t("Size (bytes)", "Taille (octets)")),
                ),
            ),
            default = ParamValue.Choice("width"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val image = imageOf(ctx)
        val (w, h) = ctx.device.imageSize(image.bytes)
        return when (ctx.choice("Detail")) {
            "height" -> RunValue.Number(h.toDouble())
            "type" -> RunValue.Text(image.mime)
            "size" -> RunValue.Number(image.bytes.size.toDouble())
            else -> RunValue.Number(w.toDouble())
        }
    }
}
