package no.nav.tms.varsel.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.nav.tms.varsel.action.*;

import java.time.ZonedDateTime;
import java.util.*;

import static no.nav.tms.varsel.builder.BuilderUtil.metadata;
import static no.nav.tms.varsel.builder.BuilderUtil.produsent;

public class OpprettVarselBuilder {
    private static final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private Varseltype type;
    private String varselId;
    private String ident;
    private Sensitivitet sensitivitet;
    private String link;
    private List<Tekst> tekster;
    private EksternVarslingBestilling eksternVarsling;
    private ZonedDateTime aktivFremTil;
    private Produsent produsent;

    private final HashMap<String, Object> metadata;

    private OpprettVarselBuilder() {
        type = null;
        varselId = null;
        ident = null;
        sensitivitet = null;
        link = null;
        tekster = new ArrayList<>();
        eksternVarsling = null;
        aktivFremTil = null;
        produsent = produsent();
        metadata = metadata();
    }

    public static OpprettVarselBuilder newInstance() {
        return new OpprettVarselBuilder();
    }

    public OpprettVarselBuilder withType(Varseltype type) {
        this.type = type;
        return this;
    }

    public OpprettVarselBuilder withVarselId(String varselId) {
        this.varselId = varselId;
        return this;
    }

    public OpprettVarselBuilder withIdent(String ident) {
        this.ident = ident;
        return this;
    }
    public OpprettVarselBuilder withSensitivitet(Sensitivitet sensitivitet) {
        this.sensitivitet = sensitivitet;
        return this;
    }
    public OpprettVarselBuilder withLink(String link) {
        this.link = link;
        return this;
    }

    public OpprettVarselBuilder withTekst(String spraak, String tekst) {
        return withTekst(spraak, tekst, false);
    }

    public OpprettVarselBuilder withTekst(String spraak, String tekst, boolean isDefault) {
        tekster.add(new Tekst(spraak, tekst, isDefault));
        return this;
    }

    public OpprettVarselBuilder withEksternVarsling() {
        return withEksternVarsling(
            Collections.emptyList(),
            null,
            null,
            null
        );
    }

    public OpprettVarselBuilder withEksternVarsling(EksternKanal kanal) {
        return withEksternVarsling(
            Collections.singletonList(kanal),
            null,
            null,
            null
        );
    }

    public OpprettVarselBuilder withEksternVarsling(
        List<EksternKanal> prefererteKanaler,
        String smsVarslingstekst,
        String epostVarslingstittel,
        String epostVarslingstekst
        ) {
        this.eksternVarsling = new EksternVarslingBestilling(
            prefererteKanaler,
            smsVarslingstekst,
            epostVarslingstittel,
            epostVarslingstekst
        );
        return this;
    }
    public OpprettVarselBuilder withAktivFremTil(ZonedDateTime aktivFremTil) {
        this.aktivFremTil = aktivFremTil;
        return this;
    }
    public OpprettVarselBuilder withProdusent(String cluster, String namespace, String appnavn) {
        this.produsent = new Produsent(cluster, namespace, appnavn);
        return this;
    }

    public String build() {
        performNullCheck();

        OpprettVarsel opprettVarsel = new OpprettVarsel(
            this.type,
            this.varselId,
            this.ident,
            this.sensitivitet,
            this.link,
            this.tekster,
            this.eksternVarsling,
            this.aktivFremTil,
            this.produsent,
            this.metadata
        );

        OpprettVarselValidation.INSTANCE.validate(opprettVarsel);

        try {
            return objectMapper.writeValueAsString(opprettVarsel);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void performNullCheck() {
        try {
            Objects.requireNonNull(type, "type kan ikke være null");
            Objects.requireNonNull(varselId, "varselId kan ikke være null");
            Objects.requireNonNull(ident, "ident kan ikke være null");
            Objects.requireNonNull(sensitivitet, "sensitivitet kan ikke være null");
            Objects.requireNonNull(produsent, "produsent kan ikke være null");

            if (tekster.isEmpty()) {
                throw new VarselValidationException("Må ha satt minst 1 tekst", Collections.emptyList());
            }

        } catch (NullPointerException e) {
            throw new VarselValidationException(e.getMessage(), Collections.emptyList());
        }
    }
}
