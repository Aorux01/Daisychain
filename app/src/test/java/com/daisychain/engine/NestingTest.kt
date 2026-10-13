package com.daisychain.engine

import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.edit.ActionTree
import com.daisychain.engine.edit.FlowNode
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.spec.ActionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NestingTest {
    private fun create(id: String) = ActionOps.create(ActionRegistry.find(id)!!)
    private val menu = "is.workflow.actions.choosefrommenu"
    private val repeat = "is.workflow.actions.repeat.count"
    private val text = "is.workflow.actions.gettext"

    /** Where the editor inserts when the "add action" placeholder of segment [segment] of [group] is used. */
    private fun slotIndex(actions: List<ActionInstance>, group: FlowNode.Group, segment: Int): Int {
        fun indexOf(uuid: String) = actions.indexOfFirst { it.uuid == uuid }
        return group.segments.getOrNull(segment + 1)?.header?.uuid?.let(::indexOf)
            ?: group.end?.uuid?.let(::indexOf)
            ?: actions.size
    }

    @Test
    fun aBlockCanStillBeAddedToAMenuItemAfterARepeatWasPutInIt() {
        var list = create(menu) // start, item 1, item 2, end
        val menuGroup = ActionTree.parse(list)[0] as FlowNode.Group
        // a Repeat into item 1
        list = ActionOps.insertAt(list, slotIndex(list, menuGroup, 1), create(repeat))
        // then a text into item 1 again
        val menuAfter = ActionTree.parse(list)[0] as FlowNode.Group
        val item1 = menuAfter.segments[1]
        assertEquals(1, item1.children.size)
        list = ActionOps.insertAt(list, slotIndex(list, menuAfter, 1), create(text))

        val finalMenu = ActionTree.parse(list)[0] as FlowNode.Group
        assertEquals(3, finalMenu.segments.size)
        val children = finalMenu.segments[1].children
        assertEquals(2, children.size)
        assertTrue(children[0] is FlowNode.Group)
        assertTrue(children[1] is FlowNode.Leaf)
        // and nothing went inside the Repeat
        assertTrue((children[0] as FlowNode.Group).segments[0].children.isEmpty())
    }
}
