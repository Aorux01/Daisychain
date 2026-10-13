package com.daisychain.engine

import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import com.daisychain.engine.actions.Expression
import com.daisychain.engine.run.DeviceApi
import com.daisychain.engine.run.CalendarEventInfo
import com.daisychain.engine.run.ContactInfo
import com.daisychain.engine.run.HttpRequest
import com.daisychain.engine.run.LocationInfo
import com.daisychain.engine.run.PickedFile
import com.daisychain.engine.run.HttpResponse
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.run.ShortcutLibrary
import com.daisychain.engine.run.missingText
import com.daisychain.engine.run.RunFailure
import com.daisychain.engine.run.RunProgress
import com.daisychain.engine.run.RunUi
import com.daisychain.engine.run.ShortcutRunner
import com.daisychain.engine.spec.ActionRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeDevice(var battery: Int = 50) : DeviceApi {
    override fun batteryPercent() = battery
    override fun setVolumePercent(percent: Int) {}
    override fun setBrightnessPercent(percent: Int) {}
    override fun showNotification(title: String, body: String) {}
    override fun openApp(packageName: String) {}
    override fun clipboardText() = "clip"
    override fun setClipboardText(text: String) {}
    override fun openUrl(url: String) {}
    override fun vibrate(millis: Long) {}
    override fun setFlashlight(on: Boolean?) { flashes += on }
    override fun shareText(text: String) {}
    override fun dial(number: String) {}
    override suspend fun speak(text: String) {}
    val saved = mutableListOf<ByteArray>()
    val flashes = mutableListOf<Boolean?>()
    override suspend fun httpGet(url: String) =
        if (url.contains("apod")) HttpResponse("""{"title":"T","url":"https://img.example/x.jpg","n":{"a":[1,2,{"b":true}]}}""".toByteArray(), "application/json")
        else HttpResponse(byteArrayOf(1, 2, 3), "image/jpeg")
    override fun saveImage(bytes: ByteArray, mime: String) { saved += bytes }
    val requests = mutableListOf<HttpRequest>()
    val written = mutableMapOf<String, ByteArray>()
    val temp = linkedMapOf<String, ByteArray>()
    val sms = mutableListOf<Pair<String, String>>()
    override suspend fun currentLocation() = LocationInfo(48.85, 2.35, 12f)
    override fun reverseGeocode(latitude: Double, longitude: Double) = mapOf("city" to "Paris", "country" to "France")
    override fun networkDetails() = mapOf("type" to "wifi", "ssid" to "Home")
    override fun findEvents(fromMillis: Long, toMillis: Long, limit: Int) =
        listOf(CalendarEventInfo("Dentist", fromMillis + 1000, fromMillis + 2000, "Clinic", "", false))
    override fun findContacts(query: String, limit: Int) = listOf(ContactInfo("Ada Lovelace", listOf("+33 1 23"), listOf("ada@example.org")))
    override fun sendSms(number: String, body: String) { sms += number to body }
    override fun readUri(uri: String) = written[uri] ?: ByteArray(0)
    override fun writeUri(uri: String, bytes: ByteArray) { written[uri] = bytes }
    override fun tempWrite(name: String, bytes: ByteArray, append: Boolean) { temp[name] = if (append) (temp[name] ?: ByteArray(0)) + bytes else bytes }
    override fun tempRead(name: String) = temp[name]
    override fun tempList() = temp.keys.sorted()
    override fun tempDelete(name: String) = temp.remove(name) != null
    override fun imageSize(bytes: ByteArray) = 640 to 480
    override suspend fun recognizeText(bytes: ByteArray) = "text in image"
    override suspend fun translate(text: String, sourceTag: String?, targetTag: String) = "[$targetTag] $text"
    override suspend fun detectLanguage(text: String) = "en"
    override fun makeQrCode(text: String, size: Int) = byteArrayOf(9, 9)
    override suspend fun scanCodes(bytes: ByteArray) = listOf("https://example.org")
    override suspend fun dictate(languageTag: String) = "dictated $languageTag"
    override suspend fun httpRequest(request: HttpRequest): HttpResponse {
        requests += request
        return if (request.url.contains("rss")) {
            HttpResponse(
                "<rss><channel><item><title>One</title><link>http://a</link></item><item><title>Two</title><link>http://b</link></item></channel></rss>".toByteArray(),
                "application/rss+xml", request.url,
            )
        } else if (request.url.contains("short")) HttpResponse(ByteArray(0), "text/html", "https://long.example/page")
        else httpGet(request.url)
    }
}

private class FakeUi(var menuChoice: Int = 0) : RunUi {
    val results = mutableListOf<String>()
    var image: RunValue.Image? = null
    var cancelAlerts = false
    var lastAllowCopy: Boolean? = null
    override suspend fun showResult(text: String, image: RunValue.Image?, allowCopy: Boolean) { results += text; lastAllowCopy = allowCopy; if (image != null) this.image = image }
    override suspend fun alert(title: String, message: String, allowCancel: Boolean) = !(allowCancel && cancelAlerts)
    override suspend fun choose(prompt: String, items: List<String>) = menuChoice
    override suspend fun ask(prompt: String) = "asked"
    override suspend fun saveFile(name: String, mime: String) = PickedFile("content://saved/$name", name, mime)
    override suspend fun pickFile(mimeTypes: List<String>) = PickedFile("content://picked/a.txt", "a.txt", "text/plain")
}

