package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.ShowWhen
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private val notANumber = t("The input is not a number", "L'entrée n'est pas un nombre")

private fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun asNumber(v: RunValue): Double? = when (v) {
    is RunValue.Number -> v.value
    is RunValue.Text -> parseNumber(v.value)
    else -> null
}

/** Every number found in a value: a list's numbers, or the numbers written in a text. */
private fun numbersIn(v: RunValue): List<Double> = when (v) {
    is RunValue.Items -> v.values.flatMap { numbersIn(it) }
    is RunValue.Number -> listOf(v.value)
    is RunValue.Nothing -> emptyList()
    else -> v.display().split(Regex("[\\s,;]+")).mapNotNull { it.toDoubleOrNull() }
}

object CalculateOps {
    val arithmetic = setOf("+", "-", "×", "÷", "%", "^")
    val rounding = setOf("round", "ceil", "floor")

    val options = listOf(
        ParamType.Option("+", t("+  add", "+  ajouter")),
        ParamType.Option("-", t("−  subtract", "−  soustraire")),
        ParamType.Option("×", t("×  multiply", "×  multiplier")),
        ParamType.Option("÷", t("÷  divide", "÷  diviser")),
        ParamType.Option("%", t("remainder of division", "reste de la division")),
        ParamType.Option("^", t("power", "puissance")),
        ParamType.Option("sqrt", t("square root", "racine carrée")),
        ParamType.Option("abs", t("absolute value", "valeur absolue")),
        ParamType.Option("square", t("squared", "au carré")),
        ParamType.Option("cube", t("cubed", "au cube")),
        ParamType.Option("ln", t("natural logarithm (ln)", "logarithme népérien (ln)")),
        ParamType.Option("log10", t("logarithm base 10", "logarithme décimal")),
        ParamType.Option("sin", t("sine (degrees)", "sinus (degrés)")),
        ParamType.Option("cos", t("cosine (degrees)", "cosinus (degrés)")),
        ParamType.Option("tan", t("tangent (degrees)", "tangente (degrés)")),
        ParamType.Option("round", t("round", "arrondir")),
        ParamType.Option("ceil", t("round up", "arrondir au-dessus")),
        ParamType.Option("floor", t("round down", "arrondir en dessous")),
        ParamType.Option("count", t("number of items", "nombre d'éléments")),
        ParamType.Option("chars", t("number of characters", "nombre de caractères")),
        ParamType.Option("words", t("number of words", "nombre de mots")),
        ParamType.Option("lines", t("number of lines", "nombre de lignes")),
        ParamType.Option("sum", t("sum", "somme")),
        ParamType.Option("average", t("average", "moyenne")),
        ParamType.Option("min", t("minimum", "minimum")),
        ParamType.Option("max", t("maximum", "maximum")),
        ParamType.Option("median", t("median", "médiane")),
    )
}

/** All the maths in one block: operations, functions, rounding and statistics on a list. */
@AutoService(ActionDefinition::class)
class CalculateAction : ActionDefinition() {
    override val id = "is.workflow.actions.math"
    override val category = Category.Numbers
    override val title = t("Calculate", "Calculer")
    override val iconKey = "calc"
    override val colorKey = "teal"
    override val accepts = setOf(ContentType.Number, ContentType.List)
    override val produces = ContentType.Number
    override val outputLabel = t("Calculation Result", "Résultat du calcul")
    override val keywords = listOf(
        "math", "add", "plus", "moins", "multiply", "divide", "round", "arrondir", "count", "compter",
        "average", "moyenne", "sum", "somme", "sqrt", "racine", "power", "puissance",
    )

