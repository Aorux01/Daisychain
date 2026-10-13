package com.daisychain.engine.plugin

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.HttpRequest
import com.daisychain.engine.run.IntentSpec
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Requirement
import org.json.JSONArray
import org.json.JSONObject

/** One thing a plugin block does when it runs. Texts may hold `{{key}}` (a parameter), `{{input}}` (previous block) and `{{result}}` (previous step). */
sealed interface PluginStep {
    data class Http(val method: String, val url: String, val headers: Map<String, String>, val body: String?) : PluginStep
    data class Intent(val action: String, val data: String, val packageName: String, val mime: String, val extras: Map<String, String>, val broadcast: Boolean) : PluginStep
    data class Text(val template: String) : PluginStep
}

class PluginException(message: String) : Exception(message)

/** A plugin file: a few blocks described in JSON (no code runs: only web requests, intents and text templates). */
class Plugin(val id: String, val name: String, val blocks: List<ActionDefinition>, val needsNetwork: Boolean, val usesIntents: Boolean)

object PluginParser {
    private fun localized(j: Any?, fallback: String): LocalizedText = when (j) {
        is JSONObject -> {
            val map = j.keys().asSequence().associateWith { j.getString(it) }
            LocalizedText(if ("en" in map) map else map + ("en" to (map.values.firstOrNull() ?: fallback)))
        }
        is String -> LocalizedText(mapOf("en" to j))
        else -> LocalizedText(mapOf("en" to fallback))
    }

    private fun strings(j: JSONObject?): Map<String, String> = j?.keys()?.asSequence()?.associateWith { j.getString(it) }.orEmpty()

    fun parse(text: String): Plugin {
        val root = try { JSONObject(text) } catch (e: Exception) { throw PluginException("Not a valid JSON file") }
        val pluginId = root.optString("id").trim()
        if (!Regex("[A-Za-z0-9._-]{3,64}").matches(pluginId)) throw PluginException("\"id\" is missing or invalid (letters, digits, . _ -)")
        val name = root.optString("name", pluginId)
        val category = Category("plugin.$pluginId", localized(root.opt("name"), pluginId), root.optString("icon", "bolt"), root.optString("color", "teal"))
        val array = root.optJSONArray("blocks") ?: throw PluginException("\"blocks\" is missing")
        val blocks = (0 until array.length()).map { block(pluginId, category, array.getJSONObject(it)) }
        if (blocks.isEmpty()) throw PluginException("The plugin has no block")
        val steps = blocks.filterIsInstance<DeclarativeAction>().flatMap { it.steps }
        return Plugin(pluginId, name, blocks, steps.any { it is PluginStep.Http }, steps.any { it is PluginStep.Intent })
    }

    private fun block(pluginId: String, category: Category, j: JSONObject): DeclarativeAction {
        val blockId = j.optString("id").trim()
        if (!Regex("[A-Za-z0-9_-]{1,40}").matches(blockId)) throw PluginException("A block has no valid \"id\"")
        val params = j.optJSONArray("params")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                val key = p.optString("key").ifBlank { throw PluginException("A parameter of \"$blockId\" has no \"key\"") }
                val type = when (p.optString("type", "text")) {
                    "number" -> ParamType.Number()
                    "toggle" -> ParamType.Toggle
                    "textarea" -> ParamType.Text(multiline = true)
                    "choice" -> ParamType.Choice(
                        (p.optJSONArray("options") ?: JSONArray()).let { o -> (0 until o.length()).map { n -> o.getString(n) } }.map { ParamType.Option(it, localized(it, it)) },
                    )
                    else -> ParamType.Text()
                }
                val default: ParamValue? = if (!p.has("default")) null else when (type) {
                    is ParamType.Number -> ParamValue.Number(p.getDouble("default"))
                    ParamType.Toggle -> ParamValue.Bool(p.getBoolean("default"))
                    is ParamType.Choice -> ParamValue.Choice(p.getString("default"))
                    else -> ParamValue.Text(TokenString.of(p.getString("default")))
                }
                ParamSpec(key, localized(p.opt("label"), key), type, default = default, help = p.opt("help")?.let { localized(it, "") })
            }
        }.orEmpty()
        val stepsJson = j.optJSONArray("steps") ?: throw PluginException("Block \"$blockId\" has no \"steps\"")
        val steps = (0 until stepsJson.length()).map { i ->
            val s = stepsJson.getJSONObject(i)
            when (s.optString("type")) {
                "http" -> PluginStep.Http(s.optString("method", "GET").uppercase(), s.optString("url").ifBlank { throw PluginException("An http step of \"$blockId\" has no \"url\"") }, strings(s.optJSONObject("headers")), s.optString("body").takeIf { s.has("body") })
                "intent" -> PluginStep.Intent(
                    s.optString("action").ifBlank { throw PluginException("An intent step of \"$blockId\" has no \"action\"") },
                    s.optString("data"), s.optString("package"), s.optString("mime"), strings(s.optJSONObject("extras")), s.optString("kind") == "broadcast",
                )
                "text" -> PluginStep.Text(s.optString("template"))
                else -> throw PluginException("Unknown step type \"${s.optString("type")}\" in \"$blockId\"")
            }
        }
        if (steps.isEmpty()) throw PluginException("Block \"$blockId\" has no step")
        return DeclarativeAction(
            id = "plugin.$pluginId.$blockId",
            category = category,
            title = localized(j.opt("title"), blockId),
            iconKey = j.optString("icon", "bolt"),
            colorKey = j.optString("color", "teal"),
            params = params,
            steps = steps,
            hasOutput = j.optString("output", "text") != "none",
            description = j.opt("description")?.let { localized(it, "") },
        )
    }
}

/** A block made from a plugin file. */
class DeclarativeAction(
    override val id: String,
    override val category: Category,
    override val title: LocalizedText,
    override val iconKey: String,
    override val colorKey: String,
    override val params: List<ParamSpec>,
    val steps: List<PluginStep>,
    private val hasOutput: Boolean,
    override val description: LocalizedText?,
) : ActionDefinition() {
    override val produces = if (hasOutput) ContentType.Text else null
    override val accepts = setOf(ContentType.Any)
    override val requirements = if (steps.any { it is PluginStep.Http }) setOf(Requirement.Network) else emptySet()

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val values = HashMap<String, String>()
        for (p in params) values[p.key] = ctx.text(p.key)
        values["input"] = ctx.input.display()
        var result = RunValue.Nothing as RunValue
        for (step in steps) {
            values["result"] = result.display()
            result = when (step) {
                is PluginStep.Text -> RunValue.Text(fill(step.template, values))
                is PluginStep.Http -> {
                    val body = step.body?.let { fill(it, values).toByteArray() }
                    val url = fill(step.url, values).trim().let { if (it.contains("://")) it else "https://$it" }
                    val response = ctx.device.httpRequest(HttpRequest(step.method, url, step.headers.mapValues { fill(it.value, values) }, body, if (body != null) "application/json" else null))
                    RunValue.Text(String(response.bytes, Charsets.UTF_8))
                }
                is PluginStep.Intent -> {
                    ctx.device.sendIntent(
                        IntentSpec(step.action, fill(step.data, values), step.packageName, step.mime, step.extras.mapValues { fill(it.value, values) }, step.broadcast),
                    )
                    RunValue.Nothing
                }
            }
        }
        return if (hasOutput) result else RunValue.Nothing
    }

    companion object {
        fun fill(template: String, values: Map<String, String>): String =
            Regex("\\{\\{\\s*([^}]+?)\\s*\\}\\}").replace(template) { values[it.groupValues[1]] ?: "" }
    }
}
