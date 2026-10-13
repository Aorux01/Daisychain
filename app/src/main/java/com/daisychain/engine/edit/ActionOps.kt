package com.daisychain.engine.edit

import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.FlowMembership
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.RepeatableParams
import java.util.UUID

/** A variable the user can pick for a parameter. */
data class VariableOption(val ref: VariableRef, val type: ContentType)

/** Pure edits of the flat action list; every function returns a new list. */
object ActionOps {
    const val SET_VARIABLE_ID = "is.workflow.actions.setvariable"
    const val VARIABLE_NAME_KEY = "WFVariableName"
    private const val APPEND_VARIABLE_ID = "is.workflow.actions.appendvariable"
    private const val REPEAT_PREFIX = "is.workflow.actions.repeat"
    private const val REPEAT_EACH_ID = "is.workflow.actions.repeat.each"

    /** Blocks to insert for [def]: one block, or the whole group (start, middles, end) for control flow. */
    fun create(def: ActionDefinition): List<ActionInstance> {
        val flow = def.flow ?: return listOf(instance(def, null, def.params))
        val groupId = newId()
        return flow.roles.flatMapIndexed { index, role ->
            val params = if (index == 0) def.params else role.params
            // A repeatable role (menu item) starts with two entries, like iOS.
            List(if (role.repeatable) 2 else 1) { instance(def, FlowMembership(groupId, index), params) }
        }
    }

    private fun instance(def: ActionDefinition, flow: FlowMembership?, params: List<ParamSpec>) = ActionInstance(
        uuid = newId(),
        identifier = def.id,
        params = params.mapNotNull { p -> p.default?.let { p.key to it } }.toMap(),
        flow = flow,
    )

    /** Indices removed together with the block [uuid] (its whole group when it is part of one). */
    fun range(actions: List<ActionInstance>, uuid: String): IntRange {
        val index = actions.indexOfFirst { it.uuid == uuid }
        if (index < 0) return IntRange.EMPTY
        val membership = actions[index].flow ?: return index..index
        val spec = ActionRegistry.find(actions[index].identifier)?.flow ?: return index..index
        val group = actions.indices.filter { actions[it].flow?.groupId == membership.groupId }

        val role = spec.roles.getOrNull(membership.role)
        if (role != null && role.repeatable) {
            // One menu item with everything under it, up to the next block of the same group.
            val next = group.firstOrNull { it > index } ?: return index..index
            return index until next
        }
        return group.first()..group.last()
    }

    /** Inserts [blocks] so they end up at position [index] (clamped to the list). */
    fun insertAt(actions: List<ActionInstance>, index: Int, blocks: List<ActionInstance>): List<ActionInstance> {
        val i = index.coerceIn(0, actions.size)
        return actions.take(i) + blocks + actions.drop(i)
    }

    /**
     * Moves a block (with everything it contains) so it sits before the block currently at [targetIndex]
     * (use `actions.size` for "at the end"). Does nothing when the target is inside the moved range.
     */
    fun move(actions: List<ActionInstance>, uuid: String, targetIndex: Int): List<ActionInstance> {
        val r = range(actions, uuid)
        if (r.isEmpty() || targetIndex in r.first..(r.last + 1)) return actions
        val moved = actions.slice(r)
        val rest = actions.filterIndexed { i, _ -> i !in r }
        val at = if (targetIndex > r.last) targetIndex - moved.size else targetIndex
        return rest.take(at) + moved + rest.drop(at)
    }

    fun removalCount(actions: List<ActionInstance>, uuid: String): Int = range(actions, uuid).count()

    fun remove(actions: List<ActionInstance>, uuid: String): List<ActionInstance> {
        val r = range(actions, uuid)
        if (r.isEmpty()) return actions
        return actions.filterIndexed { i, _ -> i !in r }
    }

