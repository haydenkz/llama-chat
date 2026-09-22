package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/** Current weather via Open-Meteo (no API key). */
class WeatherTool(
    private val client: OkHttpClient,
    private val json: Json,
) : ChatTool {
    override val name = NAME
    override val displayName = "Weather"
    override val description =
        "Get the current weather for a city or place. Use this for any weather question."

    override fun definition(): ToolDto =
        functionTool(NAME, description, mapOf("location" to "The city or place name, e.g. \"London\"."))

    override suspend fun execute(arguments: String): String {
        val location = toolStringArg(arguments, "location", "city", "query")
            ?: return "No location provided."

        val geo = client.getText(
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?count=1&language=en&format=json&name=${urlEncode(location)}",
        )
        val place = runCatching {
            json.parseToJsonElement(geo).jsonObject["results"]?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return "Could not find a location named \"$location\"."

        val latitude = place["latitude"]?.jsonPrimitive?.doubleOrNull
            ?: return "Could not resolve \"$location\"."
        val longitude = place["longitude"]?.jsonPrimitive?.doubleOrNull
            ?: return "Could not resolve \"$location\"."
        val name = listOfNotNull(
            place["name"]?.jsonPrimitive?.contentOrNull,
            place["admin1"]?.jsonPrimitive?.contentOrNull,
            place["country"]?.jsonPrimitive?.contentOrNull,
        ).joinToString(", ")

        val forecast = client.getText(
            "https://api.open-meteo.com/v1/forecast" +
                "?current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m" +
                "&timezone=auto&latitude=$latitude&longitude=$longitude",
        )
        val current = runCatching {
            json.parseToJsonElement(forecast).jsonObject["current"]?.jsonObject
        }.getOrNull() ?: return "No weather data available for $name."

        fun number(key: String) = current[key]?.jsonPrimitive?.doubleOrNull
        fun text(key: String) = current[key]?.jsonPrimitive?.contentOrNull

        return buildString {
            append("Current weather in ").append(name).append(":\n")
            append("Condition: ").append(weatherText(text("weather_code")?.toIntOrNull() ?: 0)).append('\n')
            number("temperature_2m")?.let { append("Temperature: ").append(trim(it)).append(" °C\n") }
            number("apparent_temperature")?.let { append("Feels like: ").append(trim(it)).append(" °C\n") }
            number("relative_humidity_2m")?.let { append("Humidity: ").append(trim(it)).append(" %\n") }
            number("wind_speed_10m")?.let { append("Wind: ").append(trim(it)).append(" km/h\n") }
            number("precipitation")?.let { append("Precipitation: ").append(trim(it)).append(" mm\n") }
            text("time")?.let { append("Observed: ").append(it).append(" (local)") }
        }.trim()
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

    private fun weatherText(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Depositing rime fog"
        51 -> "Light drizzle"
        53 -> "Moderate drizzle"
        55 -> "Dense drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Slight rain"
        63 -> "Moderate rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Slight snow"
        73 -> "Moderate snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Slight rain showers"
        81 -> "Moderate rain showers"
        82 -> "Violent rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown conditions ($code)"
    }

    companion object {
        const val NAME = "get_weather"
    }
}