    /** The number to work on; left empty, it is the previous block's output (a number, or a list for statistics). */
    override val params = listOf(
        ParamSpec("WFInput", t("Number", "Nombre"), ParamType.Number(), fallsBackToInput = true),
        ParamSpec("WFMathOperation", t("Operation", "Opération"), ParamType.Choice(CalculateOps.options), default = ParamValue.Choice("+")),
        ParamSpec(
            "WFMathOperand", t("Number", "Nombre"), ParamType.Number(), default = ParamValue.Number(1.0),
            showWhen = ShowWhen("WFMathOperation", CalculateOps.arithmetic),
        ),
        ParamSpec(
            "Decimals", t("Decimals", "Décimales"), ParamType.Number(min = 0.0, max = 10.0), default = ParamValue.Number(0.0),
            showWhen = ShowWhen("WFMathOperation", CalculateOps.rounding),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val op = ctx.choice("WFMathOperation") ?: "+"
        val input = ctx.valueOrInput("WFInput")

        // Counting and statistics work on lists and texts.
        when (op) {
            "count" -> return RunValue.Number(
                when (input) {
                    is RunValue.Items -> input.values.size
                    is RunValue.Dict -> input.entries.size
                    RunValue.Nothing -> 0
                    else -> 1
                }.toDouble(),
            )
            "chars" -> return RunValue.Number(input.display().let { it.codePointCount(0, it.length) }.toDouble())
            "words" -> return RunValue.Number(input.display().split(Regex("\\s+")).count { it.isNotEmpty() }.toDouble())
            "lines" -> return RunValue.Number(input.display().let { if (it.isEmpty()) 0 else it.lines().size }.toDouble())
            "sum", "average", "min", "max", "median" -> {
                val numbers = numbersIn(input)
                if (numbers.isEmpty()) throw ActionFailed(t("There are no numbers to calculate with", "Il n'y a aucun nombre pour calculer"))
                return RunValue.Number(
                    when (op) {
                        "sum" -> numbers.sum()
                        "average" -> numbers.average()
                        "min" -> numbers.min()
                        "max" -> numbers.max()
                        else -> numbers.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
                    },
                )
            }
        }

        val x = asNumber(input) ?: throw ActionFailed(notANumber)
        val result = when (op) {
            "-" -> x - operand(ctx)
            "×" -> x * operand(ctx)
            "÷" -> {
                val d = operand(ctx)
                if (d == 0.0) throw ActionFailed(t("Cannot divide by zero", "Division par zéro impossible"))
                x / d
            }
            "%" -> {
                val d = operand(ctx)
                if (d == 0.0) throw ActionFailed(t("Cannot divide by zero", "Division par zéro impossible"))
                x % d
            }
            "^" -> Math.pow(x, operand(ctx))
            "sqrt" -> if (x < 0) throw ActionFailed(t("No square root of a negative number", "Pas de racine carrée d'un nombre négatif")) else Math.sqrt(x)
            "abs" -> Math.abs(x)
            "square" -> x * x
            "cube" -> x * x * x
            "ln" -> if (x <= 0) throw ActionFailed(t("The logarithm needs a number above 0", "Le logarithme demande un nombre supérieur à 0")) else Math.log(x)
            "log10" -> if (x <= 0) throw ActionFailed(t("The logarithm needs a number above 0", "Le logarithme demande un nombre supérieur à 0")) else Math.log10(x)
            "sin" -> Math.sin(Math.toRadians(x))
            "cos" -> Math.cos(Math.toRadians(x))
            "tan" -> Math.tan(Math.toRadians(x))
            "round", "ceil", "floor" -> {
                val mode = when (op) {
                    "ceil" -> RoundingMode.CEILING
                    "floor" -> RoundingMode.FLOOR
                    else -> RoundingMode.HALF_UP
                }
                BigDecimal.valueOf(x).setScale((ctx.number("Decimals") ?: 0.0).toInt().coerceIn(0, 10), mode).toDouble()
            }
            else -> x + operand(ctx)
        }
        return RunValue.Number(result)
    }

    private suspend fun operand(ctx: ActionRunContext): Double =
        ctx.number("WFMathOperand") ?: throw ActionFailed(notANumber)
}

private class MeasureUnit(val id: String, val label: LocalizedText, val factor: Double)

private class Dimension(val id: String, val label: LocalizedText, val units: List<MeasureUnit>)

private val dimensions = listOf(
    Dimension(
        "length", t("Length", "Longueur"),
        listOf(
            MeasureUnit("mm", t("Millimeters (mm)", "Millimètres (mm)"), 0.001),
            MeasureUnit("cm", t("Centimeters (cm)", "Centimètres (cm)"), 0.01),
            MeasureUnit("m", t("Meters (m)", "Mètres (m)"), 1.0),
            MeasureUnit("km", t("Kilometers (km)", "Kilomètres (km)"), 1000.0),
            MeasureUnit("in", t("Inches (in)", "Pouces (in)"), 0.0254),
            MeasureUnit("ft", t("Feet (ft)", "Pieds (ft)"), 0.3048),
            MeasureUnit("yd", t("Yards (yd)", "Yards (yd)"), 0.9144),
            MeasureUnit("mi", t("Miles (mi)", "Miles (mi)"), 1609.344),
        ),
    ),
    Dimension(
        "mass", t("Mass", "Masse"),
        listOf(
            MeasureUnit("mg", t("Milligrams (mg)", "Milligrammes (mg)"), 0.000001),
            MeasureUnit("g", t("Grams (g)", "Grammes (g)"), 0.001),
            MeasureUnit("kg", t("Kilograms (kg)", "Kilogrammes (kg)"), 1.0),
            MeasureUnit("t", t("Metric tons (t)", "Tonnes (t)"), 1000.0),
            MeasureUnit("oz", t("Ounces (oz)", "Onces (oz)"), 0.028349523125),
            MeasureUnit("lb", t("Pounds (lb)", "Livres (lb)"), 0.45359237),
        ),
    ),
    Dimension(
        "temperature", t("Temperature", "Température"),
        listOf(
            MeasureUnit("c", t("Celsius (°C)", "Celsius (°C)"), 1.0),
            MeasureUnit("f", t("Fahrenheit (°F)", "Fahrenheit (°F)"), 1.0),
            MeasureUnit("k", t("Kelvin (K)", "Kelvin (K)"), 1.0),
        ),
    ),
    Dimension(
        "volume", t("Volume", "Volume"),
        listOf(
            MeasureUnit("ml", t("Milliliters (ml)", "Millilitres (ml)"), 0.001),
            MeasureUnit("cl", t("Centiliters (cl)", "Centilitres (cl)"), 0.01),
            MeasureUnit("l", t("Liters (l)", "Litres (l)"), 1.0),
            MeasureUnit("m3", t("Cubic meters (m³)", "Mètres cubes (m³)"), 1000.0),
            MeasureUnit("floz", t("US fluid ounces", "Onces liquides US"), 0.0295735295625),
            MeasureUnit("cup", t("US cups", "Tasses US"), 0.2365882365),
            MeasureUnit("gal", t("US gallons", "Gallons US"), 3.785411784),
        ),
    ),
    Dimension(
        "speed", t("Speed", "Vitesse"),
        listOf(
            MeasureUnit("ms", t("Meters per second (m/s)", "Mètres par seconde (m/s)"), 1.0),
            MeasureUnit("kmh", t("Kilometers per hour (km/h)", "Kilomètres par heure (km/h)"), 1.0 / 3.6),
            MeasureUnit("mph", t("Miles per hour (mph)", "Miles par heure (mph)"), 0.44704),
            MeasureUnit("kn", t("Knots (kn)", "Nœuds (kn)"), 1852.0 / 3600.0),
        ),
    ),
    Dimension(
        "duration", t("Duration", "Durée"),
        listOf(
            MeasureUnit("msec", t("Milliseconds", "Millisecondes"), 0.001),
            MeasureUnit("s", t("Seconds", "Secondes"), 1.0),
            MeasureUnit("min", t("Minutes", "Minutes"), 60.0),
            MeasureUnit("h", t("Hours", "Heures"), 3600.0),
            MeasureUnit("day", t("Days", "Jours"), 86400.0),
            MeasureUnit("week", t("Weeks", "Semaines"), 604800.0),
        ),
    ),
    Dimension(
        "area", t("Area", "Surface"),
        listOf(
            MeasureUnit("m2", t("Square meters (m²)", "Mètres carrés (m²)"), 1.0),
            MeasureUnit("km2", t("Square kilometers (km²)", "Kilomètres carrés (km²)"), 1_000_000.0),
            MeasureUnit("ha", t("Hectares (ha)", "Hectares (ha)"), 10_000.0),
            MeasureUnit("ft2", t("Square feet (ft²)", "Pieds carrés (ft²)"), 0.09290304),
            MeasureUnit("acre", t("Acres", "Acres"), 4046.8564224),
        ),
    ),
    Dimension(
        "energy", t("Energy", "Énergie"),
        listOf(
            MeasureUnit("j", t("Joules (J)", "Joules (J)"), 1.0),
            MeasureUnit("kj", t("Kilojoules (kJ)", "Kilojoules (kJ)"), 1000.0),
            MeasureUnit("cal", t("Calories (cal)", "Calories (cal)"), 4.184),
            MeasureUnit("kcal", t("Kilocalories (kcal)", "Kilocalories (kcal)"), 4184.0),
            MeasureUnit("wh", t("Watt-hours (Wh)", "Wattheures (Wh)"), 3600.0),
            MeasureUnit("kwh", t("Kilowatt-hours (kWh)", "Kilowattheures (kWh)"), 3_600_000.0),
        ),
    ),
    Dimension(
        "data", t("Data", "Données"),
        listOf(
            MeasureUnit("bit", t("Bits", "Bits"), 0.125),
            MeasureUnit("b", t("Bytes (B)", "Octets (o)"), 1.0),
            MeasureUnit("kb", t("Kilobytes (KB)", "Kilooctets (Ko)"), 1000.0),
            MeasureUnit("mb", t("Megabytes (MB)", "Mégaoctets (Mo)"), 1_000_000.0),
            MeasureUnit("gb", t("Gigabytes (GB)", "Gigaoctets (Go)"), 1_000_000_000.0),
            MeasureUnit("tb", t("Terabytes (TB)", "Téraoctets (To)"), 1_000_000_000_000.0),
        ),
    ),
)

object Measures {
    fun convert(category: String, from: String, to: String, value: Double): Double {
        if (category == "temperature") {
            val celsius = when (from) {
                "f" -> (value - 32) * 5 / 9
                "k" -> value - 273.15
                else -> value
            }
            return when (to) {
                "f" -> celsius * 9 / 5 + 32
                "k" -> celsius + 273.15
                else -> celsius
            }
        }
        val dimension = dimensions.firstOrNull { it.id == category } ?: return value
        val a = dimension.units.firstOrNull { it.id == from } ?: return value
        val b = dimension.units.firstOrNull { it.id == to } ?: return value
        return value * a.factor / b.factor
    }
}

/** Converts a measurement between units, a number between bases, or text to and from character codes. */
@AutoService(ActionDefinition::class)
class ConvertAction : ActionDefinition() {
    override val id = "is.workflow.actions.measurement.convert"
    override val category = Category.Numbers
    override val title = t("Convert", "Convertir")
    override val iconKey = "swap"
    override val colorKey = "teal"
    override val accepts = setOf(ContentType.Number, ContentType.Text)
    override val produces = ContentType.Any
    override val outputLabel = t("Converted Value", "Valeur convertie")
    override val keywords = listOf(
        "unit", "unité", "measure", "mesure", "temperature", "km", "miles", "binary", "binaire", "hex",
        "unicode", "ascii", "code", "base",
    )