    fun duplicate(actions: List<ActionInstance>, uuid: String): List<ActionInstance> {
        val r = range(actions, uuid)
        if (r.isEmpty()) return actions
        val groupIds = mutableMapOf<String, String>()
        val copy = actions.slice(r).map { a ->
            a.copy(
                uuid = newId(),
                flow = a.flow?.let { it.copy(groupId = groupIds.getOrPut(it.groupId) { newId() }) },
            )
        }
        return actions.take(r.last + 1) + copy + actions.drop(r.last + 1)
    }

    fun setParam(actions: List<ActionInstance>, uuid: String, key: String, value: ParamValue?): List<ActionInstance> =
        actions.map { a ->
            if (a.uuid != uuid) a else a.copy(params = if (value == null) a.params - key else a.params + (key to value))
        }

    /** The extra rows of a block that has [ActionDefinition.repeatable] params (extra conditions...). */
    fun rowsOf(action: ActionInstance): List<Map<String, ParamValue>> {
        val key = ActionRegistry.find(action.identifier)?.repeatable?.storageKey ?: return emptyList()
        return (action.params[key] as? ParamValue.Array)?.items?.map { (it as? ParamValue.Dict)?.entries ?: emptyMap() }.orEmpty()
    }

    private fun changeRows(
        actions: List<ActionInstance>,
        uuid: String,
        change: (ActionDefinition, List<Map<String, ParamValue>>) -> List<Map<String, ParamValue>>,
    ): List<ActionInstance> = actions.map { a ->
        val def = ActionRegistry.find(a.identifier)
        val key = def?.repeatable?.storageKey
        if (a.uuid != uuid || def == null || key == null) a else {
            val rows = change(def, rowsOf(a))
            a.copy(params = if (rows.isEmpty()) a.params - key else a.params + (key to ParamValue.Array(rows.map { ParamValue.Dict(it) })))
        }
    }

    /** Adds a row with the default values, joined with the first join option. */
    fun addRow(actions: List<ActionInstance>, uuid: String): List<ActionInstance> = changeRows(actions, uuid) { def, rows ->
        val rep = def.repeatable!!
        val defaults = def.params.mapNotNull { p -> p.default?.let { p.key to it } }.toMap()
        val join = rep.joinOptions.firstOrNull()?.let { mapOf(RepeatableParams.JOIN_KEY to ParamValue.Choice(it.id)) }.orEmpty()
        rows + (defaults + join)
    }

    fun setRowParam(actions: List<ActionInstance>, uuid: String, row: Int, key: String, value: ParamValue?): List<ActionInstance> =
        changeRows(actions, uuid) { _, rows ->
            rows.mapIndexed { i, r -> if (i != row) r else if (value == null) r - key else r + (key to value) }
        }

    fun removeRow(actions: List<ActionInstance>, uuid: String, row: Int): List<ActionInstance> =
        changeRows(actions, uuid) { _, rows -> rows.filterIndexed { i, _ -> i != row } }

