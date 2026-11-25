package com.lunar.rockets.persistence

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun Instant.toUtcOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)

internal fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()
