package com.daisychain.engine.apple

import com.daisychain.engine.model.Accessor
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.FlowMembership
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.t
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.RepeatableParams
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Why a file could not be imported. */
class ImportError(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** An Apple-signed shortcut (iOS 15 and later share them as "Apple Encrypted Archives"). */
        Signed,
        NotAShortcut,
    }
}

class ImportedShortcut(
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val actions: List<ActionInstance>,
    /** Things that could not be carried over, in plain words. */
    val warnings: List<LocalizedText>,
)

class ExportedShortcut(val bytes: ByteArray, val warnings: List<LocalizedText>)

/**
 * Reads and writes iOS/macOS `.shortcut` files (property lists). Our parameter keys are the Apple ones, so most blocks
 * pass straight through; the rest is translated here. Anything not understood is kept untouched, so it survives a round trip.
 */
object ShortcutFile {
    private const val FFFC = '￼'

    private val palette = mapOf(
        "teal" to 0x0B6B5D, "indigo" to 0x4A4DB5, "amber" to 0xA8560C, "slate" to 0x3E4A52,
        "blue" to 0x1F5FA8, "plum" to 0x8B3A82, "rose" to 0xB3263E, "green" to 0x3F7D20,
    )

    private fun nearestColor(rgba: Long): String {
        val r = (rgba shr 24 and 0xFF).toInt()
        val g = (rgba shr 16 and 0xFF).toInt()
        val b = (rgba shr 8 and 0xFF).toInt()
        return palette.minByOrNull { (_, hex) ->
            val dr = r - (hex shr 16 and 0xFF)
            val dg = g - (hex shr 8 and 0xFF)
            val db = b - (hex and 0xFF)
            dr * dr + dg * dg + db * db
        }!!.key
    }

    // ================================================================== import

    fun import(bytes: ByteArray, fileName: String): ImportedShortcut {
        if (bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "AEA1") {
            throw ImportError(ImportError.Reason.Signed, "Signed shortcut")
        }
        val root = try {
            Plist.parse(bytes)
        } catch (e: PlistException) {
            throw ImportError(ImportError.Reason.NotAShortcut, e.message ?: "Not a shortcut")
        }
        val actionsRaw = (root as? Map<*, *>)?.get("WFWorkflowActions") as? List<*>
            ?: throw ImportError(ImportError.Reason.NotAShortcut, "No actions in this file")

        val warnings = mutableListOf<LocalizedText>()
        val actions = actionsRaw.mapNotNull { (it as? Map<*, *>)?.let { m -> importAction(m, warnings) } }

        val icon = (root as Map<*, *>)["WFWorkflowIcon"] as? Map<*, *>
        val color = (icon?.get("WFWorkflowIconStartColor") as? Number)?.toLong()?.let(::nearestColor) ?: "teal"
        val name = fileName.substringAfterLast('/').removeSuffix(".shortcut").trim().ifEmpty { "Shortcut" }
        return ImportedShortcut(name, "bolt", color, actions, warnings.distinct())
    }