    /** Variables available to the block [beforeUuid]: outputs and named variables set before it, plus the built-ins. */
    fun availableVariables(actions: List<ActionInstance>, beforeUuid: String?): List<VariableOption> {
        val before = if (beforeUuid == null) actions else actions.takeWhile { it.uuid != beforeUuid }
        val result = mutableListOf<VariableOption>()
        val names = mutableSetOf<String>()
        // Inside a "Repeat" the current item and position can be used.
        val openRepeats = mutableSetOf<String>()
        val headers = mutableMapOf<String, ActionInstance>()
        val groupResults = mutableListOf<VariableOption>()
        for (a in before) {
            val membership = a.flow ?: continue
            val roles = ActionRegistry.find(a.identifier)?.flow?.roles?.size ?: continue
            if (membership.role == 0) headers[membership.groupId] = a
            if (membership.role == 0 && a.identifier.startsWith(REPEAT_PREFIX)) openRepeats += membership.groupId
            if (membership.role == roles - 1) {
                openRepeats -= membership.groupId
                // A finished "Repeat" hands back the list of its results.
                headers[membership.groupId]?.let { header ->
                    ActionRegistry.find(header.identifier)?.produces?.let { type ->
                        groupResults += VariableOption(VariableRef(VariableSource.ActionOutput(header.uuid)), type)
                    }
                }
            }
        }
        if (openRepeats.isNotEmpty()) {
            result += VariableOption(VariableRef(VariableSource.RepeatItem), ContentType.Any)
            result += VariableOption(VariableRef(VariableSource.RepeatIndex), ContentType.Number)
        }
        for (a in before) {
            if (a.identifier == SET_VARIABLE_ID) {
                val name = ((a.params[VARIABLE_NAME_KEY] as? ParamValue.Text)?.value?.parts?.firstOrNull() as?
                    com.daisychain.engine.model.TokenString.Part.Literal)?.text?.trim().orEmpty()
                if (name.isNotEmpty() && names.add(name)) {
                    result += VariableOption(VariableRef(VariableSource.Named(name)), ContentType.Any)
                }
                continue
            }
            // A group (Repeat) only gives its results once it has ended; those are added above.
            if (a.flow != null && ActionRegistry.find(a.identifier)?.flow != null) continue
            val type = ActionRegistry.find(a.identifier)?.produces ?: continue
            result += VariableOption(VariableRef(VariableSource.ActionOutput(a.uuid)), type)
        }
        result += groupResults
        result += VariableOption(VariableRef(VariableSource.ShortcutInput), ContentType.Any)
        result += VariableOption(VariableRef(VariableSource.Clipboard), ContentType.Text)
        result += VariableOption(VariableRef(VariableSource.CurrentDate), ContentType.Date)
        result += VariableOption(VariableRef(VariableSource.AskWhenRun), ContentType.Any)
        result += VariableOption(VariableRef(VariableSource.Empty), ContentType.Any)
        return result
    }

    /**
     * What the block [uuid] receives when its input is left on "automatic": the nearest block above that produces
     * something, the current item when it is the first thing inside a "Repeat with each", or the shortcut input.
     */
    fun previousOutput(actions: List<ActionInstance>, uuid: String, ofType: ContentType? = null): VariableRef? {
        var j = actions.indexOfFirst { it.uuid == uuid } - 1
        while (j >= 0) {
            if (ofType != null) {
                // Looking for the latest block that gives one kind of value (for example a dictionary).
                val candidate = actions[j]
                if (candidate.flow == null && ActionRegistry.find(candidate.identifier)?.produces == ofType) {
                    return VariableRef(VariableSource.ActionOutput(candidate.uuid))
                }
                j--
                continue
            }
            val a = actions[j]
            val def = ActionRegistry.find(a.identifier)
            val membership = a.flow
            val roles = def?.flow?.roles?.size
            if (membership != null && roles != null) {
                if (membership.role == roles - 1) {
                    // The end of a group above: its results if it has some, otherwise look above the group.
                    val head = actions.indexOfFirst { it.flow?.groupId == membership.groupId && it.flow?.role == 0 }
                    if (head >= 0) {
                        if (ActionRegistry.find(actions[head].identifier)?.produces != null) {
                            return VariableRef(VariableSource.ActionOutput(actions[head].uuid))
                        }
                        j = head - 1
                        continue
                    }
                } else if (membership.role == 0 && a.identifier == REPEAT_EACH_ID) {
                    return VariableRef(VariableSource.RepeatItem)
                }
                j--
                continue
            }
            if (def?.produces != null) return VariableRef(VariableSource.ActionOutput(a.uuid))
            j--
        }
        return if (ofType == null) VariableRef(VariableSource.ShortcutInput) else null
    }

    /** The kind of value a variable gives. */
    private fun typeOf(ref: VariableRef, actions: List<ActionInstance>): ContentType = when (val source = ref.source) {
        is VariableSource.ActionOutput ->
            actions.firstOrNull { it.uuid == source.actionUuid }?.let { ActionRegistry.find(it.identifier)?.produces } ?: ContentType.Any
        VariableSource.Clipboard -> ContentType.Text
        VariableSource.CurrentDate -> ContentType.Date
        VariableSource.RepeatIndex -> ContentType.Number
        else -> ContentType.Any
    }

