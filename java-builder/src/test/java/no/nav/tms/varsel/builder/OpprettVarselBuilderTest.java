package no.nav.tms.varsel.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.nav.tms.varsel.action.EksternKanal;
import no.nav.tms.varsel.action.Sensitivitet;
import no.nav.tms.varsel.action.VarselValidationException;
import no.nav.tms.varsel.action.Varseltype;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static no.nav.tms.varsel.action.ValidationKt.VarselActionVersion;
import static org.junit.jupiter.api.Assertions.*;

class OpprettVarselBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUp() {
        BuilderEnvironment.reset();
    }

    @Test
    void lagerOpprettVarselPaaVentetFormat() throws JsonProcessingException {

        String testVarselId = UUID.randomUUID().toString();

        String opprettVarsel = OpprettVarselBuilder.newInstance()
            .withType(Varseltype.Beskjed)
            .withVarselId(testVarselId)
            .withIdent("12345678910")
            .withSensitivitet(Sensitivitet.High)
            .withLink("https://link")
            .withTekst("no", "tekst", true)
            .withTekst("en", "text", false)
            .withEksternVarsling(
                OpprettVarselBuilder.eksternVarsling()
                    .withPreferertKanal(EksternKanal.SMS)
                    .withEpostVarslingstittel("eposttittel")
                    .withEpostVarslingstekst("eposttekst")
                    .withSmsVarslingstekst("smstekst")
                    .withKanBatches(true)
                    .withUtsettSendingTil(ZonedDateTime.parse("2023-10-05T10:00:00Z"))
            )
            .withAktivFremTil(ZonedDateTime.parse("2023-10-10T10:00:00Z"))
            .withProdusent("cluster", "namespace", "app")
            .build();

        JsonNode json = objectMapper.readTree(opprettVarsel);

        assertEquals(json.get("type").asText(), "beskjed");
        assertEquals(json.get("varselId").asText(), testVarselId);
        assertEquals(json.get("ident").asText(), "12345678910");
        assertEquals(json.get("sensitivitet").asText(), "high");
        assertEquals(json.get("link").asText(), "https://link");

        JsonNode tekster1 = json.get("tekster").get(0);
        assertEquals(tekster1.get("spraakkode").asText(), "no");
        assertEquals(tekster1.get("tekst").asText(), "tekst");
        assertTrue(tekster1.get("default").asBoolean());


        JsonNode tekster2 = json.get("tekster").get(1);
        assertEquals(tekster2.get("spraakkode").asText(), "en");
        assertEquals(tekster2.get("tekst").asText(), "text");
        assertFalse(tekster2.get("default").asBoolean());

        JsonNode eksternVarsling = json.get("eksternVarsling");
        assertFalse(eksternVarsling.isNull());
        assertEquals(eksternVarsling.get("prefererteKanaler").get(0).asText(), "SMS");
        assertEquals(eksternVarsling.get("epostVarslingstittel").asText(), "eposttittel");
        assertEquals(eksternVarsling.get("epostVarslingstekst").asText(), "eposttekst");
        assertEquals(eksternVarsling.get("smsVarslingstekst").asText(), "smstekst");
        assertTrue(eksternVarsling.get("kanBatches").asBoolean());
        assertEquals(eksternVarsling.get("utsettSendingTil").asText(), "2023-10-05T10:00:00Z");

        assertEquals(json.get("aktivFremTil").asText(), "2023-10-10T10:00:00Z");

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
    void setterRiktigDefaultVerdierPaaEksternVarsling() throws JsonProcessingException {

        String testVarselId = UUID.randomUUID().toString();

        String opprettVarsel = OpprettVarselBuilder.newInstance()
                .withType(Varseltype.Oppgave)
                .withVarselId(testVarselId)
                .withIdent("12345678910")
                .withSensitivitet(Sensitivitet.High)
                .withLink("https://link")
                .withTekst("no", "tekst", true)
                .withEksternVarsling()
                .withProdusent("cluster", "namespace", "app")
                .build();

        JsonNode json = objectMapper.readTree(opprettVarsel);

        assertEquals(json.get("type").asText(), "oppgave");

        JsonNode eksternVarsling = json.get("eksternVarsling");
        assertFalse(eksternVarsling.isNull());
        assertEquals(0, eksternVarsling.get("prefererteKanaler").size());
        assertNull(eksternVarsling.get("kanBatches"));
        assertNull(eksternVarsling.get("utsettSendingTil"));
        assertNull(eksternVarsling.get("smsVarslingstekst"));
        assertNull(eksternVarsling.get("epostVarslingstittel"));
        assertNull(eksternVarsling.get("epostVarslingstekst"));
        assertNull(eksternVarsling.get("kanBatches"));
        assertNull(eksternVarsling.get("utsettSendingTil"));
    }

    @Test
    void henterInfoOmProdusentAutomatiskForOpprettActionDerDetErMulig() throws JsonProcessingException {
        Map<String, String> naisEnv = new HashMap<>();
        naisEnv.put("NAIS_APP_NAME", "test-app");
        naisEnv.put("NAIS_NAMESPACE", "test-namespace");
        naisEnv.put("NAIS_CLUSTER_NAME", "dev");

        BuilderEnvironment.extend(naisEnv);

        String opprettVarsel = OpprettVarselBuilder.newInstance()
            .withType(Varseltype.Beskjed)
            .withVarselId(UUID.randomUUID().toString())
            .withIdent("12345678910")
            .withSensitivitet(Sensitivitet.High)
            .withLink("https://link")
            .withTekst("no", "tekst", true)
            .withTekst("en", "text", false)
            .withEksternVarsling()
            .withAktivFremTil(ZonedDateTime.parse("2023-10-10T10:00:00Z"))
            .build();

        JsonNode produsent = objectMapper.readTree(opprettVarsel).get("produsent");
        assertEquals(produsent.get("cluster").asText(), "dev");
        assertEquals(produsent.get("namespace").asText(), "test-namespace");
        assertEquals(produsent.get("appnavn").asText(), "test-app");
    }

    @Test
    void kasterExceptionHvisOpprettVarselIkkeErGyldig() {
        assertThrows(VarselValidationException.class, () ->
            OpprettVarselBuilder.newInstance()
                .withType(Varseltype.Beskjed)
                .withVarselId("badId")
                .withIdent("12345678910")
                .withSensitivitet(Sensitivitet.High)
                .withLink("https://link")
                .withTekst("no", "tekst", true)
                .withTekst("en", "text", false)
                .withEksternVarsling()
                .withAktivFremTil(ZonedDateTime.parse("2023-10-10T10:00:00Z"))
                .withProdusent("cluster", "namespace", "app")
                .build()
        );
    }

    @Test
    void feilerHvisProdusentIkkeErSattOgDetIkkeKanHentesAutomatisk() {
        assertThrows(VarselValidationException.class, () ->
            OpprettVarselBuilder.newInstance()
                .withType(Varseltype.Beskjed)
                .withVarselId(UUID.randomUUID().toString())
                .withIdent("12345678910")
                .withSensitivitet(Sensitivitet.High)
                .withLink("https://link")
                .withTekst("no", "tekst", true)
                .withTekst("en", "text", false)
                .withEksternVarsling()
                .withAktivFremTil(ZonedDateTime.parse("2023-10-10T10:00:00Z"))
                .build()
        );
    }

    @Test
    void tillaterAtEksternVarslingIkkeErSatt() {
        assertDoesNotThrow(() ->
            OpprettVarselBuilder.newInstance()
                .withType(Varseltype.Beskjed)
                .withVarselId(UUID.randomUUID().toString())
                .withIdent("12345678910")
                .withSensitivitet(Sensitivitet.High)
                .withLink("https://link")
                .withTekst("no", "tekst")
                .withProdusent("cluster", "namespace", "app")
                .build()
        );
    }
}

