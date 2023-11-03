# Komme i gang med varsler

_For brukeropplevelsen er det viktig at du bruker riktig type varsel. Ta gjerne en ekstrasjekk
med [innholdsguiden vår](https://tms-dokumentasjon.intern.nav.no/innholdsguide)._

1. Kafka tilgang: Opprett en pull-request
   i [min-side-brukervarsel-topic-iac](https://github.com/navikt/min-side-brukervarsel-topic-iac).
2. Koble på topicene.
3. Send event!

## Varseltyper

| type    | beskrivelse                                          | Deaktiveres av                                                                                                                                        |
|---------|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| Beskjed | For korte påminnelser eller oppdatering av status    | Bruker, produsent, eller min side hvis aktivFremTil dato er satt og dato er passert.                                                                  | 
| Oppgave | For noe som må bli utført                            | Produsent når oppgaven er utført ( inaktiver-event), eller av min side hvis aktivFremTil dato er satt eller varslet har vært aktivt i mer enn ett år. |
| Innboks | For varsler om digitale innbokser andre steder i NAV | Produsent                                                                                                                                             |

**NB! Det er viktig å huske å sende inaktiver-event for oppgave-varsler. hvis ikke vil det se ut for personen som at oppgaven ikke er utført.**

Alle varsler slettes 1 år etter mottaksdato.

## Ekstern varsling

Produsent kan velge om bruker også skal varsles via eksterne kanaler (sms og epost). Produsent kan velge preferert kanal,
og hvorvidt standardtekst skal overskrives. 

Standardtekst er av typen: `Hei! Du har fått en ny <varseltype> fra NAV. Logg inn på NAV for å se hva varselet gjelder. Vennlig hilsen NAV`

Eksterne varseltekster skal ikke inneholde lenker. Det er også produsentens ansvar å ikke sende sensitiv informasjon på
epost og sms.

### Revarsling 

Varsler med typen oppgave eller innboks får automatisk revarsling dersom varselet ikke er ferdigstilt etter et
bestemt antall dager. Oppgaver blir revarsler etter 7 dager, og innboks blir revarslet etter 4 dager.

### Overskriving av standardtekster

Dersom en velger å overskrive standardtekster for epost/sms, er det anbefalt å overskrive samtlige tekster, selv om
kun 1 kanal er preferert. Dette er fordi bruker kan motta varsler via annen kanal enn preferansen.


## Kafka, schemas og buildere

Min side varsler bruker ikke lenger Avro for schema-validering og serialisering. Produsenter sender eventer direkte på
json-format. Vi tilbyr to sett med buildere for henholdsvis java- og kotlin-prosjekter. Det er ikke strengt nødvendig å 
bruke disse, men sterkt anbefalt. Builderne sørger for at format er riktig og har gjør forhåndsvalidering av innhold. 
Det er også anbefalt å bruke varselId som kafka-nøkkel, for å opprettholde kronologi per enkelt varsel.

Builderne finnes i følgende bibliotek:

### Github Maven repository

- kotlin: no.nav.tms.varsel:kotlin-builder:1.0.0-beta
- java: no.nav.tms.varsel:java-builder:1.0.0-beta

### Jitpack

- kotlin: com.github.navikt.tms-varsel-authority:kotlin-builder:1.0.0-beta
- java: com.github.navikt.tms-varsel-authority:java-builder:1.0.0-beta

## Oppretting av varsel

For å gi varsel til bruker sender en et opprett-varsel event.

### Opprett-varsel felter

| felt            | påkrevd          | beskrivelse                                                                                | restriksjoner                                                                 | tillegginfo                                                                                                                                                                                                                 |
|-----------------|------------------|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| type            | ja               | Type på varsel (beskjed, oppgave, innboks)                                                 | Må være én av `beskjed`, `oppgave`, `innboks`                                 |                                                                                                                                                                                                                             |
| varselId        | ja               | Id til varselet. Produsenten bruker samme Id til å inaktivere varsel                       | Må være UUID eller ULID                                                       |                                                                                                                                                                                                                             |
| ident           | ja               | Personident til mottaker av varsel                                                         | Må ha 11 siffer                                                               |                                                                                                                                                                                                                             |
| tekster         | ja, minst 1      | Teksten som faktisk vises i varselet med språkkode.                                        | Dersom flere tekster på ulike språk er gitt må én tekst være satt som default | Språkkode må følge ISO-639 (typen `no`, `nb`, `en`...)                                                                                                                                                                      |
| link            | ikke for beskjed | Lenke som blir aktivert når en person trykker på varselet i varselbjella eller på min side | Komplett URL, inkludert `https` protokoll.                                    |                                                                                                                                                                                                                             |
| sensitivitet    | ja               | påkrevd level-of-assurance for å kunne se innhold i varsel                                 | Én av `high`, `substantial`                                                   | `high` og `substantial` tilsvarer det som tidligere var henholdvis nivå `4` og `3`. Hvis personen har varsler med sensitivitet `high`, men er logget inn med LoA `substantial`, vil hen se type varsel, men ikke innholdet. |
| aktivFremTil    | nei              | Tidspunkt for når varslet skal inaktiverer automatisk av systemet                          | Tidspunkt med tidssone. `UTC` eller `Z` er anbefalt                           | Støttes ikke for Innboks-varsler                                                                                                                                                                                            |
| eksternVarsling | nei              | Om det skal sendes sms og/eller epost til mottaker                                         | Kan kun velge preferert kanal `SMS` eller `EPOST`.                            | Dersom ekstern varslingstekst ikke er satt blir det sendt en standardtekst.                                                                                                                                                 |
| produsent       | ja               | Teknisk kilde til varsel-eventet                                                           | Ingen spesielle                                                               | Buildere vil forsøke å hente dette automatisk basert på nais-miljøvariabler. Der disse ikke er tilgjengelige må produsent settes manuelt.                                                                                   |


### Json-format

```json
{
   "@event_name": "opprett",
   "type": "<type>",
   "varselId": "<varselId>",
   "ident": "<ident>",
   "tekster": [
      {
         "spraakkode": "<spraakkode>",
         "tekst": "<tekst>",
         "default": true
      }
   ],
   "link": "<link>",
   "sensitivitet": "<sensitivitet>",
   "aktivFremTil": "<aktivFremTil>",
   "eksternVarsling": {
      "prefererteKanaler": ["SMS", "EPOST"],
      "smsVarslingstekst": "<smsTekst>",
      "epostVarslingstittel": "epostTittel",
      "epostVarslingstekst": "epostTekst"
   },
   "produsent": {
      "cluster": "<cluster>",
      "namespace": "<namespace>",
      "appnavn": "<appnavn>"
   }
}
```

### Eksempel med buildere

Eksempel på oppretting av Oppgave-varsel for bruker `12345678901` med id `aabbccdd-abcd-1234-5678-abcdef123456`. 

Varselet skal: 
- ha sensitivitet `high`
- lenke til `https://www.nav.no`
- løpe ut 14 dager etter oppretting
- ha en norsk (default) og en engelsk tekst
- varsles på sms med standardtekst.

#### Med kotlin-builder

```kotlin
val kafkaValueJson = VarselActionBuilder.opprett {
   type = Varseltype.Oppgave
   varselId = "aabbccdd-abcd-1234-5678-abcdef123456"
   sensitivitet = Sensitivitet.High
   ident = "12345678901"
   tekster += Tekst(
      spraakkode = "nb",
      tekst = "Norsk tekst",
      default = true
   )
   tekster += Tekst(
      spraakkode = "en",
      tekst = "English text",
      default = false
   )
   link = "https://www.nav.no"
   aktivFremTil = ZonedDateTime.now(ZoneId.of("Z")).plusDays(14)
   eksternVarsling = EksternVarslingBestilling(prefererteKanaler = listOf(EksternKanal.SMS))
}
```

#### Med java-builder

```java
String kafkaValueJson = OpprettVarselBuilder.newInstance()
   .withType(Varseltype.Oppgave)
   .withVarselId("aabbccdd-abcd-1234-5678-abcdef123456")
   .withSensitivitet(Sensitivitet.High)
   .withIdent("12345678901")
   .withTekst("nb", "Norsk tekst", true)
   .withTekst("en", "English text", false)
   .withLink("https://www.nav.no")
   .withAktivFremTil(ZonedDateTime.now(ZoneId.of("Z")).plusDays(14))
   .withEksternVarsling(EksternKanal.SMS)
   .build();
```

## Inaktivering av varsel

Oppgave- og innboks-varsler inaktiveres av produsent. Dette gjør en ved å sende et inaktiver-event.

### Inaktiver-varsel felter

| felt            | påkrevd          | beskrivelse                           | tillegginfo                                                                                                                                                                                                                 |
|-----------------|------------------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| varselId        | ja               | Id til varselet som skal inaktiveres  |                                                                                                                                                                                                                             |
| produsent       | ja               | Teknisk kilde til varsel-eventet      | Buildere vil forsøke å hente dette automatisk basert på nais-miljøvariabler. Der disse ikke er tilgjengelige må produsent settes manuelt.                                                                                   |


### Json-format

```json
{
   "@event_name": "inaktiver",
   "varselId": "<varselId>",
   "produsent": {
      "cluster": "<cluster>",
      "namespace": "<namespace>",
      "appnavn": "<appnavn>"
   }
}
```

### Eksempel med buildere

Eksempel på inaktivering av varsel med id `aabbccdd-abcd-1234-5678-abcdef123456`.

#### Med kotlin-builder

```kotlin
val kafkaValueJson = VarselActionBuilder.inaktiver {
   varselId = "aabbccdd-abcd-1234-5678-abcdef123456"
}
```

#### Med java-builder

```java
String kafkaValueJson = InaktiverVarselBuilder.newInstance()
   .withVarselId("aabbccdd-abcd-1234-5678-abcdef123456")
   .build();
```
