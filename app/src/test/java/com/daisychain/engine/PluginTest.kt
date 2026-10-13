package com.daisychain.engine

import com.daisychain.engine.plugin.PluginException
import com.daisychain.engine.plugin.PluginParser
import com.daisychain.engine.plugin.DeclarativeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginTest {
    private val sample = """
        {"id":"demo.hello","name":"Demo",
         "blocks":[{"id":"greet","title":{"en":"Greet","fr":"Saluer"},
           "params":[{"key":"Who","label":"Who","type":"text","default":"world"}],
           "steps":[{"type":"text","template":"Hello {{Who}}!"},{"type":"http","url":"example.com/{{result}}"}]}]}
    """.trimIndent()

    @Test fun parsesBlocksAndCapabilities() {
        val p = PluginParser.parse(sample)
        assertEquals("plugin.demo.hello.greet", p.blocks.single().id)
        assertEquals("Saluer", p.blocks.single().title.resolve("fr"))
        assertEquals("Greet", p.blocks.single().title.resolve("de"))
        assertTrue(p.needsNetwork)
        assertEquals(false, p.usesIntents)
    }

    @Test fun rejectsBadFiles() {
        for (bad in listOf("nope", """{"id":"x","blocks":[]}""", """{"id":"abc.def","blocks":[{"id":"a","steps":[{"type":"shell"}]}]}""")) {
            try { PluginParser.parse(bad); fail("accepted: $bad") } catch (_: PluginException) {}
        }
    }

    @Test fun documentationExamplesAreValid() {
        val dir = java.io.File("../docs/examples")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("no example found in ${dir.absolutePath}", files.isNotEmpty())
        files.forEach { PluginParser.parse(it.readText()) }
    }

    @Test fun fillsTemplates() {
        assertEquals("a-1-", DeclarativeAction.fill("a-{{ x }}-{{missing}}", mapOf("x" to "1")))
    }
}
