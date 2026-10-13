package com.daisychain.engine.spec

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.FlowRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.unsupported

/** A text with one value per language; falls back to English. */
class LocalizedText(private val values: Map<String, String>) {
    fun hasLanguage(language: String) = language in values

    fun resolve(language: String): String = values[language]
        ?: Translations.lookup(language, values.getValue("en"))
        ?: values.getValue("en")
}

/** `t("Show result", "Afficher le résultat")` */
fun t(en: String, fr: String = en) = LocalizedText(mapOf("en" to en, "fr" to fr))

/** A text written in all four languages at once (used where the Translations table would be awkward). */
fun t(en: String, fr: String, italian: String, german: String, spanish: String) =
    LocalizedText(mapOf("en" to en, "fr" to fr, "it" to italian, "de" to german, "es" to spanish))

/** A group of blocks in the action picker. Plugins can introduce their own. */
data class Category(val id: String, val title: LocalizedText, val iconKey: String, val colorKey: String) {
    companion object {
        val Scripting = Category("scripting", t("Scripting", "Scripts"), "bolt", "slate")
        val Control = Category("control", t("Control", "Contrôle"), "bolt", "slate")
        val Text = Category("text", t("Text", "Texte"), "mail", "plum")
        val Numbers = Category("numbers", t("Numbers", "Nombres"), "bolt", "teal")
        val Device = Category("device", t("System", "Système"), "bolt", "teal")
        val Apps = Category("apps", t("Apps", "Applications"), "bolt", "indigo")
        val Notifications = Category("notifications", t("Notifications"), "bell", "amber")
        val Sharing = Category("sharing", t("Sharing", "Partage"), "share", "blue")
        val Web = Category("web", t("Web"), "link", "blue")
        val Places = Category("places", t("Places", "Lieux"), "location", "blue")
        val Files = Category("files", t("Files", "Fichiers"), "save", "slate")
        val Media = Category("media", t("Media", "Médias"), "camera", "rose")
    }
}

/** How the user edits one parameter in the block. */
sealed interface ParamType {
    data class Text(val multiline: Boolean = false) : ParamType
    data class Number(val min: Double? = null, val max: Double? = null, val unit: String? = null) : ParamType
    data object Toggle : ParamType
    data class Choice(val options: List<Option>) : ParamType
    data class Slider(val min: Double, val max: Double, val unit: String? = null) : ParamType

    /** Free name, e.g. a variable name; no variable picker. */
    data object Name : ParamType

    /** An installed app, chosen from a list (stored as its package name). */
    data object App : ParamType

    /** The name of a variable: pick one that already exists or type a new one. */
    data object VariableName : ParamType

    /** The block's main input: it must be a variable (e.g. what "If" tests). */
    data object Input : ParamType

    data class Option(val id: String, val label: LocalizedText)
}

data class ParamSpec(
    /** Key in the iOS plist (e.g. `WFTextActionText`) so import/export needs no translation table. */
    val key: String,
    val label: LocalizedText,
    val type: ParamType,
    val default: ParamValue? = null,
    /** Whether a variable can be dropped in instead of a typed value. */
    val acceptsVariables: Boolean = true,
    /** Hides the parameter unless another [ParamType.Choice] parameter has one of the given values. */
    val showWhen: ShowWhen? = null,
    /** Left unset, the previous block's output is used; the card shows that output's name as the default. */
    val fallsBackToInput: Boolean = false,
    /** One sentence shown when editing the parameter. */
    val help: LocalizedText? = null,
    /** With [fallsBackToInput]: "automatic" is the nearest block above that gives this kind of value (e.g. a dictionary). */
    val defaultFromType: ContentType? = null,
    /** Only variables of these kinds are offered in the picker (null = all). */
    val variableTypes: Set<ContentType>? = null,
)

/**
 * The value a block works on. It is shown first on the card; left on "automatic" it is the previous block's output,
 * and it can be pointed at any other variable. Stored under [INPUT_KEY] as a variable.
 */
data class InputSpec(val label: LocalizedText, val help: LocalizedText? = null) {
    fun toParamSpec() = ParamSpec(INPUT_KEY, label, ParamType.Input, fallsBackToInput = true, help = help)

