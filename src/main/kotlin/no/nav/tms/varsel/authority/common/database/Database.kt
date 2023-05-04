package no.nav.tms.varsel.authority.common.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Query
import kotliquery.Session
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger(Database::class.java)

interface Database {

    val dataSource: HikariDataSource

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
