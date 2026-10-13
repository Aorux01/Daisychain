package com.daisychain.engine.actions

import com.daisychain.engine.model.ContentType
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.ActionRunContext
import com.daisychain.engine.run.HttpRequest
import com.daisychain.engine.run.LocationInfo
import com.daisychain.engine.run.RunValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Category
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.LocalizedText
import com.daisychain.engine.spec.ParamSpec
import com.daisychain.engine.spec.ParamType
import com.daisychain.engine.spec.Requirement
import com.daisychain.engine.spec.t
import com.google.auto.service.AutoService
import org.json.JSONObject
import java.util.Locale

/** A position as a dictionary with the keys latitude, longitude (and accuracy, altitude when known). */
fun locationValue(info: LocationInfo): RunValue.Dict {
    val entries = linkedMapOf<String, RunValue>(
        "latitude" to RunValue.Number(info.latitude),
        "longitude" to RunValue.Number(info.longitude),
    )
    info.accuracy?.let { entries["accuracy"] = RunValue.Number(it.toDouble()) }
    info.altitude?.let { entries["altitude"] = RunValue.Number(it) }
    return RunValue.Dict(entries)
}

/** The latitude and longitude of a value that is a position (a dictionary with those keys), or null. */
fun coordinatesOf(v: RunValue): Pair<Double, Double>? {
    val dict = v as? RunValue.Dict ?: return null
    val lat = (dict.entries["latitude"] as? RunValue.Number)?.value ?: return null
    val lon = (dict.entries["longitude"] as? RunValue.Number)?.value ?: return null
    return lat to lon
}

@AutoService(ActionDefinition::class)
class CurrentLocationAction : ActionDefinition() {
    override val id = "is.workflow.actions.location"
    override val category = Category.Places
    override val title = t("Get current location", "Obtenir la position actuelle")
    override val iconKey = "location"
    override val colorKey = "blue"
    override val produces = ContentType.Location
    override val outputLabel = t("Current Location", "Position actuelle")
    override val requirements = setOf(Requirement.Location)
    override val keywords = listOf("gps", "where", "où", "coordinates", "coordonnées")

    override suspend fun run(ctx: ActionRunContext): RunValue = locationValue(ctx.device.currentLocation())
}

@AutoService(ActionDefinition::class)
class LocationDetailsAction : ActionDefinition() {
    override val id = "is.workflow.actions.properties.locations"
    override val category = Category.Places
    override val title = t("Get location details", "Obtenir les détails de la position")
    override val iconKey = "location"
    override val colorKey = "blue"
    override val input = InputSpec(t("Location", "Position"))
    override val produces = ContentType.Any
    override val outputLabel = t("Location Detail", "Détail de la position")
    override val keywords = listOf("address", "adresse", "city", "ville", "country", "pays")
    override val params = listOf(
        ParamSpec(
            "WFContentItemPropertyName", t("Detail", "Détail"),
            ParamType.Choice(
                listOf(
                    ParamType.Option("latitude", t("Latitude", "Latitude")),
                    ParamType.Option("longitude", t("Longitude", "Longitude")),
                    ParamType.Option("street", t("Street", "Rue")),
                    ParamType.Option("city", t("City", "Ville")),
                    ParamType.Option("region", t("Region", "Région")),
                    ParamType.Option("postalCode", t("Postal code", "Code postal")),
                    ParamType.Option("country", t("Country", "Pays")),
                    ParamType.Option("name", t("Place name", "Nom du lieu")),
                ),
            ),
            default = ParamValue.Choice("city"),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val (lat, lon) = coordinatesOf(ctx.input)
            ?: throw ActionFailed(t("The input is not a location", "L'entrée n'est pas une position"))
        return when (val detail = ctx.choice("WFContentItemPropertyName") ?: "city") {
            "latitude" -> RunValue.Number(lat)
            "longitude" -> RunValue.Number(lon)
            else -> RunValue.Text(ctx.device.reverseGeocode(lat, lon)[detail].orEmpty())
        }
    }
}

@AutoService(ActionDefinition::class)
class NetworkDetailsAction : ActionDefinition() {
    override val id = "is.workflow.actions.getwifi"
    override val category = Category.Device
    override val title = t("Get network details", "Obtenir les détails du réseau")
    override val iconKey = "wifi"
    override val colorKey = "teal"
    override val produces = ContentType.Dictionary
    override val outputLabel = t("Network Details", "Détails du réseau")
    override val requirements = setOf(Requirement.Location)
    override val keywords = listOf("wifi", "ssid", "signal", "connection", "connexion")