    companion object {
        const val INPUT_KEY = "WFInput"
    }
}

/** Shown only when [paramKey] has one of [optionIds] (and, if given, [also] holds too). */
data class ShowWhen(val paramKey: String, val optionIds: Set<String>, val also: ShowWhen? = null)

/**
 * Control flow: several blocks that belong together (If / Otherwise / End If).
 * Roles are ordered; the first is the start, the last is the end, those in between are repeatable
 * separators (Otherwise, one per menu item, ...).
 */
data class FlowSpec(
    val roles: List<FlowRole>,
    /** Whether blocks can sit directly under the first role (not for a menu: only its choices run). */
    val startHoldsBlocks: Boolean = true,
) {
    init { require(roles.size >= 2) { "A flow needs at least a start and an end" } }
}

/** @property params extra parameters shown next to this role's label (e.g. a menu item's title). */
data class FlowRole(
    val title: LocalizedText,
    val repeatable: Boolean = false,
    val params: List<ParamSpec> = emptyList(),
    /** For a repeatable role: the label of the button that adds one more (e.g. "Add choice"). */
    val addLabel: LocalizedText? = null,
)

/**
 * Lets the user add extra rows of a block's [ActionDefinition.params] (extra conditions of an "If"),
 * each joined to the previous one with a choice such as and / or (no join when [RepeatableParams.joinOptions] is empty).
 * Extra rows are stored as an array of dictionaries under [storageKey]; the join is stored in each row under [JOIN_KEY].
 */
data class RepeatableParams(
    val storageKey: String,
    val addLabel: LocalizedText,
    val removeLabel: LocalizedText,
    val joinOptions: List<ParamType.Option>,
) {
    companion object {
        const val JOIN_KEY = "join"
    }
}

/** What a block needs from the device. Used to warn or ask permission before a run. */
enum class Requirement { Notifications, Location, Contacts, Calendar, Camera, Microphone, SendSms, Photos, WriteSettings, Accessibility, Network, Shizuku }

/**
 * A block ("action") the user can add to a shortcut.
 *
 * ## Adding a block
 * Write a class with a public no-arg constructor, extend this class and annotate it with
 * `@AutoService(ActionDefinition::class)`. It is picked up by [ActionRegistry] automatically.
 */
abstract class ActionDefinition {
    /** Canonical id, the iOS identifier when one exists (`is.workflow.actions.gettext`). */
    abstract val id: String

    /** Other ids that must map to this block when importing (older iOS names, other apps). */
    open val aliases: List<String> = emptyList()

    abstract val category: Category
    abstract val title: LocalizedText
    abstract val iconKey: String
    abstract val colorKey: String

    open val description: LocalizedText? = null
    open val params: List<ParamSpec> = emptyList()

    /** The value this block works on, when it has no parameter of its own for it (see [InputSpec]). */
    open val input: InputSpec? = null

    /** What the card and the parameter dialogs show: the input first, then the parameters. */
    val displayParams: List<ParamSpec> get() = input?.let { listOf(it.toParamSpec()) + params } ?: params

    /** What the block accepts from the previous block; empty = it ignores its input. */
    open val accepts: Set<ContentType> = emptySet()
    open val produces: ContentType? = null

    /** Name of this block's output in the variable picker ("Battery Level"); defaults to [title]. */
    open val outputLabel: LocalizedText? = null
    open val flow: FlowSpec? = null

    /** Extra rows of [params] the user can add (see [RepeatableParams]). */
    open val repeatable: RepeatableParams? = null
    open val requirements: Set<Requirement> = emptySet()

    /** Extra search words (synonyms), per language not needed: matched in any language. */
    open val keywords: List<String> = emptyList()

    /**
     * Runs a plain block and returns its output ([RunValue.Nothing] for none).
     * Blocks that cannot run on Android keep this default, which reports "not supported".
     */
    open suspend fun run(ctx: ActionRunContext): RunValue = unsupported()

    /** Runs a control-flow block (one that has a [flow]): decides which segments run with `ctx.runSegment`. */
    open suspend fun runFlow(ctx: FlowRunContext): Unit = unsupported()
}
