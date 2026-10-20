package me.rerere.rikkahub.web.routes

import me.rerere.rikkahub.web.BadRequestException
import kotlin.uuid.Uuid

internal fun String?.toUuid(name: String = "id"): Uuid {
    if (this == null) throw BadRequestException("Missing $name")
    return runCatching { Uuid.parse(this) }.getOrNull()
        ?: throw BadRequestException("Invalid $name")
}
