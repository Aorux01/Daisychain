package com.daisychain.engine

import com.daisychain.app.ui.help.helpTopics
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Translations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text a block shows must exist in Italian, German and Spanish. */
class TranslationsTest {
    private fun texts(): List<LocalizedText> {
        val out = mutableListOf<LocalizedText>()
        fun param(p: ParamSpec) {
            out += p.label
            p.help?.let { out += it }
            (p.type as? ParamType.Choice)?.options?.forEach { out += it.label }
        }
        for (d in ActionRegistry.all()) {
            out += d.title
            d.description?.let { out += it }
            d.outputLabel?.let { out += it }
            d.displayParams.forEach(::param)
            d.input?.let { out += it.label; it.help?.let { h -> out += h } }
            d.flow?.roles?.forEach { r -> out += r.title; r.addLabel?.let { out += it }; r.params.forEach(::param) }
            d.repeatable?.let { out += it.addLabel; out += it.removeLabel; it.joinOptions.forEach { o -> out += o.label } }
            out += d.category.title
        }
        helpTopics.forEach { out += it.title; out += it.body }
        return out
    }

    @Test fun everyBlockTextIsTranslated() {
        val missing = texts().filter { !(it.hasLanguage("it") && it.hasLanguage("de") && it.hasLanguage("es")) }.map { it.resolve("en") }.distinct()
            .filter { it.isNotEmpty() }
            .flatMap { en -> listOf("it", "de", "es").filter { Translations.lookup(it, en) == null }.map { "$it: $en" } }
        assertTrue("Not translated:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun carriesValuesOverInMessages() {
        assertEquals("Chiave non trovata: x", Translations.lookup("it", "Key not found: x"))
        assertEquals("Schlüssel nicht gefunden: x (die Schlüssel sind: a, b)", Translations.lookup("de", "Key not found: x (the keys are: a, b)"))
    }
}
