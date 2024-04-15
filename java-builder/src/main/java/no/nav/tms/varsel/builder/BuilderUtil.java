package no.nav.tms.varsel.builder;

import no.nav.tms.varsel.action.Produsent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static no.nav.tms.varsel.action.ValidationKt.VarselActionVersion;

class BuilderUtil {
    static Produsent produsent() {
        String cluster = BuilderEnvironment.get("NAIS_CLUSTER_NAME");
        String namespace = BuilderEnvironment.get("NAIS_NAMESPACE");
        String appnavn = BuilderEnvironment.get("NAIS_APP_NAME");

        if (cluster == null || namespace == null || appnavn == null) {
            return null;
        } else {
            return new Produsent(cluster, namespace, appnavn);
        }
    }

    static HashMap<String, Object> metadata() {
        HashMap<String, Object> metadata = new HashMap<>();

        metadata.put("version", VarselActionVersion);
        metadata.put("built_at", ZonedDateTime.now(ZoneId.of("Z")).truncatedTo(ChronoUnit.MILLIS));
        metadata.put("builder_lang", "java");

        return metadata;
    }
}
