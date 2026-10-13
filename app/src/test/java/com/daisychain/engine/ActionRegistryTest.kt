package com.daisychain.engine

import com.daisychain.engine.spec.ActionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRegistryTest {
    @Test
    fun builtInBlocksAreDiscoveredAutomatically() {
        assertTrue("expected built-in blocks, found ${ActionRegistry.all().size}", ActionRegistry.all().size >= 15)
        assertNotNull(ActionRegistry.find("is.workflow.actions.conditional"))
    }

    @Test
    fun idsAreUnique() {
        val ids = ActionRegistry.all().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyBlockHasTitlesInBothLanguages() {
        ActionRegistry.all().forEach { def ->
            assertTrue(def.id, def.title.resolve("fr").isNotBlank() && def.title.resolve("en").isNotBlank())
        }
    }

    @Test
    fun searchMatchesAnyLanguageAndKeywords() {
        assertTrue(ActionRegistry.search("sinon").any { it.id.endsWith("conditional") })
        assertTrue(ActionRegistry.search("loop").any { it.id.endsWith("repeat.count") })
    }

    @Test
    fun unknownIdentifierIsNull() {
        assertEquals(null, ActionRegistry.find("com.example.nope"))
    }
}
