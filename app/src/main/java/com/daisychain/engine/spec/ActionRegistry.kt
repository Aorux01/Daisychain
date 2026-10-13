package com.daisychain.engine.spec

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Knows every block. Built-in blocks are found automatically through [ServiceLoader]
 * (see `@AutoService` on each definition); plugins add theirs with [register].
 */
object ActionRegistry {
    private val byId = ConcurrentHashMap<String, ActionDefinition>()
    private val byAlias = ConcurrentHashMap<String, ActionDefinition>()
    /** Which plugin registered a block; built-ins are absent. */
    private val owners = ConcurrentHashMap<String, String>()

    init {
        ServiceLoader.load(ActionDefinition::class.java, ActionDefinition::class.java.classLoader)
            .forEach { add(it, owner = null) }
    }

    private fun add(def: ActionDefinition, owner: String?) {
        require(byId.putIfAbsent(def.id, def) == null) { "Duplicate action id: ${def.id}" }
        def.aliases.forEach { byAlias.putIfAbsent(it, def) }
        owner?.let { owners[def.id] = it }
    }

    /** Adds a block at runtime. Throws if the id is already taken. */
    fun register(def: ActionDefinition, pluginId: String) = add(def, pluginId)

    /** Removes everything a plugin added. */
    fun unregisterPlugin(pluginId: String) {
        owners.filterValues { it == pluginId }.keys.forEach { id ->
            byId.remove(id)?.aliases?.forEach { byAlias.remove(it) }
            owners.remove(id)
        }
    }

    /** Finds a block by id or alias; null for unknown identifiers (keep them as "unsupported"). */
    fun find(identifier: String): ActionDefinition? = byId[identifier] ?: byAlias[identifier]

    fun all(): List<ActionDefinition> = byId.values.sortedBy { it.id }

    fun byCategory(): Map<Category, List<ActionDefinition>> = all().groupBy { it.category }

    fun search(query: String): List<ActionDefinition> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return all().filter { def ->
            def.title.let { listOf("en", "fr", "it", "de", "es").any { l -> it.resolve(l).lowercase().contains(q) } } ||
                def.keywords.any { it.lowercase().contains(q) }
        }
    }
}
