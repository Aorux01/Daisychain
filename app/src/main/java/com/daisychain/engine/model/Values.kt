package com.daisychain.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Where a variable's value comes from. */
@Serializable
sealed interface VariableSource {
    /** A variable created with "Set variable". */
    @Serializable @SerialName("named")
    data class Named(val name: String) : VariableSource

    /** The output of a previous block (a "magic variable"). */
    @Serializable @SerialName("output")
    data class ActionOutput(val actionUuid: String) : VariableSource

    @Serializable @SerialName("input") data object ShortcutInput : VariableSource
    @Serializable @SerialName("clipboard") data object Clipboard : VariableSource
    @Serializable @SerialName("date") data object CurrentDate : VariableSource
    @Serializable @SerialName("ask") data object AskWhenRun : VariableSource

    /** Nothing at all: used to create a variable that starts empty. */
    @Serializable @SerialName("empty") data object Empty : VariableSource

    /** The current item / position (starting at 1) inside "Repeat with each" and "Repeat". */
    @Serializable @SerialName("repeatItem") data object RepeatItem : VariableSource
    @Serializable @SerialName("repeatIndex") data object RepeatIndex : VariableSource

    /** Device details, e.g. battery or model; [key] is defined by the action that reads it. */
    @Serializable @SerialName("device")
    data class Device(val key: String) : VariableSource
}

/** A variable reference, optionally followed by property accesses ("Name", dictionary key, ...). */
@Serializable
data class VariableRef(val source: VariableSource, val path: List<Accessor> = emptyList())

/** A step applied to a variable's value: read a property, a dictionary key, convert the type... */
@Serializable
data class Accessor(val kind: String, val argument: String? = null)

/** A string that can contain variables inline ("Battery is {Battery Level}%"). */
@Serializable
data class TokenString(val parts: List<Part>) {
    @Serializable
    sealed interface Part {
        @Serializable @SerialName("text")
        data class Literal(val text: String) : Part

        @Serializable @SerialName("var")
        data class Variable(val ref: VariableRef) : Part
    }

    companion object {
        fun of(text: String) = TokenString(if (text.isEmpty()) emptyList() else listOf(Part.Literal(text)))
    }
}

/** The value of one parameter of a block. */
@Serializable
sealed interface ParamValue {
    @Serializable @SerialName("text")
    data class Text(val value: TokenString) : ParamValue

    @Serializable @SerialName("number")
    data class Number(val value: Double) : ParamValue

    @Serializable @SerialName("bool")
    data class Bool(val value: Boolean) : ParamValue

    /** The parameter is a variable as a whole, not text containing variables. */
    @Serializable @SerialName("variable")
    data class Variable(val ref: VariableRef) : ParamValue

    @Serializable @SerialName("choice")
    data class Choice(val optionId: String) : ParamValue

    @Serializable @SerialName("dict")
    data class Dict(val entries: Map<String, ParamValue>) : ParamValue

    @Serializable @SerialName("array")
    data class Array(val items: kotlin.collections.List<ParamValue>) : ParamValue

    /** Anything we do not understand yet (the plist converted to JSON), kept verbatim. */
    @Serializable @SerialName("raw")
    data class Raw(val data: JsonElement) : ParamValue
}

/** Types of data flowing between blocks, used to filter the variable picker and check compatibility. */
enum class ContentType { Any, Text, Number, Boolean, Date, List, Dictionary, File, Image, Url, Location, Contact }