    /**
     * Resets to "automatic" any variable choice that does not fit its parameter, for example a list picked where a
     * dictionary is needed (the picker no longer offers it, but it may have been saved before).
     */
    fun sanitize(actions: List<ActionInstance>): List<ActionInstance> = actions.map { action ->
        val def = ActionRegistry.find(action.identifier) ?: return@map action
        var params = action.params
        for (spec in def.displayParams) {
            val allowed = spec.variableTypes ?: continue
            val chosen = (params[spec.key] as? ParamValue.Variable)?.ref ?: continue
            if (typeOf(chosen, actions) !in allowed) params = params - spec.key
        }
        if (params === action.params) action else action.copy(params = params)
    }

    /** Names of the variables already defined by "Set variable" / "Add to variable" blocks, sorted. */
    fun variableNames(actions: List<ActionInstance>): List<String> = actions
        .filter { it.identifier == SET_VARIABLE_ID || it.identifier == APPEND_VARIABLE_ID }
        .mapNotNull { a ->
            ((a.params[VARIABLE_NAME_KEY] as? ParamValue.Text)?.value?.parts?.firstOrNull() as?
                com.daisychain.engine.model.TokenString.Part.Literal)?.text?.trim()?.takeIf { it.isNotEmpty() }
        }
        .distinct()
        .sortedBy { it.lowercase() }

    /**
     * Creates the variable [name] right before the block [beforeUuid]: a "Set variable" block that stores
     * the output of whatever comes before it.
     */
    fun insertSetVariableBefore(actions: List<ActionInstance>, beforeUuid: String, name: String): List<ActionInstance> {
        val index = actions.indexOfFirst { it.uuid == beforeUuid }
        if (index < 0) return actions
        val block = ActionInstance(
            uuid = newId(),
            identifier = SET_VARIABLE_ID,
            params = mapOf(VARIABLE_NAME_KEY to ParamValue.Text(com.daisychain.engine.model.TokenString.of(name))),
        )
        return insertAt(actions, index, listOf(block))
    }

    /** Adds one more entry of the repeatable role (a menu choice) to the group that contains [uuid], just before its end. */
    fun addSegment(actions: List<ActionInstance>, uuid: String): List<ActionInstance> {
        val member = actions.firstOrNull { it.uuid == uuid } ?: return actions
        val membership = member.flow ?: return actions
        val def = ActionRegistry.find(member.identifier) ?: return actions
        val roles = def.flow?.roles ?: return actions
        val roleIndex = roles.indexOfFirst { it.repeatable }
        if (roleIndex < 0) return actions
        val endIndex = actions.indexOfLast { it.flow?.groupId == membership.groupId }
        val entry = instance(def, FlowMembership(membership.groupId, roleIndex), roles[roleIndex].params)
        return insertAt(actions, endIndex, listOf(entry))
    }

    /** Names (or, with null / blank, un-names) the output of a block. */
    fun setOutputName(actions: List<ActionInstance>, uuid: String, name: String?): List<ActionInstance> =
        actions.map { if (it.uuid == uuid) it.copy(outputName = name?.trim()?.ifEmpty { null }) else it }

    /** Puts [def] where the block [uuid] is (used to fix a block that no longer exists). The output name is kept. */
    fun replace(actions: List<ActionInstance>, uuid: String, def: ActionDefinition): List<ActionInstance> {
        val index = actions.indexOfFirst { it.uuid == uuid }
        if (index < 0) return actions
        val old = actions[index]
        val created = create(def)
        val fresh = if (def.flow == null) listOf(created.first().copy(uuid = old.uuid, outputName = old.outputName)) else created
        return actions.take(index) + fresh + actions.drop(index + 1)
    }

    private fun newId() = UUID.randomUUID().toString()
}