    private fun importAction(map: Map<*, *>, warnings: MutableList<LocalizedText>): ActionInstance? {
        val appleId = map["WFWorkflowActionIdentifier"] as? String ?: return null
        val raw = (map["WFWorkflowActionParameters"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
        val (id, params) = translate(appleId, raw)

        val uuid = params["UUID"] as? String ?: UUID.randomUUID().toString()
        val outputName = (params["CustomOutputName"] as? String)?.ifBlank { null }
        val def = ActionRegistry.find(id)
        if (def == null) {
            warnings += t("« ${friendlyName(appleId)} » does not exist in Daisychain yet (it is kept, in red)", "« ${friendlyName(appleId)} » n'existe pas encore dans Daisychain (il est conservé, en rouge)")
            return ActionInstance(uuid, appleId, rawParams(params), null, outputName)
        }

        val membership = flowMembership(def, params)
        val role = membership?.role ?: 0
        val specs = if (role == 0) def.displayParams else def.flow?.roles?.getOrNull(role)?.params.orEmpty()
        val converted = LinkedHashMap<String, ParamValue>()
        val skip = setOf("UUID", "GroupingIdentifier", "WFControlFlowMode", "CustomOutputName", "WFMenuItems")
        for ((key, value) in params) {
            if (key in skip) continue
            val spec = specs.firstOrNull { it.key == key }
            val v = if (spec != null) convert(spec, value) else if (key == def.repeatable?.storageKey) value as? ParamValue else null
            when {
                v != null -> converted[key] = v
                value is ParamValue -> converted[key] = value
                spec == null -> {
                    converted[key] = ParamValue.Raw(toJson(value))
                    warnings += t("« ${def.title.resolve("en")} »: the setting “$key” is kept but not used", "« ${def.title.resolve("fr")} » : le réglage « $key » est conservé mais pas utilisé")
                }
                else -> warnings += t("« ${def.title.resolve("en")} »: the setting “$key” could not be converted", "« ${def.title.resolve("fr")} » : le réglage « $key » n'a pas pu être converti")
            }
        }
        return ActionInstance(uuid, id, converted, membership, outputName)
    }

    private fun flowMembership(def: ActionDefinition, params: Map<String, Any?>): FlowMembership? {
        val spec = def.flow ?: return null
        val group = params["GroupingIdentifier"] as? String ?: return null
        val mode = (params["WFControlFlowMode"] as? Number)?.toInt() ?: return null
        val last = spec.roles.size - 1
        val role = when (mode) {
            0 -> 0
            2 -> last
            else -> spec.roles.indexOfFirst { it.repeatable }.takeIf { it >= 0 } ?: 1.coerceAtMost(last - 1)
        }
        return FlowMembership(group, role)
    }

    private fun friendlyName(identifier: String) = identifier.removePrefix("is.workflow.actions.").replace('.', ' ')

    /** Apple's actions that Daisychain folds into other blocks, and parameter forms that differ. */
    private fun translate(id: String, raw: Map<String, Any?>): Pair<String, Map<String, Any?>> {
        fun with(newId: String, vararg extra: Pair<String, Any?>) = newId to (raw + extra)
        return when (id) {
            "is.workflow.actions.count" -> with(
                "is.workflow.actions.math",
                "WFMathOperation" to when (raw["WFCountType"]) {
                    "Characters" -> "chars"
                    "Words" -> "words"
                    "Lines" -> "lines"
                    else -> "count"
                },
            )
            "is.workflow.actions.round" -> with(
                "is.workflow.actions.math",
                "WFMathOperation" to when (raw["WFRoundMode"]) {
                    "Always Round Up" -> "ceil"
                    "Always Round Down" -> "floor"
                    else -> "round"
                },
            )
            "is.workflow.actions.statistics" -> {
                val op = when (raw["WFStatisticsOperation"]) {
                    "Average" -> "average"
                    "Minimum" -> "min"
                    "Maximum" -> "max"
                    "Sum" -> "sum"
                    "Median" -> "median"
                    else -> null
                }
                if (op != null) with("is.workflow.actions.math", "WFMathOperation" to op) else id to raw
            }
            "is.workflow.actions.getvariable" -> {
                val name = attachmentOf(raw["WFVariable"])?.let { (it.source as? VariableSource.Named)?.name }
                if (name != null) id to (raw + ("WFVariable" to name)) else id to raw
            }
            "is.workflow.actions.conditional" -> id to normalizeCondition(raw)
            "is.workflow.actions.dictionary" -> id to dictionaryRows(raw)
            else -> id to raw
        }
    }

    private val conditionIds = mapOf(
        "Equals" to "4", "Is" to "4", "Is Not" to "5", "Not Equals" to "5", "Contains" to "99", "Does Not Contain" to "999",
        "Is Greater Than" to "2", "Is Greater Than Or Equal To" to "3", "Is Less Than" to "0", "Is Less Than Or Equal To" to "1",
        "Begins With" to "8", "Ends With" to "9", "Has Any Value" to "100", "Does Not Have Any Value" to "101",
    )

    private fun normalizeCondition(raw: Map<String, Any?>): Map<String, Any?> {
        val condition = raw["WFCondition"]
        val id = when (condition) {
            is Number -> condition.toInt().toString()
            is String -> conditionIds[condition] ?: condition
            else -> return raw
        }
        return raw + ("WFCondition" to id)
    }

    /** Apple's dictionary editor: a list of key/value items. Ours: a first key/value plus extra rows. */
    private fun dictionaryRows(raw: Map<String, Any?>): Map<String, Any?> {
        val items = (((raw["WFItems"] as? Map<*, *>)?.get("Value") as? Map<*, *>)?.get("WFDictionaryFieldValueItems") as? List<*>) ?: return raw
        fun entryOf(item: Any?): Pair<ParamValue, ParamValue>? {
            val m = item as? Map<*, *> ?: return null
            val key = paramText(m["WFKey"]) ?: return null
            val value = (m["WFValue"] as? Map<*, *>)?.get("Value").let { paramText(it) ?: paramNumberAsText(it) } ?: ParamValue.Text(TokenString.of(""))
            return key to value
        }
        val entries = items.mapNotNull(::entryOf)
        if (entries.isEmpty()) return raw - "WFItems"
        val extra = entries.drop(1).map { (k, v) -> ParamValue.Dict(mapOf("Key" to k, "Value" to v)) }
        val result = LinkedHashMap(raw - "WFItems")
        result["Key"] = entries[0].first
        result["Value"] = entries[0].second
        if (extra.isNotEmpty()) result["Items"] = ParamValue.Array(extra)
        return result
    }

    private fun paramNumberAsText(v: Any?): ParamValue? = (v as? Number)?.let { ParamValue.Text(TokenString.of(if (it.toDouble() % 1.0 == 0.0) it.toLong().toString() else it.toString())) }

    private fun paramText(v: Any?): ParamValue? = when (v) {
        is String -> ParamValue.Text(TokenString.of(v))
        is Map<*, *> -> convertText(v)
        else -> null
    }

    // ---- values

    private fun convert(spec: ParamSpec, v: Any?): ParamValue? = when (spec.type) {
        is ParamType.Choice -> when (v) {
            is String -> ParamValue.Choice(v)
            is Number -> ParamValue.Choice(v.toInt().toString())
            else -> null
        }
        is ParamType.Number, is ParamType.Slider -> when (v) {
            is Number -> ParamValue.Number(v.toDouble())
            is String -> v.trim().replace(',', '.').toDoubleOrNull()?.let { ParamValue.Number(it) }
            is Map<*, *> -> convertText(v)?.let { text ->
                (text as? ParamValue.Text)?.value?.parts?.singleOrNull()?.let { part ->
                    when (part) {
                        is TokenString.Part.Variable -> ParamValue.Variable(part.ref)
                        is TokenString.Part.Literal -> part.text.trim().replace(',', '.').toDoubleOrNull()?.let { ParamValue.Number(it) }
                    }
                } ?: text.takeIf { it is ParamValue.Variable }
            }
            else -> null
        }
        ParamType.Toggle -> when (v) {
            is Boolean -> ParamValue.Bool(v)
            is Number -> ParamValue.Bool(v.toInt() != 0)
            else -> null
        }
        ParamType.Input -> (v as? Map<*, *>)?.let { it["Variable"] as? Map<*, *> ?: it }?.let { convertText(it) }?.let { p ->
            when (p) {
                is ParamValue.Variable -> p
                is ParamValue.Text -> (p.value.parts.singleOrNull() as? TokenString.Part.Variable)?.let { ParamValue.Variable(it.ref) }
                else -> null
            }
        }
        else -> when (v) {
            is String -> ParamValue.Text(TokenString.of(v))
            is Number -> ParamValue.Text(TokenString.of(if (v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()))
            is Map<*, *> -> convertText(v)
            else -> null
        }
    }

    /** A "token string" (text with variables inside) or a single variable, as Apple stores them. */
    private fun convertText(m: Map<*, *>): ParamValue? = when (m["WFSerializationType"]) {
        "WFTextTokenAttachment" -> attachmentOf(m)?.let { ParamValue.Variable(it) }
        "WFTextTokenString" -> {
            val value = m["Value"] as? Map<*, *>
            val string = value?.get("string") as? String ?: ""
            val attachments = (value?.get("attachmentsByRange") as? Map<*, *>).orEmpty()
            val at = attachments.entries.mapNotNull { (k, a) -> rangeStart(k.toString())?.let { start -> start to attachmentOf(a) } }.toMap()
            val parts = mutableListOf<TokenString.Part>()
            val literal = StringBuilder()
            string.forEachIndexed { i, c ->
                val ref = at[i]
                if (c == FFFC && ref != null) {
                    if (literal.isNotEmpty()) { parts += TokenString.Part.Literal(literal.toString()); literal.clear() }
                    parts += TokenString.Part.Variable(ref)
                } else if (c != FFFC) literal.append(c)
            }
            if (literal.isNotEmpty()) parts += TokenString.Part.Literal(literal.toString())
            ParamValue.Text(TokenString(parts))
        }
        else -> null
    }

    private fun rangeStart(key: String): Int? = Regex("\\{\\s*(\\d+)\\s*,").find(key)?.groupValues?.get(1)?.toIntOrNull()

    /** Apple's variable reference (either the wrapper with "Value", or the reference itself). */
    private fun attachmentOf(v: Any?): VariableRef? {
        var m = v as? Map<*, *> ?: return null
        (m["Value"] as? Map<*, *>)?.let { if (it["Type"] != null) m = it }
        val source: VariableSource = when (m["Type"]) {
            "Variable" -> VariableSource.Named(m["VariableName"] as? String ?: return null)
            "ActionOutput" -> VariableSource.ActionOutput(m["OutputUUID"] as? String ?: return null)
            "ExtensionInput" -> VariableSource.ShortcutInput
            "Clipboard" -> VariableSource.Clipboard
            "CurrentDate" -> VariableSource.CurrentDate
            "Ask" -> VariableSource.AskWhenRun
            else -> return null
        }
        val accessors = (m["Aggrandizements"] as? List<*>).orEmpty().mapNotNull { a ->
            (a as? Map<*, *>)?.let { Accessor(it["Type"] as? String ?: "", (it["PropertyName"] ?: it["DictionaryKey"]) as? String) }
        }
        // Names Apple gives to loop variables.
        val named = (source as? VariableSource.Named)?.name
        val mapped = when (named) {
            "Repeat Item" -> VariableSource.RepeatItem
            "Repeat Index" -> VariableSource.RepeatIndex
            else -> source
        }
        return VariableRef(mapped, accessors)
    }

    private fun rawParams(params: Map<String, Any?>): Map<String, ParamValue> =
        params.filterKeys { it !in setOf("UUID", "CustomOutputName") }.mapValues { ParamValue.Raw(toJson(it.value)) }

    // ---- plist <-> JSON (to keep what we do not understand)

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
        is List<*> -> JsonArray(v.map { toJson(it) })
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is ByteArray -> JsonObject(mapOf("\$data" to JsonPrimitive(Base64.getEncoder().encodeToString(v))))
        is java.util.Date -> JsonObject(mapOf("\$date" to JsonPrimitive(v.time)))
        else -> JsonPrimitive(v.toString())
    }

    private fun fromJson(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> when {
            e.size == 1 && "\$data" in e -> Base64.getDecoder().decode((e["\$data"] as JsonPrimitive).content)
            e.size == 1 && "\$date" in e -> java.util.Date((e["\$date"] as JsonPrimitive).content.toLong())
            else -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, v) -> m[k] = fromJson(v) } }
        }
        is JsonArray -> e.map { fromJson(it) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            e.doubleOrNull != null -> e.doubleOrNull
            else -> e.content
        }
    }

    // ================================================================== export

    fun export(colorKey: String, actions: List<ActionInstance>): ExportedShortcut {
        val warnings = mutableListOf<LocalizedText>()
        val byUuid = actions.associateBy { it.uuid }
        val list = actions.map { exportAction(it, actions, byUuid, warnings) }
        val hex = palette[colorKey] ?: palette.getValue("teal")
        val color = ((hex.toLong() shl 8) or 0xFF) and 0xFFFFFFFFL
        val root = linkedMapOf<String, Any?>(
            "WFWorkflowActions" to list,
            "WFWorkflowClientVersion" to "2900.0.1",
            "WFWorkflowMinimumClientVersion" to 900L,
            "WFWorkflowMinimumClientVersionString" to "900",
            "WFWorkflowIcon" to linkedMapOf("WFWorkflowIconStartColor" to color, "WFWorkflowIconGlyphNumber" to 59511L),
            "WFWorkflowImportQuestions" to emptyList<Any?>(),
            "WFWorkflowInputContentItemClasses" to emptyList<Any?>(),
            "WFWorkflowTypes" to listOf("NCWidget", "WatchKit"),
        )
        return ExportedShortcut(Plist.toXml(root), warnings.distinct())
    }

    private fun exportAction(
        action: ActionInstance,
        all: List<ActionInstance>,
        byUuid: Map<String, ActionInstance>,
        warnings: MutableList<LocalizedText>,
    ): Map<String, Any?> {
        val def = ActionRegistry.find(action.identifier)
        var id = action.identifier
        val params = LinkedHashMap<String, Any?>()
        val specs = def?.let { d ->
            val role = action.flow?.role ?: 0
            if (role == 0) d.displayParams else d.flow?.roles?.getOrNull(role)?.params.orEmpty()
        }.orEmpty()

        for ((key, value) in action.params) {
            val spec = specs.firstOrNull { it.key == key }
            when {
                value is ParamValue.Raw -> params[key] = fromJson(value.data)
                key == def?.repeatable?.storageKey -> Unit // handled with the rows below
                else -> params[key] = exportValue(spec, value, byUuid, warnings)
            }
        }
        if (def == null) {
            warnings += t("« ${action.identifier} » is not a known block: it is exported as it is", "« ${action.identifier} » n'est pas un bloc connu : il est exporté tel quel")
        } else {
            if (!id.startsWith("is.workflow.actions.")) {
                warnings += t("« ${def.title.resolve("en")} » only exists in Daisychain: Apple Shortcuts will not know it", "« ${def.title.resolve("fr")} » n'existe que dans Daisychain : Raccourcis d'Apple ne le connaîtra pas")
            }
            when (id) {
                "is.workflow.actions.math" -> id = exportMath(params, warnings)
                "is.workflow.actions.dictionary" -> exportDictionary(action, params)
                "is.workflow.actions.getvariable" -> (params["WFVariable"] as? String)?.let {
                    params["WFVariable"] = linkedMapOf("Value" to linkedMapOf("Type" to "Variable", "VariableName" to it), "WFSerializationType" to "WFTextTokenAttachment")
                }
            }
            if (def.repeatable != null && id == "is.workflow.actions.conditional" && ActionRegistryRows.rows(action).isNotEmpty()) {
                warnings += t("« If » with several conditions: only the first one is exported", "« Si » avec plusieurs conditions : seule la première est exportée")
            }
            if (def.id == "is.workflow.actions.choosefrommenu" && action.flow?.role == 0) {
                params["WFMenuItems"] = menuItems(action, all)
            }
        }
        params["UUID"] = action.uuid
        action.outputName?.let { params["CustomOutputName"] = it }
        action.flow?.let { f ->
            val roles = def?.flow?.roles?.size ?: 3
            params["GroupingIdentifier"] = f.groupId
            params["WFControlFlowMode"] = when (f.role) {
                0 -> 0L
                roles - 1 -> 2L
                else -> 1L
            }
        }
        return linkedMapOf("WFWorkflowActionIdentifier" to id, "WFWorkflowActionParameters" to params)
    }

    private object ActionRegistryRows {
        fun rows(action: ActionInstance): List<Any?> = (action.params["WFExtraConditions"] as? ParamValue.Array)?.items.orEmpty()
    }

    private fun menuItems(start: ActionInstance, all: List<ActionInstance>): List<Any?> = all
        .filter { it.flow?.groupId == start.flow?.groupId && it.flow?.role == 1 }
        .map { item ->
            (item.params["WFMenuItemTitle"] as? ParamValue.Text)?.value?.parts?.joinToString("") { (it as? TokenString.Part.Literal)?.text.orEmpty() }.orEmpty()
        }

    private fun exportMath(params: LinkedHashMap<String, Any?>, warnings: MutableList<LocalizedText>): String {
        val op = (params["WFMathOperation"] as? String) ?: return "is.workflow.actions.math"
        fun reuse(newId: String, key: String, value: String): String {
            params.remove("WFMathOperation")
            params.remove("WFMathOperand")
            params.remove("Decimals")
            params[key] = value
            return newId
        }
        return when (op) {
            "count" -> reuse("is.workflow.actions.count", "WFCountType", "Items")
            "chars" -> reuse("is.workflow.actions.count", "WFCountType", "Characters")
            "words" -> reuse("is.workflow.actions.count", "WFCountType", "Words")
            "lines" -> reuse("is.workflow.actions.count", "WFCountType", "Lines")
            "round" -> reuse("is.workflow.actions.round", "WFRoundMode", "Normal")
            "ceil" -> reuse("is.workflow.actions.round", "WFRoundMode", "Always Round Up")
            "floor" -> reuse("is.workflow.actions.round", "WFRoundMode", "Always Round Down")
            "sum" -> reuse("is.workflow.actions.statistics", "WFStatisticsOperation", "Sum")
            "average" -> reuse("is.workflow.actions.statistics", "WFStatisticsOperation", "Average")
            "min" -> reuse("is.workflow.actions.statistics", "WFStatisticsOperation", "Minimum")
            "max" -> reuse("is.workflow.actions.statistics", "WFStatisticsOperation", "Maximum")
            "median" -> reuse("is.workflow.actions.statistics", "WFStatisticsOperation", "Median")
            "+", "-", "×", "÷" -> "is.workflow.actions.math"
            else -> {
                warnings += t("« Calculate » with “$op”: Apple Shortcuts has no such operation", "« Calculer » avec « $op » : Raccourcis d'Apple n'a pas cette opération")
                "is.workflow.actions.math"
            }
        }
    }

    private fun exportDictionary(action: ActionInstance, params: LinkedHashMap<String, Any?>) {
        val rows = mutableListOf<Pair<Any?, Any?>>()
        params.remove("Key")?.let { key -> rows += key to params.remove("Value") }
        ((action.params["Items"] as? ParamValue.Array)?.items.orEmpty()).forEach { item ->
            val dict = (item as? ParamValue.Dict)?.entries ?: return@forEach
            rows += exportValue(null, dict["Key"] ?: ParamValue.Text(TokenString.of("")), emptyMap(), mutableListOf()) to
                exportValue(null, dict["Value"] ?: ParamValue.Text(TokenString.of("")), emptyMap(), mutableListOf())
        }
        params.remove("Items")
        val items = rows.map { (k, v) ->
            linkedMapOf(
                "WFItemType" to 0L,
                "WFKey" to textToken(k),
                "WFValue" to linkedMapOf("Value" to textToken(v), "WFSerializationType" to "WFTextTokenString"),
            )
        }
        params["WFItems"] = linkedMapOf(
            "Value" to linkedMapOf("WFDictionaryFieldValueItems" to items),
            "WFSerializationType" to "WFDictionaryFieldValue",
        )
    }

    private fun textToken(v: Any?): Any? = when (v) {
        is Map<*, *> -> v["Value"]?.let { inner -> linkedMapOf("Value" to inner, "WFSerializationType" to (v["WFSerializationType"] ?: "WFTextTokenString")) } ?: v
        is String -> linkedMapOf("Value" to linkedMapOf("string" to v), "WFSerializationType" to "WFTextTokenString")
        else -> v
    }

    private fun exportValue(spec: ParamSpec?, v: ParamValue, byUuid: Map<String, ActionInstance>, warnings: MutableList<LocalizedText>): Any? = when (v) {
        is ParamValue.Text -> exportTokens(v.value, byUuid, warnings)
        is ParamValue.Number -> if (v.value % 1.0 == 0.0 && Math.abs(v.value) < 1e15) v.value.toLong() else v.value
        is ParamValue.Bool -> v.value
        is ParamValue.Choice -> if (spec?.key == "WFCondition") v.optionId.toLongOrNull() ?: v.optionId else v.optionId
        is ParamValue.Variable -> exportAttachment(v.ref, byUuid, warnings)?.let {
            linkedMapOf("Value" to it, "WFSerializationType" to "WFTextTokenAttachment")
        }
        is ParamValue.Dict -> v.entries.mapValues { exportValue(null, it.value, byUuid, warnings) }
        is ParamValue.Array -> v.items.map { exportValue(null, it, byUuid, warnings) }
        is ParamValue.Raw -> fromJson(v.data)
    }

    private fun exportTokens(t: TokenString, byUuid: Map<String, ActionInstance>, warnings: MutableList<LocalizedText>): Any? {
        if (t.parts.all { it is TokenString.Part.Literal }) return t.parts.joinToString("") { (it as TokenString.Part.Literal).text }
        val text = StringBuilder()
        val attachments = LinkedHashMap<String, Any?>()
        for (part in t.parts) {
            when (part) {
                is TokenString.Part.Literal -> text.append(part.text)
                is TokenString.Part.Variable -> {
                    val attachment = exportAttachment(part.ref, byUuid, warnings) ?: continue
                    attachments["{${text.length}, 1}"] = attachment
                    text.append(FFFC)
                }
            }
        }
        return linkedMapOf(
            "Value" to linkedMapOf("string" to text.toString(), "attachmentsByRange" to attachments),
            "WFSerializationType" to "WFTextTokenString",
        )
    }

    private fun exportAttachment(ref: VariableRef, byUuid: Map<String, ActionInstance>, warnings: MutableList<LocalizedText>): Map<String, Any?>? {
        val base: LinkedHashMap<String, Any?> = when (val s = ref.source) {
            is VariableSource.Named -> linkedMapOf("Type" to "Variable", "VariableName" to s.name)
            is VariableSource.ActionOutput -> linkedMapOf(
                "Type" to "ActionOutput",
                "OutputUUID" to s.actionUuid,
                "OutputName" to (byUuid[s.actionUuid]?.let { a ->
                    a.outputName ?: ActionRegistry.find(a.identifier)?.let { (it.outputLabel ?: it.title).resolve("en") }
                } ?: "Output"),
            )
            VariableSource.ShortcutInput -> linkedMapOf("Type" to "ExtensionInput")
            VariableSource.Clipboard -> linkedMapOf("Type" to "Clipboard")
            VariableSource.CurrentDate -> linkedMapOf("Type" to "CurrentDate")
            VariableSource.AskWhenRun -> linkedMapOf("Type" to "Ask")
            VariableSource.RepeatItem -> linkedMapOf("Type" to "Variable", "VariableName" to "Repeat Item")
            VariableSource.RepeatIndex -> linkedMapOf("Type" to "Variable", "VariableName" to "Repeat Index")
            VariableSource.Empty, is VariableSource.Device -> {
                warnings += t("A variable (“Empty” or a device detail) has no Apple equivalent and was left out", "Une variable (« Vide » ou un détail de l'appareil) n'a pas d'équivalent chez Apple et a été retirée")
                return null
            }
        }
        if (ref.path.isNotEmpty()) {
            base["Aggrandizements"] = ref.path.map { a ->
                linkedMapOf<String, Any?>("Type" to a.kind).also { m ->
                    a.argument?.let { m[if (a.kind.contains("Dictionary")) "DictionaryKey" else "PropertyName"] = it }
                }
            }
        }
        return base
    }
}
