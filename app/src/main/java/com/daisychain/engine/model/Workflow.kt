package com.daisychain.engine.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * In-memory model of a shortcut, deliberately close to the iOS `.shortcut` property list so that
 * import/export is lossless. Actions are a FLAT list (like iOS); nesting (If / Repeat / Menu) is
 * expressed with [FlowMembership] and rebuilt as a tree by the editor.
 */
data class Workflow(
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val actions: List<ActionInstance> = emptyList(),
)

/**
 * One block placed in a shortcut.
 *
 * @property uuid identifies this block; other blocks reference its output with [VariableSource.ActionOutput].
 * @property identifier the action id, e.g. `is.workflow.actions.conditional`. Unknown ids are kept as is
 *   (shown as "unsupported") so nothing is lost on import/export.
 */
@Serializable
data class ActionInstance(
    val uuid: String,
    val identifier: String,
    val params: Map<String, ParamValue> = emptyMap(),
    val flow: FlowMembership? = null,
    /** User-chosen name of this block's output ("Rename" in the variable picker). */
    val outputName: String? = null,
)

/** Which control-flow group a block belongs to (iOS: GroupingIdentifier + WFControlFlowMode). */
@Serializable
data class FlowMembership(val groupId: String, val role: Int)

/** Converts the action list to and from the JSON stored in the database. */
object ActionsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val serializer = ListSerializer(ActionInstance.serializer())

    fun encode(actions: List<ActionInstance>): String = json.encodeToString(serializer, actions)

    /** Returns an empty list when the stored text is unreadable, so a bad row never crashes the app. */
    fun decode(text: String): List<ActionInstance> =
        runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}
