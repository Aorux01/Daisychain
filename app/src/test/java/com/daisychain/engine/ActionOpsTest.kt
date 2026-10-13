package com.daisychain.engine

import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.edit.ActionTree
import com.daisychain.engine.edit.FlowNode
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.spec.ActionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionOpsTest {
    private fun def(id: String) = ActionRegistry.find(id)!!
    private val iff = "is.workflow.actions.conditional"
    private val menu = "is.workflow.actions.choosefrommenu"
    private val battery = "is.workflow.actions.getbatterylevel"
    private val text = "is.workflow.actions.gettext"

    @Test
    fun ifCreatesStartOtherwiseEnd() {
        val blocks = ActionOps.create(def(iff))
        assertEquals(listOf(0, 1, 2), blocks.map { it.flow!!.role })
        assertEquals(1, blocks.map { it.flow!!.groupId }.toSet().size)
        assertEquals(ParamValue.Choice("4"), blocks[0].params["WFCondition"])
    }

    @Test
    fun menuStartsWithTwoItems() {
        val blocks = ActionOps.create(def(menu))
        assertEquals(listOf(0, 1, 1, 2), blocks.map { it.flow!!.role })
        assertTrue(blocks[1].params.containsKey("WFMenuItemTitle"))
    }

    @Test
    fun removingAnyEdgeOfAGroupRemovesTheWholeGroup() {
        val list = ActionOps.create(def(battery)) + ActionOps.create(def(iff)) + ActionOps.create(def(text))
        val start = list.first { it.identifier == iff }
        assertEquals(listOf(battery, text), ActionOps.remove(list, start.uuid).map { it.identifier })
        val end = list.last { it.identifier == iff }
        assertEquals(2, ActionOps.remove(list, end.uuid).size)
    }

    @Test
    fun removingAMenuItemKeepsTheRest() {
        val m = ActionOps.create(def(menu))
        val inner = ActionOps.create(def(text))
        val list = listOf(m[0], m[1]) + inner + listOf(m[2], m[3])
        val out = ActionOps.remove(list, m[1].uuid)
        assertEquals(listOf(m[0].uuid, m[2].uuid, m[3].uuid), out.map { it.uuid })
    }

    @Test
    fun duplicateGivesNewIdsAndANewGroup() {
        val list = ActionOps.create(def(iff))
        val out = ActionOps.duplicate(list, list[0].uuid)
        assertEquals(6, out.size)
        assertEquals(6, out.map { it.uuid }.toSet().size)
        assertEquals(2, out.map { it.flow!!.groupId }.toSet().size)
    }

    @Test
    fun treeNestsGroupsAndKeepsStrayBlocks() {
        val outer = ActionOps.create(def(iff))
        val inner = ActionOps.create(def(iff))
        val leaf = ActionOps.create(def(text))[0]
        val list = listOf(outer[0]) + inner + leaf + listOf(outer[1], outer[2])
        val tree = ActionTree.parse(list)
        assertEquals(1, tree.size)
        val g = tree[0] as FlowNode.Group
        assertEquals(2, g.segments.size)
        assertEquals(2, g.segments[0].children.size) // inner group + leaf
        assertTrue(g.segments[0].children[0] is FlowNode.Group)

        val stray = ActionTree.parse(listOf(outer[2]))
        assertTrue(stray[0] is FlowNode.Leaf)
        val unclosed = ActionTree.parse(listOf(outer[0], leaf))
        assertEquals(null, (unclosed[0] as FlowNode.Group).end)
    }

    @Test
    fun insertAndMove() {
        val a = ActionOps.create(def(text))[0]
        val b = ActionOps.create(def(battery))[0]
        val g = ActionOps.create(def(iff))
        val list = listOf(a, b) + g // a b if otherwise end
        // move `a` into the Otherwise branch (before the End block, index 4)
        val moved = ActionOps.move(list, a.uuid, 4)
        assertEquals(listOf(b.uuid, g[0].uuid, g[1].uuid, a.uuid, g[2].uuid), moved.map { it.uuid })
        // move the whole If group to the top
        val top = ActionOps.move(list, g[0].uuid, 0)
        assertEquals(listOf(g[0].uuid, g[1].uuid, g[2].uuid, a.uuid, b.uuid), top.map { it.uuid })
        // dropping a group inside itself, or on its own edges, changes nothing
        assertEquals(list, ActionOps.move(list, g[0].uuid, 3))
        assertEquals(list, ActionOps.move(list, g[0].uuid, 5))
        assertEquals(listOf(a.uuid, b.uuid, g[0].uuid), ActionOps.insertAt(listOf(a, b), 2, listOf(g[0])).map { it.uuid })
    }

    @Test
    fun variablesListOnlyEarlierOutputsPlusBuiltIns() {
        val b = ActionOps.create(def(battery))[0]
        val set = ActionInstance(
            "s", ActionOps.SET_VARIABLE_ID,
            params = mapOf(ActionOps.VARIABLE_NAME_KEY to ParamValue.Text(TokenString.of("level"))),
        )
        val t = ActionOps.create(def(text))[0]
        val vars = ActionOps.availableVariables(listOf(b, set, t), t.uuid).map { it.ref.source }
        assertTrue(VariableSource.ActionOutput(b.uuid) in vars)
        assertTrue(VariableSource.Named("level") in vars)
        assertTrue(VariableSource.ShortcutInput in vars)
        assertNotEquals(true, vars.contains(VariableSource.ActionOutput(t.uuid)))
    }
}
