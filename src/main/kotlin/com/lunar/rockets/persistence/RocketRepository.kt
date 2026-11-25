package com.lunar.rockets.persistence

import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketQuery
import com.lunar.rockets.domain.RocketSortField
import com.lunar.rockets.domain.RocketStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class RocketRepository(private val jdbc: JdbcClient) {

    /**
     * Creates the rocket row as `PENDING` if it does not exist yet, then locks it (`FOR UPDATE`) until the end
     * of the current transaction. See "Why the lock" in README.
     *
     * Requires an active transaction ([Propagation.MANDATORY]): without one, each statement would auto-commit
     * and the lock would be released immediately — silently. Throws `IllegalTransactionStateException` instead.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockOrCreate(channel: UUID): Rocket {
        jdbc.sql(
            """
            INSERT INTO rockets (channel, status) VALUES (:channel, :status)
            ON CONFLICT (channel) DO NOTHING
            """,
        )
            .param("channel", channel)
            .param("status", RocketStatus.PENDING.name)
            .update()

        return jdbc.sql("SELECT * FROM rockets WHERE channel = :channel FOR UPDATE")
            .param("channel", channel)
            .query(ROCKET_MAPPER)
            .single()
    }

    /**
     * Plain read without a lock; returns rockets in any status, including `PENDING`.
     */
    fun findByChannel(channel: UUID): Rocket? =
        jdbc.sql("SELECT * FROM rockets WHERE channel = :channel")
            .param("channel", channel)
            .query(ROCKET_MAPPER)
            .optional()
            .orElse(null)

    /**
     * Rockets visible in the API (not `PENDING`), filtered and sorted by [query], with `channel` as a tie-breaker.
     *
     * Only fixed SQL fragments are concatenated; filter values are bound as parameters
     * and the sort column comes from [sortColumn], never from client input.
     */
    fun findAllVisible(query: RocketQuery): List<Rocket> {
        val conditions = mutableListOf("status <> :pending")
        val params = mutableMapOf<String, Any>("pending" to RocketStatus.PENDING.name)
        query.status?.let {
            conditions += "status = :status"
            params["status"] = it.name
        }
        query.minSpeed?.let {
            conditions += "speed >= :minSpeed"
            params["minSpeed"] = it
        }
        query.maxSpeed?.let {
            conditions += "speed <= :maxSpeed"
            params["maxSpeed"] = it
        }
        val sql = """
            SELECT * FROM rockets
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY ${sortColumn(query.sort)} ${query.order.name}, channel
        """
        return jdbc.sql(sql).params(params).query(ROCKET_MAPPER).list()
    }

    private fun sortColumn(field: RocketSortField): String = when (field) {
        RocketSortField.SPEED -> "speed"
        RocketSortField.TYPE -> "type"
        RocketSortField.MISSION -> "mission"
        RocketSortField.STATUS -> "status"
        RocketSortField.LAUNCHED_AT -> "launched_at"
    }

    /**
     * Writes the full state of [rocket] (not a delta), i.e. the second half of read → compute → write.
     * Only correct in the same transaction that locked the row via [lockOrCreate], hence [Propagation.MANDATORY].
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun update(rocket: Rocket) {
        val updated = jdbc.sql(
            """
            UPDATE rockets
            SET status = :status,
                type = :type,
                mission = :mission,
                speed = :speed,
                explosion_reason = :explosionReason,
                launched_at = :launchedAt,
                updated_at = :updatedAt,
                last_applied_message_number = :lastAppliedMessageNumber
            WHERE channel = :channel
            """,
        )
            .param("channel", rocket.channel)
            .param("status", rocket.status.name)
            .param("type", rocket.type)
            .param("mission", rocket.mission)
            .param("speed", rocket.speed)
            .param("explosionReason", rocket.explosionReason)
            .param("launchedAt", rocket.launchedAt?.toUtcOffsetDateTime())
            .param("updatedAt", rocket.updatedAt?.toUtcOffsetDateTime())
            .param("lastAppliedMessageNumber", rocket.lastAppliedMessageNumber)
            .update()
        check(updated == 1) { "Rocket ${rocket.channel} does not exist" }
    }

    private companion object {
        val ROCKET_MAPPER = RowMapper { rs, _ ->
            Rocket(
                channel = rs.getObject("channel", UUID::class.java),
                status = RocketStatus.valueOf(rs.getString("status")),
                type = rs.getString("type"),
                mission = rs.getString("mission"),
                speed = rs.getLong("speed").takeUnless { rs.wasNull() },
                explosionReason = rs.getString("explosion_reason"),
                launchedAt = rs.getInstant("launched_at"),
                updatedAt = rs.getInstant("updated_at"),
                lastAppliedMessageNumber = rs.getLong("last_applied_message_number"),
            )
        }
    }
}