    /** A dictionary with type (wifi, cellular, none...), ssid (the Wi-Fi name), signal, speed and ip. */
    override suspend fun run(ctx: ActionRunContext): RunValue =
        RunValue.Dict(ctx.device.networkDetails().mapValues { RunValue.Text(it.value) })
}

/** Weather codes of the Open-Meteo service (the WMO codes), described in English and French. */
object WeatherCodes {
    private val names: Map<Int, LocalizedText> = buildMap {
        put(0, t("Clear sky", "Ciel dégagé"))
        put(1, t("Mostly clear", "Plutôt dégagé"))
        put(2, t("Partly cloudy", "Partiellement nuageux"))
        put(3, t("Overcast", "Couvert"))
        listOf(45, 48).forEach { put(it, t("Fog", "Brouillard")) }
        listOf(51, 53, 55).forEach { put(it, t("Drizzle", "Bruine")) }
        listOf(56, 57).forEach { put(it, t("Freezing drizzle", "Bruine verglaçante")) }
        listOf(61, 63, 65).forEach { put(it, t("Rain", "Pluie")) }
        listOf(66, 67).forEach { put(it, t("Freezing rain", "Pluie verglaçante")) }
        listOf(71, 73, 75, 77).forEach { put(it, t("Snow", "Neige")) }
        listOf(80, 81, 82).forEach { put(it, t("Rain showers", "Averses")) }
        listOf(85, 86).forEach { put(it, t("Snow showers", "Averses de neige")) }
        put(95, t("Thunderstorm", "Orage"))
        listOf(96, 99).forEach { put(it, t("Thunderstorm with hail", "Orage avec grêle")) }
    }

    fun describe(code: Int, language: String): String = names[code]?.resolve(language) ?: ""
}

@AutoService(ActionDefinition::class)
class WeatherAction : ActionDefinition() {
    override val id = "is.workflow.actions.weather.currentconditions"
    override val category = Category.Places
    override val title = t("Get weather", "Obtenir la météo")
    override val iconKey = "sun"
    override val colorKey = "amber"
    override val input = InputSpec(
        t("Location", "Position"),
        t(
            "The place to get the weather for. Automatic: the previous block if it is a location, otherwise where you are now.",
            "Le lieu dont on veut la météo. Automatique : le bloc précédent si c'est une position, sinon l'endroit où vous êtes.",
        ),
    )
    override val produces = ContentType.Dictionary
    override val outputLabel = t("Weather", "Météo")
    override val requirements = setOf(Requirement.Location, Requirement.Network)
    override val keywords = listOf("forecast", "prévisions", "temperature", "température", "rain", "pluie", "meteo")
    override val params = listOf(
        ParamSpec(
            "Unit", t("Temperature", "Température"),
            ParamType.Choice(listOf(ParamType.Option("celsius", t("Celsius (°C)", "Celsius (°C)")), ParamType.Option("fahrenheit", t("Fahrenheit (°F)", "Fahrenheit (°F)")))),
            default = ParamValue.Choice("celsius"),
            help = t(
                "The result is a dictionary: temperature, feelsLike, humidity, windSpeed, condition, min, max and a forecast list.",
                "Le résultat est un dictionnaire : temperature, feelsLike, humidity, windSpeed, condition, min, max et une liste forecast.",
            ),
        ),
    )

    override suspend fun run(ctx: ActionRunContext): RunValue {
        val (lat, lon) = coordinatesOf(ctx.input) ?: coordinatesOf(locationValue(ctx.device.currentLocation()))!!
        val fahrenheit = ctx.choice("Unit") == "fahrenheit"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=7" +
            if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val body = String(ctx.device.httpRequest(HttpRequest("GET", url)).bytes, Charsets.UTF_8)
        return parse(body, Locale.getDefault().language)
    }

    companion object {
        fun parse(json: String, language: String): RunValue.Dict {
            val root = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw ActionFailed(t("The weather service sent something unexpected", "Le service météo a envoyé une réponse inattendue"))
            }
            val current = root.optJSONObject("current")
                ?: throw ActionFailed(t("No weather available for this place", "Pas de météo disponible pour ce lieu"))
            val daily = root.optJSONObject("daily")
            val days = daily?.optJSONArray("time")?.length() ?: 0
            val forecast = (0 until days).map { i ->
                RunValue.Dict(
                    mapOf(
                        "date" to RunValue.Text(daily!!.getJSONArray("time").getString(i)),
                        "min" to RunValue.Number(daily.getJSONArray("temperature_2m_min").getDouble(i)),
                        "max" to RunValue.Number(daily.getJSONArray("temperature_2m_max").getDouble(i)),
                        "condition" to RunValue.Text(WeatherCodes.describe(daily.getJSONArray("weather_code").getInt(i), language)),
                    ),
                )
            }
            return RunValue.Dict(
                linkedMapOf(
                    "temperature" to RunValue.Number(current.getDouble("temperature_2m")),
                    "feelsLike" to RunValue.Number(current.getDouble("apparent_temperature")),
                    "humidity" to RunValue.Number(current.getDouble("relative_humidity_2m")),
                    "windSpeed" to RunValue.Number(current.getDouble("wind_speed_10m")),
                    "condition" to RunValue.Text(WeatherCodes.describe(current.getInt("weather_code"), language)),
                    "min" to (forecast.firstOrNull()?.entries?.get("min") ?: RunValue.Nothing),
                    "max" to (forecast.firstOrNull()?.entries?.get("max") ?: RunValue.Nothing),
                    "forecast" to RunValue.Items(forecast),
                ),
            )
        }
    }
}
