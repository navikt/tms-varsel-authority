package no.nav.tms.varsel.authority.common

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Query
import kotliquery.Session
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.sessionOf
import kotliquery.using
import org.postgresql.util.PSQLState
import java.sql.SQLException

interface Database {

    val dataSource: HikariDataSource

    fun insert(queryBuilder: () -> Query) = try {
        using(sessionOf(dataSource)) {
            it.run(queryBuilder.invoke().asUpdate)
        }
    } catch (e: SQLException) {
        if (e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
            throw UniqueConstraintException()
        } else {
            throw e
        }
    }

    fun update(queryBuilder: (Session) -> Query) {
        using(sessionOf(dataSource)) {
            it.run(queryBuilder.invoke(it).asUpdate)
        }
    }

    fun <T> singleOrNull(action: () -> NullableResultQueryAction<T>): T? =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        }

    fun <T> list(action: () -> ListResultQueryAction<T>): List<T> =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        }

    fun batch(statement: String, params: List<Map<String, Any?>>) =
        using(sessionOf(dataSource)) {
            it.batchPreparedNamedStatement(statement, params)
        }
}

class UniqueConstraintException(): RuntimeException()
