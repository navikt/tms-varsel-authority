package no.nav.tms.varsel.authority.metrics

import io.prometheus.client.Counter
import no.nav.tms.varsel.authority.config.EventType

object PrometheusMetricsCollector {

    private const val NAMESPACE = "dittnav_consumer"
    private const val DB_CACHED_EVENTS = "db_cached_events"

    private val MESSAGES_CACHED: Counter = Counter.build()
            .name(DB_CACHED_EVENTS)
            .namespace(NAMESPACE)
            .help("Number of events of type in DB-table")
            .labelNames("type", "producer")
            .register()

    fun registerEventsCached(count: Int, eventType: EventType, producer: String) {
        MESSAGES_CACHED.labels(eventType.eventType, producer).inc(count.toDouble())
    }
}
