package no.nav.tms.varsel.authority.metrics

private const val METRIC_NAMESPACE = "dittnav.kafka.events.v1"

const val DB_EVENTS_CACHED = "$METRIC_NAMESPACE.cached"

const val KAFKA_RAPID_EVENTS_PROCESSED = "$METRIC_NAMESPACE.rapid.processed"
const val KAFKA_RAPID_VARSEL_INAKTIVERT_PRODUCED = "$METRIC_NAMESPACE.rapid.varselinaktivert.produced"
const val KAFKA_RAPID_VARSEL_AKTIVERT_PRODUCED = "$METRIC_NAMESPACE.rapid.varselaktivert.produced"

const val KAFKA_DOKNOT_STATUS_TOTAL_PROCESSED = "$METRIC_NAMESPACE.doknot.status.total.processed"
const val KAFKA_DOKNOT_STATUS_UPDATED = "$METRIC_NAMESPACE.doknot.status.updated"
const val KAFKA_DOKNOT_STATUS_IGNORED = "$METRIC_NAMESPACE.doknot.status.ignored"
const val KAFKA_DOKNOT_STATUS_BATCH = "$METRIC_NAMESPACE.doknot.status.batch"

const val DB_EVENTS_ARCHIVED = "$METRIC_NAMESPACE.db.events.archived"
const val DB_EVENTS_EXPIRED = "$METRIC_NAMESPACE.db.events.expired"