private class Log : RunProgress {
    val started = mutableListOf<String>()
    val finished = mutableListOf<String>()
    override fun onStart(uuid: String) { started += uuid }
    override fun onFinish(uuid: String) { finished += uuid }
}

class ShortcutRunnerTest {
    private fun block(id: String, vararg params: Pair<String, ParamValue>): ActionInstance =
        ActionOps.create(ActionRegistry.find(id)!!)[0].let { it.copy(params = it.params + params) }

    private fun text(s: String) = ParamValue.Text(TokenString.of(s))
    private val battery = "is.workflow.actions.getbatterylevel"
    private val iff = "is.workflow.actions.conditional"
    private val show = "is.workflow.actions.showresult"
    private val gettext = "is.workflow.actions.gettext"

    private fun run(actions: List<ActionInstance>, device: DeviceApi = FakeDevice(), ui: FakeUi = FakeUi(), log: Log = Log()) {
        runBlocking { ShortcutRunner(device, ui, stepDelayMs = 0).run(actions, log) }
    }

    @Test
    fun textFlowsIntoShowResult() {
        val ui = FakeUi()
        run(listOf(block(gettext, "WFTextActionText" to text("hello")), block(show)), ui = ui)
        assertEquals(listOf("hello"), ui.results)
    }

    @Test
    fun variablesInTextAreResolved() {
        val b = block(battery)
        val parts = listOf(
            TokenString.Part.Literal("Level "),
            TokenString.Part.Variable(VariableRef(VariableSource.ActionOutput(b.uuid))),
        )
        val t = block(gettext, "WFTextActionText" to ParamValue.Text(TokenString(parts)))
        val ui = FakeUi()
        run(listOf(b, t, block(show)), device = FakeDevice(battery = 15), ui = ui)
        assertEquals(listOf("Level 15"), ui.results)
    }

    private fun branchResults(battery: Int): List<String> {
        val b = block(this.battery)
        val low = block(gettext, "WFTextActionText" to text("low"))
        val ok = block(gettext, "WFTextActionText" to text("ok"))
        val g = ActionOps.create(ActionRegistry.find(iff)!!)
        val start = g[0].copy(
            params = g[0].params + mapOf(
                "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(b.uuid))),
                "WFCondition" to ParamValue.Choice("0"),
                "WFNumberValue" to ParamValue.Number(20.0),
            ),
        )
        val list = listOf(b, start, low, block(show), g[1], ok, block(show), g[2])
        val ui = FakeUi()
        run(list, device = FakeDevice(battery), ui = ui)
        return ui.results
    }

    @Test
    fun ifRunsOnlyTheMatchingBranch() {
        assertEquals(listOf("low"), branchResults(10))
        assertEquals(listOf("ok"), branchResults(80))
    }

    /** battery -> If [battery < 20] <extra rows> -> show "yes" / "no" */
    private fun extraConditionResult(battery: Int, rows: List<Pair<String, Double>>): List<String> {
        val b = block(this.battery)
        val yes = block(gettext, "WFTextActionText" to text("yes"))
        val no = block(gettext, "WFTextActionText" to text("no"))
        val g = ActionOps.create(ActionRegistry.find(iff)!!)
        var start = g[0].copy(
            params = g[0].params + mapOf(
                "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(b.uuid))),
                "WFCondition" to ParamValue.Choice("0"),
                "WFNumberValue" to ParamValue.Number(20.0),
            ),
        )
        var list = listOf(b, start, yes, block(show), g[1], no, block(show), g[2])
        rows.forEach { (join, limit) ->
            list = ActionOps.addRow(list, start.uuid)
            val rowIndex = ActionOps.rowsOf(list.first { it.uuid == start.uuid }).size - 1
            list = ActionOps.setRowParam(list, start.uuid, rowIndex, "join", ParamValue.Choice(join))
            list = ActionOps.setRowParam(list, start.uuid, rowIndex, "WFCondition", ParamValue.Choice("2"))
            list = ActionOps.setRowParam(list, start.uuid, rowIndex, "WFNumberValue", ParamValue.Number(limit))
            list = ActionOps.setRowParam(
                list, start.uuid, rowIndex, "WFInput",
                ParamValue.Variable(VariableRef(VariableSource.ActionOutput(b.uuid))),
            )
            start = list.first { it.uuid == start.uuid }
        }
        val ui = FakeUi()
        run(list, device = FakeDevice(battery), ui = ui)
        return ui.results
    }

    @Test
    fun andNeedsEveryConditionAndOrNeedsOne() {
        // battery < 20 AND battery > 5
        assertEquals(listOf("yes"), extraConditionResult(10, listOf("and" to 5.0)))
        assertEquals(listOf("no"), extraConditionResult(3, listOf("and" to 5.0)))
        // battery < 20 OR battery > 90
        assertEquals(listOf("yes"), extraConditionResult(95, listOf("or" to 90.0)))
        assertEquals(listOf("no"), extraConditionResult(50, listOf("or" to 90.0)))
        // "and" binds tighter than "or":  (<20 AND >5) OR >90  -> 3% is false, 95% is true
        assertEquals(listOf("no"), extraConditionResult(3, listOf("and" to 5.0, "or" to 90.0)))
        assertEquals(listOf("yes"), extraConditionResult(95, listOf("and" to 5.0, "or" to 90.0)))
    }

