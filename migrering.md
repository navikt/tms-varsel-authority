# Migreringsguide 

## Topic
Produsenter trenger kun å forholde seg til ett topic for å opprette og inaktivere varsler. Opprett pr i [github-repository](https://github.com/navikt/min-side-brukervarsel-topic-iac). 

## Buildere og avro

Vi bruker ikke lenger avro for å validere schema. Varsel-eventer sendes som json-string, med varsel-id som nøkkel. 

## Endringer i meldingene
Se [produsere varsler](https://navikt.github.io/tms-dokumentasjon/varsler/produsere/) for dokumentasjon av nytt format.

#### meldingstyper
Tidligere har det vært fire meldingstyper (beskjed, oppgave, innboks, done). Disse er nå slått sammen til to objekter: opprett og inaktiver. Varseltypen spesifiseres i opprett-eventet.

#### endring av feltnavn
- `eventId` -> `varselId`
- `sikkerhetsnivaa` -> `sensitivitet` (3 -> substantial, 4 -> high)
- `synligFremTil` -> `aktivFremTil`
- `fodselsnummer` -> `ident`

#### deprekerte felter

`grupperingsId` og `tidspunkt` er fjernet.

#### tekst
Alle tekster må ha en språkkode. Det er anbefalt å legge til tekst for flere språk.
Om det er tekst på flere språk, må én være satt som default.


## Eksempel

Et system sender varsler til brukere til gammelt topic med avro, og skal migreres.

Varslene har:
- type oppgave
- sikkerhetsnivå 4
- lenke til `https://www.nav.no`
- frist 14 dager etter oppretting
- teksten "Dette er et oppgave-varsel"
- ekstern varsling på sms med teksten "Dette er en sms om oppgave"

#### Gammelt oppsett med avro-builder

```kotlin
val gammeltOppgaveTopic = "min-side.aapen-brukernotifikasjon-oppgave-v1"

val kafkaProps = Properties().apply {
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
    // ...
}

val kafkaProducer = KafkaProducer<NokkelInput, OppgaveInput>(kafkaProps)

val oppgaveId = UUID.randomUUID().toString()

val nokkelInput = NokkelInputBuilder()
    .withEventId(oppgaveId)
    .withGrupperingsId("123")
    .withFodselsnummer(fodselsnummer)
    .withNamespace("test-team")
    .withAppnavn("demo-app")
    .build()

val oppgaveInput = OppgaveInputBuilder()
    .withTidspunkt(LocalDateTime.now())
    .withSynligFremTil(LocalDateTime.now().plusDays(14))
    .withTekst("Dette er et oppgave-varsel")
    .withLink(URL("https://www.nav.no"))
    .withSikkerhetsnivaa(4)
    .withEksternVarsling(true)
    .withSmsVarslingstekst("Dette er en sms om oppgave")
    .withPrefererteKanaler("SMS")

kafkaProducer.send(ProducerRecord(gammeltOppgaveTopic, nokkelInput, oppgaveInput))
// ...
val gammeltDoneTopic = "min-side.aapen-brukernotifikasjon-done-v1"

val done = DoneInputBuilder()
    .withTidspunkt(now())

kafkaProducer.send(ProducerRecord(gammeltDoneTopic, nokkel, done))
```

#### Nytt oppsett med kotlin-builder

```kotlin

val nyttVarselTopic = "min-side.aapen-brukervarsel-v1"

val kafkaProps = Properties().apply {
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    // ...
}

val kafkaProducer = KafkaProducer<String, String>(kafkaProps)

val oppgaveId = UUID.randomUUID().toString()

val opprettVarsel = VarselActionBuilder.opprett {
    type = Varseltype.Oppgave
    varselId = oppgaveId
    sensitivitet = Sensitivitet.High
    ident = fodselsnummer
    tekst = Tekst(
        spraakkode = "nb",
        tekst = "Dette er et oppgave-varsel",
        default = true
    )
    link = "https://www.nav.no"
    aktivFremTil = ZonedDateTime.now().plusDays(14)
    eksternVarsling = EksternVarslingBestilling(
        prefererteKanaler = listOf(EksternKanal.SMS),
        smsVarslingstekst = "Dette er en sms om oppgave",
    )
}

kafkaProducer.send(ProducerRecord(nyttVarselTopic, varselId, opprettVarsel))
// ...
val inaktiverVarsel = VarselActionBuilder.inaktiver {
    varselId = oppgaveId
}

kafkaProducer.send(ProducerRecord(nyttVarselTopic, varselId, inaktiverVarsel))
```
