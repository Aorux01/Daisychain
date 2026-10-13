package com.daisychain.engine.edit

import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.spec.ActionRegistry

/** The flat action list seen as a tree: plain blocks and control-flow groups (If, Repeat, Menu). */
sealed interface FlowNode {
    data class Leaf(val action: ActionInstance) : FlowNode

    /**
     * @property segments the start block followed by every middle block (Otherwise, menu items), each with
     *   the nodes that sit under it.
     * @property end the closing block; null when the list is malformed and the group was never closed.
     */
    data class Group(val groupId: String, val segments: List<Segment>, val end: ActionInstance?) : FlowNode
}

data class Segment(val header: ActionInstance, val children: List<FlowNode>)

object ActionTree {
    private class Builder(val groupId: String, header: ActionInstance) {
        val segments = mutableListOf<Pair<ActionInstance, MutableList<FlowNode>>>(header to mutableListOf())
        val current get() = segments.last().second
    }

    fun parse(actions: List<ActionInstance>): List<FlowNode> {
        val root = mutableListOf<FlowNode>()
        val stack = ArrayDeque<Builder>()

        fun add(node: FlowNode) = (stack.lastOrNull()?.current ?: root).add(node)
        fun toGroup(b: Builder, end: ActionInstance?) =
            FlowNode.Group(b.groupId, b.segments.map { (h, c) -> Segment(h, c) }, end)

        for (a in actions) {
            val membership = a.flow
            val roles = ActionRegistry.find(a.identifier)?.flow?.roles?.size
            if (membership == null || roles == null) {
                add(FlowNode.Leaf(a))
                continue
            }
            val top = stack.lastOrNull()
            when {
                membership.role == 0 -> stack.addLast(Builder(membership.groupId, a))
                membership.role == roles - 1 && top?.groupId == membership.groupId -> {
                    stack.removeLast()
                    add(toGroup(top, a))
                }
                membership.role < roles - 1 && top?.groupId == membership.groupId ->
                    top.segments.add(a to mutableListOf())
                else -> add(FlowNode.Leaf(a)) // stray block: show it rather than lose it
            }
        }
        // Groups never closed: close them so their content is still displayed.
        while (stack.isNotEmpty()) {
            val b = stack.removeLast()
            add(toGroup(b, null))
        }
        return root
    }
}