    @Test
    fun calculateAndRandomNumber() {
        val ui = FakeUi()
        val math = block("is.workflow.actions.math", "WFMathOperation" to ParamValue.Choice("×"), "WFMathOperand" to ParamValue.Number(3.0))
        run(listOf(block("is.workflow.actions.number", "WFNumberActionNumber" to ParamValue.Number(4.0)), math, block(show)), ui = ui)
        assertEquals(listOf("12"), ui.results)

        val rnd = block(
            "is.workflow.actions.number.random",
            "WFRandomNumberMinimum" to ParamValue.Number(7.0),
            "WFRandomNumberMaximum" to ParamValue.Number(9.0),
        )
        repeat(20) {
            val u = FakeUi()
            run(listOf(rnd, block(show)), ui = u)
            assertTrue(u.results.single().toInt() in 7..9)
        }
    }

    private fun results(vararg blocks: ActionInstance): List<String> {
        val ui = FakeUi()
        run(blocks.toList() + block(show), ui = ui)
        return ui.results
    }

    private fun txt(s: String) = block(gettext, "WFTextActionText" to text(s))

    @Test
    fun textAndListBlocks() {
        assertEquals(listOf("HELLO"), results(txt("hello"), block("is.workflow.actions.text.changecase")))
        assertEquals(
            listOf("Hello World"),
            results(txt("hELLO wORLD"), block("is.workflow.actions.text.changecase", "WFCaseType" to ParamValue.Choice("Capitalize Every Word"))),
        )
        assertEquals(
            listOf("a-c-d"),
            results(txt("a-b-d"), block("is.workflow.actions.text.replace", "WFReplaceTextFind" to text("b"), "WFReplaceTextReplace" to text("c"))),
        )
        val split = block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Custom"), "WFTextCustomSeparator" to text(","))
        assertEquals(listOf("3"), results(txt("x,y,z"), split, block("is.workflow.actions.math", "WFMathOperation" to ParamValue.Choice("count"))))
        assertEquals(listOf("z"), results(txt("x,y,z"), split, block("is.workflow.actions.getitemfromlist", "WFItemSpecifier" to ParamValue.Choice("Last Item"))))
        val combine = block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces"))
        assertEquals(listOf("x y z"), results(txt("x,y,z"), split, combine))
        assertEquals(listOf("5"), results(txt("hello"), block("is.workflow.actions.math", "WFMathOperation" to ParamValue.Choice("chars"))))
    }

    @Test
    fun nasaPictureFlow() {
        val device = FakeDevice()
        val ui = FakeUi()
        val flow = listOf(
            block("is.workflow.actions.downloadurl", "WFURL" to text("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY")),
            block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("url")),
            block("is.workflow.actions.downloadurl"),
            block(show),
            block("is.workflow.actions.alert", "WFAlertActionTitle" to text("Save?")),
            block("is.workflow.actions.savetocameraroll"),
        )
        run(flow, device = device, ui = ui)
        assertEquals(1, device.saved.size)
        assertEquals(listOf<Byte>(1, 2, 3), device.saved[0].toList())
        assertTrue(ui.image != null)

