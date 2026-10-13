package com.daisychain.engine

import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.model.FlowMembership
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.model.TokenString
import com.daisychain.engine.model.VariableRef
import com.daisychain.engine.model.VariableSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionsJsonTest {
    private val sample = listOf(
        ActionInstance("a1", "is.workflow.actions.getbatterylevel", outputName = "Battery"),
        ActionInstance(
            "a2", "is.workflow.actions.conditional",
            params = mapOf(
                "WFCondition" to ParamValue.Choice("0"),
                "WFNumberValue" to ParamValue.Number(20.0),
                "WFInput" to ParamValue.Variable(VariableRef(VariableSource.ActionOutput("a1"))),
            ),
            flow = FlowMembership("g1", 0),
        ),
        ActionInstance(
            "a3", "is.workflow.actions.showresult",
            params = mapOf(
                "Text" to ParamValue.Text(
                    TokenString(
                        listOf(
                            TokenString.Part.Literal("Level: "),
                            TokenString.Part.Variable(VariableRef(VariableSource.Named("level"))),
                        ),
                    ),
                ),
                "Extra" to ParamValue.Raw(buildJsonObject { put("k", "v") }),
            ),
        ),
        ActionInstance("a4", "com.unknown.plugin", params = mapOf("Flag" to ParamValue.Bool(true))),
    )

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(sample, ActionsJson.decode(ActionsJson.encode(sample)))
    }

    @Test
    fun unreadableTextGivesEmptyList() {
        assertEquals(emptyList<ActionInstance>(), ActionsJson.decode("not json"))
    }

    @Test
    fun emptyListIsStoredAsEmptyArray() {
        assertEquals("[]", ActionsJson.encode(emptyList()))
    }
}
