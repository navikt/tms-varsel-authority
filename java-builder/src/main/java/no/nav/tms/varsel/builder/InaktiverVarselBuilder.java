package no.nav.tms.varsel.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.nav.tms.varsel.action.*;

import java.util.*;

import static no.nav.tms.varsel.builder.BuilderUtil.metadata;
import static no.nav.tms.varsel.builder.BuilderUtil.produsent;

public class InaktiverVarselBuilder {
    private static final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private String varselId;
    private Produsent produsent;

    private final Map<String, Object> metadata;

    private InaktiverVarselBuilder() {
        varselId = null;
        produsent = produsent();
        metadata = metadata();
    }

    public static InaktiverVarselBuilder newInstance() {
        return new InaktiverVarselBuilder();
    }

    public InaktiverVarselBuilder withVarselId(String varselId) {
        this.varselId = varselId;
        return this;
    }

    public InaktiverVarselBuilder withProdusent(String cluster, String namespace, String appnavn) {
        this.produsent = new Produsent(cluster, namespace, appnavn);
        return this;
    }

    public String build() {
        performNullCheck();

        InaktiverVarsel opprettVarsel = new InaktiverVarsel(
            this.varselId,
            this.produsent,
            this.metadata
        );

        try {
            return objectMapper.writeValueAsString(opprettVarsel);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void performNullCheck() {
        try {
            Objects.requireNonNull(varselId, "varselId kan ikke være null");
            Objects.requireNonNull(produsent, "produsent kan ikke være null");

        } catch (NullPointerException e) {
            throw new VarselValidationException(e.getMessage(), Collections.emptyList());
        }
    }
}