        // Cancel on the alert stops the shortcut before saving
        val cancelled = FakeDevice()
        run(flow, device = cancelled, ui = FakeUi().also { it.cancelAlerts = true })
        assertTrue(cancelled.saved.isEmpty())
    }

    private fun mathBlock(op: String, input: Double? = null, extra: Map<String, ParamValue> = emptyMap()): ActionInstance {
        val b = block("is.workflow.actions.math")
        val given = if (input != null) mapOf("WFInput" to ParamValue.Number(input)) else emptyMap()
        return b.copy(params = b.params + ("WFMathOperation" to ParamValue.Choice(op)) + given + extra)
    }

    @Test
    fun calculateCoversFunctionsRoundingAndStatistics() {
        assertEquals(listOf("4"), results(mathBlock("sqrt", 16.0)))
        assertEquals(listOf("25"), results(mathBlock("square", 5.0)))
        assertEquals(listOf("1024"), results(mathBlock("^", 2.0, mapOf("WFMathOperand" to ParamValue.Number(10.0)))))
        assertEquals(listOf("3"), results(mathBlock("%", 7.0, mapOf("WFMathOperand" to ParamValue.Number(4.0)))))
        assertEquals(listOf("2.46"), results(mathBlock("round", 2.456, mapOf("Decimals" to ParamValue.Number(2.0)))))
        assertEquals(listOf("3"), results(mathBlock("ceil", 2.1)))
        assertEquals(listOf("2"), results(mathBlock("floor", 2.9)))
        assertEquals(listOf("10"), results(txt("1,2,3,4"), mathBlock("sum")))
        assertEquals(listOf("2.5"), results(txt("1,2,3,4"), mathBlock("average")))
        assertEquals(listOf("2.5"), results(txt("1,2,3,4"), mathBlock("median")))
        assertEquals(listOf("4"), results(txt("1,2,3,4"), mathBlock("max")))
    }

    private fun convertBlock(vararg params: Pair<String, ParamValue>) = block("is.workflow.actions.measurement.convert", *params)

    @Test
    fun convertMeasuresBasesAndCharacterCodes() {
        val km = results(convertBlock("WFInput" to text("10"), "Category" to ParamValue.Choice("length"), "From.length" to ParamValue.Choice("km"), "To.length" to ParamValue.Choice("mi")))
        assertEquals(6.2137, km.single().toDouble(), 0.001)
        val fahrenheit = results(convertBlock("WFInput" to text("100"), "Category" to ParamValue.Choice("temperature"), "From.temperature" to ParamValue.Choice("c"), "To.temperature" to ParamValue.Choice("f")))
        assertEquals(212.0, fahrenheit.single().toDouble(), 0.0001)
        assertEquals(listOf("FF"), results(convertBlock("WFInput" to text("255"), "Mode" to ParamValue.Choice("base"), "FromBase" to ParamValue.Number(10.0), "ToBase" to ParamValue.Number(16.0))))
        assertEquals(listOf("65, 98"), results(convertBlock("WFInput" to text("Ab"), "Mode" to ParamValue.Choice("toCodes"))))
        assertEquals(listOf("Hi"), results(convertBlock("WFInput" to text("72 105"), "Mode" to ParamValue.Choice("fromCodes"))))
    }

    @Test
    fun dictionariesAndJson() {
        val dictionary = block("is.workflow.actions.dictionary", "Key" to text("a"), "Value" to text("1"))
        var list = listOf(dictionary)
        list = ActionOps.addRow(list, dictionary.uuid)
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Key", text("b"))
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Value", text("2"))
        assertEquals(listOf("2"), results(*list.toTypedArray(), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("b"))))
        val json = """{"a":{"b":[5,6]},"flag":true}"""
        assertEquals(listOf("6"), results(txt(json), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("a.b.1"))))
        assertEquals(listOf("true"), results(txt(json), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("flag"))))
    }

    @Test
    fun repeatItemAndIndexAreVariables() {
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val split = block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Custom"), "WFTextCustomSeparator" to text(","))
        val showItem = block(show, "Text" to ParamValue.Variable(VariableRef(VariableSource.RepeatItem)))
        val ui = FakeUi()
        run(listOf(txt("a,b,c"), split, each[0], showItem, each[1]), ui = ui)
        assertEquals(listOf("a", "b", "c"), ui.results)

        val count = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.count")!!)
        val showIndex = block(show, "Text" to ParamValue.Variable(VariableRef(VariableSource.RepeatIndex)))
        val ui2 = FakeUi()
        run(listOf(count[0].copy(params = mapOf("WFRepeatCount" to ParamValue.Number(3.0))), showIndex, count[1]), ui = ui2)
        assertEquals(listOf("1", "2", "3"), ui2.results)
    }

    @Test
    fun expressionsAreEvaluated() {
        assertEquals(11.0, Expression.eval("3+4*2"), 0.0)
        assertEquals(25.0, Expression.eval("(2+3)^2"), 0.0)
        assertEquals(2.0, Expression.eval("sqrt(16)/2"), 0.0)
        assertEquals(-3.0, Expression.eval("-3"), 0.0)
        assertEquals(2.5, Expression.eval("5 \u00f7 2"), 0.0)
        try {
            Expression.eval("2+")
            fail("expected an error")
        } catch (_: com.daisychain.engine.run.ActionFailed) {
        }
    }

    @Test
    fun aShortcutCanRunAnotherAndGetItsOutput() {
        val doubler = listOf(
            mathBlock("×", extra = mapOf("WFMathOperand" to ParamValue.Number(2.0))),
            block("is.workflow.actions.output"),
            txt("never reached"),
        )
        val library = object : ShortcutLibrary {
            override suspend fun find(name: String) = if (name.equals("Double", ignoreCase = true)) doubler else null
        }
        val main = listOf(
            block("is.workflow.actions.number", "WFNumberActionNumber" to ParamValue.Number(21.0)),
            block("is.workflow.actions.runworkflow", "WFWorkflowName" to text("double")),
            block(show),
        )
        val ui = FakeUi()
        runBlocking { ShortcutRunner(FakeDevice(), ui, 0, library).run(main, Log()) }
        assertEquals(listOf("42"), ui.results)
    }

    @Test
    fun aBlockThatNoLongerExistsStopsTheRunWithAClearMessage() {
        try {
            run(listOf(ActionInstance("gone", "app.daisychain.removed")))
            fail("expected a failure")
        } catch (e: RunFailure) {
            assertEquals("gone", e.uuid)
            assertEquals(missingText.resolve("en"), e.text.resolve("en"))
        }
    }

    @Test
    fun repeatFlowMakesTheCurrentItemAvailable() {
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val inside = block(show)
        val sources = ActionOps.availableVariables(listOf(each[0], inside, each[1]), inside.uuid).map { it.ref.source }
        assertTrue(VariableSource.RepeatItem in sources && VariableSource.RepeatIndex in sources)
        val outside = ActionOps.availableVariables(listOf(each[0], inside, each[1], block(show)), null).map { it.ref.source }
        assertTrue(VariableSource.RepeatItem !in outside)
    }

    @Test
    fun namedVariablesCanBeCreatedRenamedAndBlocksReplaced() {
        val a = block(gettext, "WFTextActionText" to text("x"))
        val b = block(show)
        val created = ActionOps.insertSetVariableBefore(listOf(a, b), b.uuid, "score")
        assertEquals(listOf(a.uuid, created[1].uuid, b.uuid), created.map { it.uuid })
        assertEquals(listOf("score"), ActionOps.variableNames(created))
        assertTrue(VariableSource.Named("score") in ActionOps.availableVariables(created, b.uuid).map { it.ref.source })

        val renamed = ActionOps.setOutputName(listOf(a), a.uuid, " Greeting ")
        assertEquals("Greeting", renamed[0].outputName)
        assertEquals(null, ActionOps.setOutputName(renamed, a.uuid, "  ")[0].outputName)

        val gone = ActionInstance("gone", "app.daisychain.removed", outputName = "Mine")
        val replaced = ActionOps.replace(listOf(a, gone), "gone", ActionRegistry.find(show)!!)
        assertEquals(show, replaced[1].identifier)
        assertEquals("gone", replaced[1].uuid)
        assertEquals("Mine", replaced[1].outputName)
    }

    @Test
    fun repeatReturnsTheListOfWhatEachTurnEndedWith() {
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val split = block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Custom"), "WFTextCustomSeparator" to text(","))
        val upper = block("is.workflow.actions.text.changecase")
        val combine = block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces"))
        assertEquals(listOf("A B C"), results(txt("a,b,c"), split, each[0], upper, each[1], combine))
    }

    @Test
    fun anInputPointedAtAVariableReplacesThePreviousOutput() {
        val first = txt("hello")
        val second = txt("world")
        val upper = block("is.workflow.actions.text.changecase", "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(first.uuid))))
        assertEquals(listOf("HELLO"), results(first, second, upper))
    }

    @Test
    fun theAutomaticInputIsTheNearestOutputAbove() {
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val text = txt("a")
        val inside = block(show)
        val after = block(show)
        val list = listOf(text, each[0], inside, each[1], after)
        assertEquals(VariableSource.ShortcutInput, ActionOps.previousOutput(list, text.uuid)!!.source)
        assertEquals(VariableSource.RepeatItem, ActionOps.previousOutput(list, inside.uuid)!!.source)
        assertEquals(VariableSource.ActionOutput(each[0].uuid), ActionOps.previousOutput(list, after.uuid)!!.source)
        assertTrue(VariableSource.ActionOutput(each[0].uuid) in ActionOps.availableVariables(list, after.uuid).map { it.ref.source })
        assertTrue(VariableSource.ActionOutput(each[0].uuid) !in ActionOps.availableVariables(list, inside.uuid).map { it.ref.source })
    }

    @Test
    fun morseWithADictionaryAndTheCurrentLetterAsKey() {
        val dictionary = block("is.workflow.actions.dictionary", "Key" to text("S"), "Value" to text("..."))
        var list = listOf(dictionary)
        list = ActionOps.addRow(list, dictionary.uuid)
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Key", text("O"))
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Value", text("---"))
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val split = block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Every Character"))
        // the dictionary is chosen, the key is left empty: it is the current letter
        val lookup = block("is.workflow.actions.getvalueforkey", "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(dictionary.uuid))))
        val combine = block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces"))
        val all = list + txt("SOS") + split + each[0] + lookup + each[1] + combine
        assertEquals(listOf("... --- ..."), results(*all.toTypedArray()))
    }

    @Test
    fun aSavedDictionaryChoiceThatIsNotADictionaryGoesBackToAutomatic() {
        val dictionary = block("is.workflow.actions.dictionary", "Key" to text("S"), "Value" to text("..."))
        var list = listOf(dictionary)
        list = ActionOps.addRow(list, dictionary.uuid)
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Key", text("O"))
        list = ActionOps.setRowParam(list, dictionary.uuid, 0, "Value", text("---"))
        val each = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.each")!!)
        val split = block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Every Character"))
        // as saved by an earlier version: the dictionary was pointed at the split text
        val stale = block("is.workflow.actions.getvalueforkey", "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(split.uuid))))
        val combine = block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces"))
        val saved = list + txt("SOS") + split + each[0] + stale + each[1] + combine

        val clean = ActionOps.sanitize(saved)
        assertTrue(!clean.first { it.uuid == stale.uuid }.params.containsKey("WFInput"))
        assertEquals(saved.size, clean.size)
        assertEquals(listOf("... --- ..."), results(*clean.toTypedArray()))
        // a real dictionary choice is kept
        val valid = stale.copy(params = mapOf("WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(dictionary.uuid)))))
        assertTrue(ActionOps.sanitize(listOf(dictionary, valid))[1].params.containsKey("WFInput"))
        // and with the dictionary left on automatic, no choice is needed at all
        val automatic = list + txt("SOS") + split + each[0] + block("is.workflow.actions.getvalueforkey") + each[1] + combine
        assertEquals(listOf("... --- ..."), results(*automatic.toTypedArray()))
    }

    @Test
    fun aDictionaryLookupWithoutDictionaryOrKeyExplainsWhatIsMissing() {
        try {
            run(listOf(txt("a"), block("is.workflow.actions.getvalueforkey")))
            fail("expected a failure")
        } catch (e: RunFailure) {
            assertTrue(e.text.resolve("en").contains("dictionary"))
        }
    }

    @Test
    fun aVariableCanStartEmptyAndCollectItems() {
        val empty = ParamValue.Variable(VariableRef(VariableSource.Empty))
        val name = "WFVariableName" to text("acc")
        val list = listOf(
            block("is.workflow.actions.setvariable", name, "WFInput" to empty),
            txt("a"), block("is.workflow.actions.appendvariable", name),
            txt("b"), block("is.workflow.actions.appendvariable", name),
            block("is.workflow.actions.getvariable", "WFVariable" to text("acc")),
            block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces")),
        )
        assertEquals(listOf("a b"), results(*list.toTypedArray()))
    }

    @Test
    fun listsAndChoosingFromThem() {
        val list = block("is.workflow.actions.list", "Item" to text("red"))
        var all = listOf(list)
        all = ActionOps.addRow(all, list.uuid)
        all = ActionOps.setRowParam(all, list.uuid, 0, "Item", text("green"))
        val combine = block("is.workflow.actions.text.combine", "WFTextSeparator" to ParamValue.Choice("Spaces"))
        assertEquals(listOf("red green"), results(*(all + combine).toTypedArray()))
        val ui = FakeUi(menuChoice = 1)
        run(all + block("is.workflow.actions.choosefromlist") + block(show), ui = ui)
        assertEquals(listOf("green"), ui.results)
    }

    @Test
    fun numbersTypesAndSearchesInText() {
        assertEquals(listOf("3, 4.5"), results(txt("a 3 b 4,5"), block("is.workflow.actions.detect.number")))
        assertEquals(listOf("Text"), results(txt("x"), block("is.workflow.actions.getitemtype")))
        assertEquals(listOf("List"), results(txt("a,b"), block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Custom"), "WFTextCustomSeparator" to text(",")), block("is.workflow.actions.getitemtype")))
        assertEquals(listOf("12, 7"), results(txt("ab12 cd7"), block("is.workflow.actions.text.match", "Pattern" to text("\\d+"))))
        // group 1 of "name@host"
        assertEquals(listOf("host"), results(txt("me@host"), block("is.workflow.actions.text.match", "Pattern" to text("@(\\w+)"), "Group" to ParamValue.Number(1.0))))
    }

    @Test
    fun base64AndHash() {
        assertEquals(listOf("aGVsbG8="), results(txt("hello"), block("is.workflow.actions.base64encode")))
        assertEquals(listOf("hello"), results(txt("aGVsbG8="), block("is.workflow.actions.base64encode", "WFEncodeMode" to ParamValue.Choice("Decode"))))
        assertEquals(listOf("5d41402abc4b2a76b9719d911017c592"), results(txt("hello"), block("is.workflow.actions.hash", "WFHashType" to ParamValue.Choice("MD5"))))
    }

    @Test
    fun datesCanBeAdjustedAndCompared() {
        val start = txt("2026-10-07")
        val plus3 = block("is.workflow.actions.adjustdate", "Amount" to ParamValue.Number(3.0), "Unit" to ParamValue.Choice("days"))
        val out = results(start, plus3, block("is.workflow.actions.format.date", "WFDateFormatStyle" to ParamValue.Choice("Custom"), "WFDateFormat" to text("yyyy-MM-dd"))).single()
        assertEquals("2026-10-10", out)
        val first = block("is.workflow.actions.gettext", "WFTextActionText" to text("2026-10-07"))
        val between = block(
            "is.workflow.actions.gettimebetweendates",
            "To" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput("later"))),
            "Unit" to ParamValue.Choice("days"),
        )
        val later = ActionInstance("later", "is.workflow.actions.gettext", mapOf("WFTextActionText" to text("2026-10-17")))
        assertEquals(listOf("10"), results(later, first, between))
        assertEquals(1, results(txt("on 2026-10-07 and 07/11/2026"), block("is.workflow.actions.detect.date")).size)
    }

    @Test
    fun webRequestsCarryMethodHeadersAndBody() {
        val device = FakeDevice()
        val headers = block("is.workflow.actions.dictionary", "Key" to text("Authorization"), "Value" to text("Bearer x"))
        val body = block("is.workflow.actions.dictionary", "Key" to text("name"), "Value" to text("Ada"))
        val post = block(
            "is.workflow.actions.downloadurl",
            "WFURL" to text("https://api.example/hook"),
            "Method" to ParamValue.Choice("POST"),
            "Headers" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput(headers.uuid))),
            "Body" to ParamValue.Text(TokenString(listOf(TokenString.Part.Variable(VariableRef(VariableSource.ActionOutput(body.uuid)))))),
        )
        run(listOf(headers, body, post), device = device)
        val sent = device.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("Bearer x", sent.headers["Authorization"])
        assertEquals("application/json", sent.contentType)
        assertEquals("{\"name\":\"Ada\"}", String(sent.body!!))
    }

    @Test
    fun urlsRssAndShortLinks() {
        assertEquals(listOf("https://a.io/x, http://b.org"), results(txt("see https://a.io/x, and http://b.org."), block("is.workflow.actions.detect.link")))
        assertEquals(listOf("One"), results(block("is.workflow.actions.rss", "WFRSSFeedURL" to text("https://site/rss"), "Limit" to ParamValue.Number(1.0)), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("0.title"))))
        assertEquals(listOf("https://long.example/page"), results(txt("short.link/abc"), block("is.workflow.actions.url.expand")))
    }

    @Test
    fun aMenuCanGetMoreChoices() {
        val menu = ActionOps.create(ActionRegistry.find("is.workflow.actions.choosefrommenu")!!)
        val more = ActionOps.addSegment(menu, menu[0].uuid)
        assertEquals(listOf(0, 1, 1, 1, 2), more.map { it.flow!!.role })
        assertTrue(more[3].params.containsKey("WFMenuItemTitle"))
        assertEquals(menu.last().uuid, more.last().uuid)
        // a block that is not part of a group with choices is left alone
        val plain = block(gettext)
        assertEquals(listOf(plain), ActionOps.addSegment(listOf(plain), plain.uuid))
    }

    @Test
    fun weatherIsReadFromTheOpenMeteoAnswer() {
        val json = """{"current":{"temperature_2m":18.5,"apparent_temperature":17.0,"relative_humidity_2m":60,"weather_code":3,"wind_speed_10m":12.0},
            "daily":{"time":["2026-10-07","2026-10-08"],"temperature_2m_max":[20.0,21.0],"temperature_2m_min":[10.0,11.0],"weather_code":[3,61]}}"""
        val weather = com.daisychain.engine.actions.WeatherAction.parse(json, "fr")
        assertEquals(RunValue.Number(18.5), weather.entries["temperature"])
        assertEquals(RunValue.Text("Couvert"), weather.entries["condition"])
        assertEquals(RunValue.Number(10.0), weather.entries["min"])
        assertEquals(2, (weather.entries["forecast"] as RunValue.Items).values.size)
        assertEquals("Rain", com.daisychain.engine.actions.WeatherCodes.describe(63, "en"))
    }

    @Test
    fun locationAndNetworkDetails() {
        assertEquals(listOf("48.85"), results(block("is.workflow.actions.location"), block("is.workflow.actions.properties.locations", "WFContentItemPropertyName" to ParamValue.Choice("latitude"))))
        assertEquals(listOf("Paris"), results(block("is.workflow.actions.location"), block("is.workflow.actions.properties.locations")))
        assertEquals(listOf("Home"), results(block("is.workflow.actions.getwifi"), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("ssid"))))
    }

    @Test
    fun eventsContactsAndSilentSms() {
        assertEquals(listOf("Dentist"), results(block("is.workflow.actions.filter.calendarevents"), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("0.title"))))
        assertEquals(listOf("+33 1 23"), results(block("is.workflow.actions.filter.contacts", "Query" to text("Ada")), block("is.workflow.actions.getvalueforkey", "WFDictionaryKey" to text("0.phone"))))
        val device = FakeDevice()
        run(listOf(block("app.daisychain.sms.send", "Recipient" to text("0612"), "Message" to text("hi"))), device = device)
        assertEquals(listOf("0612" to "hi"), device.sms)
    }

    @Test
    fun aTextBecomesAFileWhenItIsNamedAndCanBeSaved() {
        val device = FakeDevice()
        run(listOf(txt("hello file"), block("is.workflow.actions.setitemname", "Name" to text("note.txt")), block("is.workflow.actions.documentpicker.save")), device = device)
        assertEquals("hello file", String(device.written.getValue("content://saved/note.txt")))
        assertEquals(listOf("note.txt"), results(txt("x"), block("is.workflow.actions.setitemname", "Name" to text("note.txt")), block("is.workflow.actions.getitemname")))
        // a chosen file can be read back as text
        device.written["content://picked/a.txt"] = "from the phone".toByteArray()
        val ui = FakeUi()
        run(listOf(block("is.workflow.actions.documentpicker.open"), block("app.daisychain.file.read"), block(show)), device = device, ui = ui)
        assertEquals(listOf("from the phone"), ui.results)
    }

    @Test
    fun temporaryFilesAreWrittenReadListedAndDeleted() {
        val device = FakeDevice()
        val name = "Name" to text("log.txt")
        run(
            listOf(
                txt("one"), block("app.daisychain.tempfile.write", name),
                txt("-two"), block("app.daisychain.tempfile.write", name, "Mode" to ParamValue.Choice("append")),
            ),
            device = device,
        )
        assertEquals("one-two", String(device.temp.getValue("log.txt")))
        val ui = FakeUi()
        run(listOf(block("app.daisychain.tempfile.read", name), block("app.daisychain.file.read"), block(show)), device = device, ui = ui)
        assertEquals(listOf("one-two"), ui.results)
        run(listOf(block("app.daisychain.tempfile.delete", name)), device = device)
        assertTrue(device.temp.isEmpty())
    }

    private fun only(uuid: String) = ParamValue.Text(TokenString(listOf(TokenString.Part.Variable(VariableRef(VariableSource.ActionOutput(uuid))))))

    @Test
    fun filesCanBeZippedAndUnzipped() {
        val a = txt("alpha")
        val nameA = block("is.workflow.actions.setitemname", "Name" to text("a.txt"))
        val b = txt("beta")
        val nameB = block("is.workflow.actions.setitemname", "Name" to text("b.txt"))
        val list = block("is.workflow.actions.list", "Item" to only(nameA.uuid))
        var all = listOf(a, nameA, b, nameB, list)
        all = ActionOps.addRow(all, list.uuid)
        all = ActionOps.setRowParam(all, list.uuid, 0, "Item", only(nameB.uuid))
        val zip = all + block("is.workflow.actions.makezip")
        assertEquals(listOf("archive.zip"), results(*zip.toTypedArray(), block("is.workflow.actions.getitemname")))
        assertEquals(listOf("a.txt, b.txt"), results(*zip.toTypedArray(), block("is.workflow.actions.unzip"), block("is.workflow.actions.getitemname")))
    }

    private fun runWith(first: RunValue, vararg blocks: ActionInstance): List<String> {
        val ui = FakeUi()
        runBlocking { ShortcutRunner(FakeDevice(), ui, 0).run(blocks.toList() + block(show), Log(), first) }
        return ui.results
    }

    @Test
    fun smartBlocksUseThePhonesServices() {
        val image = RunValue.Image(byteArrayOf(1), "image/png")
        assertEquals(listOf("text in image"), runWith(image, block("is.workflow.actions.extracttextfromimage")))
        assertEquals(listOf("https://example.org"), runWith(image, block("is.workflow.actions.scanbarcode")))
        assertEquals(listOf("640"), runWith(image, block("is.workflow.actions.properties.images")))
        assertEquals(listOf("[fr] hello"), results(txt("hello"), block("is.workflow.actions.text.translate", "To" to ParamValue.Choice("fr"))))
        assertEquals(listOf("en"), results(txt("hello"), block("is.workflow.actions.detectlanguage")))
        assertEquals(listOf("dictated auto"), results(block("is.workflow.actions.dictatetext")))
        val ui = FakeUi()
        run(listOf(txt("https://x.org"), block("is.workflow.actions.generatebarcode"), block(show)), ui = ui)
        assertTrue(ui.image != null)
    }

    @Test
    fun webPageTextIsExtractedFromHtml() {
        val html = "<html><head><title>My page</title><style>p{}</style></head><body><h1>Hello</h1><p>One &amp; two</p><script>x()</script></body></html>"
        assertEquals("Hello\nOne & two", com.daisychain.engine.actions.Html.toText(html))
        assertEquals("My page", com.daisychain.engine.actions.Html.title(html))
    }

    @Test
    fun listsAreShownOnOneLineAndTheCopyButtonCanBeHidden() {
        val ui = FakeUi()
        run(listOf(txt("a,b,c"), block("is.workflow.actions.text.split", "WFTextSeparator" to ParamValue.Choice("Custom"), "WFTextCustomSeparator" to text(",")), block(show)), ui = ui)
        assertEquals(listOf("a, b, c"), ui.results)
        run(listOf(txt("x"), block(show, "ShowCopy" to ParamValue.Bool(false))), ui = ui)
        assertEquals(false, ui.lastAllowCopy)
    }

    @Test
    fun formatDateCustomPattern() {
        val format = block(
            "is.workflow.actions.format.date",
            "WFDateFormatStyle" to ParamValue.Choice("Custom"),
            "WFDateFormat" to text("yyyy"),
        )
        val year = results(block("is.workflow.actions.date"), format).single()
        assertTrue(year.toInt() >= 2024)
    }

    @Test
    fun repeatRunsItsBodyThatManyTimes() {
        val g = ActionOps.create(ActionRegistry.find("is.workflow.actions.repeat.count")!!)
        val start = g[0].copy(params = mapOf("WFRepeatCount" to ParamValue.Number(3.0)))
        val ui = FakeUi()
        run(listOf(block(gettext, "WFTextActionText" to text("x")), start, block(show), g[1]), ui = ui)
        assertEquals(listOf("x", "x", "x"), ui.results)
    }

    @Test
    fun menuRunsTheChosenItem() {
        val m = ActionOps.create(ActionRegistry.find("is.workflow.actions.choosefrommenu")!!)
        val a = block(gettext, "WFTextActionText" to text("A"))
        val b = block(gettext, "WFTextActionText" to text("B"))
        val list = listOf(m[0], m[1], a, block(show), m[2], b, block(show), m[3])
        val ui = FakeUi(menuChoice = 1)
        run(list, ui = ui)
        assertEquals(listOf("B"), ui.results)
    }

    @Test
    fun stopEndsTheRunQuietly() {
        val ui = FakeUi()
        run(listOf(block("is.workflow.actions.exit"), block(gettext, "WFTextActionText" to text("never")), block(show)), ui = ui)
        assertTrue(ui.results.isEmpty())
    }

    @Test
    fun unknownBlockFailsAndNamesTheBlock() {
        val bad = ActionInstance("zz", "com.unknown", emptyMap())
        try {
            run(listOf(bad))
            fail("expected a failure")
        } catch (e: RunFailure) {
            assertEquals("zz", e.uuid)
        }
    }

    @Test
    fun progressReportsEveryBlockInOrder() {
        val a = block(gettext, "WFTextActionText" to text("1"))
        val b = block(gettext, "WFTextActionText" to text("2"))
        val log = Log()
        run(listOf(a, b), log = log)
        assertEquals(listOf(a.uuid, b.uuid), log.started)
        assertEquals(listOf(a.uuid, b.uuid), log.finished)
    }
}
