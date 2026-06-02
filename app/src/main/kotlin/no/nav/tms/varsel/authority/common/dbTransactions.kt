package no.nav.tms.varsel.authority.common

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.action.NullableResultQueryAction
import kotliquery.sessionOf
import kotliquery.using
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.common.postgres.QueryException
import no.nav.tms.common.postgres.UniqueConstraintException
import org.postgresql.util.PSQLState
import java.sql.SQLException

fun <T> PostgresDatabase.transaction(actions: TransactionalSession.() -> T): T {
    val session = sessionOf(dataSource)

    val result: T = session.transaction {
        it.actions()
    }

    session.connection.close()

    return result
}

fun TransactionalSession.updateInTx(queryBuilder: () -> Query): Int {
    return try {
        queryBuilder()
            .asUpdate
            .let(::run)
    } catch (e: Exception) {
        if (e is SQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
            throw UniqueConstraintException(e)
        } else {
            throw QueryException("Error during 'update' query action", e)
        }
    }
}

fun <T> TransactionalSession.singleOrNullInTx(queryBuilder: () -> NullableResultQueryAction<T>) = run(queryBuilder.invoke())

fun <T> TransactionalSession.singleInTx(queryBuilder: () -> NullableResultQueryAction<T>) = singleOrNullInTx(queryBuilder)
    ?: throw IllegalStateException("Found no rows matching query")

