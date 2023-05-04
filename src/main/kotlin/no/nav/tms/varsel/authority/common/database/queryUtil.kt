package no.nav.tms.varsel.authority.common.database

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import org.postgresql.util.PGobject
import java.sql.Array
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.LocalDateTime


inline fun <reified T> Row.json(label: String, objectMapper: ObjectMapper = defaultObjectMapper()): T {
    return objectMapper.readValue(string(label))
}

inline fun <reified T> Row.optionalJson(label: String, objectMapper: ObjectMapper = defaultObjectMapper()): T? {
    val jsonString = stringOrNull(label)

    return jsonString?.let { objectMapper.readValue(jsonString) }
}

fun Any?.toJsonb(objectMapper: ObjectMapper = defaultObjectMapper()): PGobject? {
    return if (this == null) {
        null
    } else {
        objectMapper.writeValueAsString(this).let {
            PGobject().apply {
                type = "jsonb"
                value = it
            }
        }
    }
}

private inline fun <reified T> Row.deserialized(label: String, objectMapper: ObjectMapper = defaultObjectMapper()): T {
    return string(label).let { objectMapper.readValue(it) }
}

fun ResultSet.getUtcDateTime(columnLabel: String): LocalDateTime = getTimestamp(columnLabel).toLocalDateTime()

fun ResultSet.getNullableLocalDateTime(label: String): LocalDateTime? {
    return getTimestamp(label)?.toLocalDateTime()
}

fun Connection.executeBatchUpdateQuery(sql: String, paramInit: PreparedStatement.() -> Unit) {
    autoCommit = false
    prepareStatement(sql).use { statement ->
        statement.paramInit()
        statement.executeBatch()
    }
    commit()
}

fun Connection.toVarcharArray(stringList: List<String>): Array {
    return createArrayOf("VARCHAR", stringList.toTypedArray())
}

fun Connection.executePersistQuery(sql: String, paramInit: PreparedStatement.() -> Unit): PersistActionResult =
        prepareStatement("""$sql ON CONFLICT DO NOTHING""", Statement.RETURN_GENERATED_KEYS).use {

            it.paramInit()
            it.executeUpdate()

            if (it.generatedKeys.next()) {
                PersistActionResult.success(it.generatedKeys.getInt("id"))
            } else {
                PersistActionResult.failure(PersistOutcome.NO_INSERT_OR_UPDATE)
            }
        }

fun ResultSet.getListFromString(columnLabel: String, separator: String = ","): List<String> {
    val stringValue = getString(columnLabel)
    return if(stringValue.isNullOrEmpty()) {
        emptyList()
    }
    else {
        stringValue.split(separator)
    }
}