    private fun inMode(vararg modes: String) = ShowWhen("Mode", modes.toSet())

    override val params: List<ParamSpec> = buildList {
        add(ParamSpec("WFInput", t("Value", "Valeur"), ParamType.Text(), fallsBackToInput = true))
        add(
            ParamSpec(
                "Mode", t("Convert", "Convertir"),
                ParamType.Choice(
                    listOf(
                        ParamType.Option("measure", t("a measurement", "une mesure")),
                        ParamType.Option("base", t("a number base", "la base d'un nombre")),
                        ParamType.Option("toCodes", t("text to character codes", "texte en codes de caractères")),
                        ParamType.Option("fromCodes", t("character codes to text", "codes de caractères en texte")),
                    ),
                ),
                default = ParamValue.Choice("measure"),
            ),
        )
        add(
            ParamSpec(
                "Category", t("Kind", "Type"),
                ParamType.Choice(dimensions.map { ParamType.Option(it.id, it.label) }),
                default = ParamValue.Choice("length"), showWhen = inMode("measure"),
            ),
        )
        dimensions.forEach { d ->
            val shown = ShowWhen("Category", setOf(d.id), also = inMode("measure"))
            val options = d.units.map { ParamType.Option(it.id, it.label) }
            add(ParamSpec("From.${d.id}", t("From", "De"), ParamType.Choice(options), default = ParamValue.Choice(d.units[0].id), showWhen = shown))
            add(ParamSpec("To.${d.id}", t("To", "Vers"), ParamType.Choice(options), default = ParamValue.Choice(d.units[1].id), showWhen = shown))
        }
        add(ParamSpec("FromBase", t("From base", "De la base"), ParamType.Number(min = 2.0, max = 36.0), default = ParamValue.Number(10.0), showWhen = inMode("base")))
        add(ParamSpec("ToBase", t("To base", "Vers la base"), ParamType.Number(min = 2.0, max = 36.0), default = ParamValue.Number(2.0), showWhen = inMode("base")))
    }

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val input = ctx.valueOrInput("WFInput")
        return when (ctx.choice("Mode") ?: "measure") {
            "base" -> {
                val from = (ctx.number("FromBase") ?: 10.0).toInt().coerceIn(2, 36)
                val to = (ctx.number("ToBase") ?: 2.0).toInt().coerceIn(2, 36)
                val raw = input.display().trim().replace(" ", "").removePrefix("0x").removePrefix("0b")
                val value = try {
                    BigInteger(raw.removeSuffix(".0"), from)
                } catch (e: NumberFormatException) {
                    throw ActionFailed(t("\"$raw\" is not a base $from number", "« $raw » n'est pas un nombre en base $from"))
                }
                RunValue.Text(value.toString(to).uppercase())
            }
            "toCodes" -> {
                val text = input.display()
                val codes = mutableListOf<RunValue>()
                var i = 0
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    codes += RunValue.Number(cp.toDouble())
                    i += Character.charCount(cp)
                }
                RunValue.Items(codes)
            }
            "fromCodes" -> {
                val codes = numbersIn(input).map { it.toInt() }
                if (codes.any { it !in 0..0x10FFFF }) throw ActionFailed(t("Not a valid character code", "Code de caractère invalide"))
                RunValue.Text(buildString { codes.forEach { appendCodePoint(it) } })
            }
            else -> {
                val value = asNumber(input) ?: throw ActionFailed(notANumber)
                val category = ctx.choice("Category") ?: "length"
                val dimension = dimensions.first { it.id == category }
                val from = ctx.choice("From.$category") ?: dimension.units[0].id
                val to = ctx.choice("To.$category") ?: dimension.units[1].id
                RunValue.Number(Measures.convert(category, from, to, value))
            }
        }
    }
}
