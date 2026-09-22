package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Reports the device's current date, time and timezone. */
class DateTimeTool : ChatTool {
    override val name = NAME
    override val displayName = "Date & time"
    override val description =
        "Get the current date, time and timezone on the user's device. " +
            "Use this for anything involving the current time or date."

    override fun definition(): ToolDto =
        functionTool(NAME, description, emptyMap())

    override suspend fun execute(arguments: String): String {
        val now = ZonedDateTime.now()
        val friendly = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm:ss"))
        return buildString {
            append("Current local date and time: ").append(friendly).append('\n')
            append("Timezone: ").append(now.zone).append('\n')
            append("ISO 8601: ").append(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n')
            append("UTC: ").append(now.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
        }
    }

    companion object {
        const val NAME = "get_current_time"
    }
}
