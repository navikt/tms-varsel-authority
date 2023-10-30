package no.nav.tms.varsel.builder;

import static no.nav.tms.varsel.action.ValidationKt.VarselActionVersion;
import static no.nav.tms.varsel.builder.TestUtil.withEnv;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.nav.tms.varsel.action.VarselValidationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


class InaktiverVarselBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lagerInaktiverEventPaaVentetFormat() throws JsonProcessingException {
        String testVarselId = UUID.randomUUID().toString();

        String inaktiverVarsel = InaktiverVarselBuilder.newInstance()
            .withVarselId(testVarselId)
            .withProdusent("cluster", "namespace", "app")
            .build();

        JsonNode json = objectMapper.readTree(inaktiverVarsel);

        JsonNode produsent = json.get("produsent");
        assertEquals(produsent.get("cluster").asText(), "cluster");
        assertEquals(produsent.get("namespace").asText(), "namespace");
        assertEquals(produsent.get("appnavn").asText(), "app");

        JsonNode metadata = json.get("metadata");
        assertEquals(metadata.get("version").asText(), VarselActionVersion);
        assertEquals(metadata.get("builder_lang").asText(), "java");
        assertFalse(metadata.get("built_at").isNull());
    }

    @Test
    void henterInfoOmProdusentAutomatiskForInaktiverActionDerDetErMulig() throws JsonProcessingException {
        Map<String, String> naisEnv = new HashMap<>();
        naisEnv.put("NAIS_APP_NAME", "test-app");
        naisEnv.put("NAIS_NAMESPACE", "test-namespace");
        naisEnv.put("NAIS_CLUSTER_NAME", "dev");

        String inaktiverVarsel = withEnv(naisEnv, () ->
            InaktiverVarselBuilder.newInstance()
                .withVarselId(UUID.randomUUID().toString())
                .build()
        );

        JsonNode produsent = objectMapper.readTree(inaktiverVarsel).get("produsent");
        assertEquals(produsent.get("cluster").asText(), "dev");
        assertEquals(produsent.get("namespace").asText(), "test-namespace");
        assertEquals(produsent.get("appnavn").asText(), "test-app");
    }

    @Test
    void feilerHvisProdusentIkkeErSattOgDetIkkeKanHentesAutomatisk() {
        assertThrows(VarselValidationException.class, () ->
            InaktiverVarselBuilder.newInstance()
                .withVarselId(UUID.randomUUID().toString())
                .build()
        );
    }
}
