# Komme i gang med varsler

_For brukeropplevelsen er det viktig at du bruker riktig type varsel. Ta gjerne en ekstrasjekk med [innholdsguiden vår](https://navikt.github.io/tms-dokumentasjon/guide/)._

1. Kafka tilgang: Opprett en pull-request mot topic `aapen-brukervarsel-v1`
   i [min-side-brukervarsel-topic-iac](https://github.com/navikt/min-side-brukervarsel-topic-iac).
2. Koble på topicene.
3. Send event!

## Varseltyper

| type    | beskrivelse                                          | Deaktiveres av                                                                                                                                        |
|---------|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| Beskjed | For korte påminnelser eller oppdatering av status    | Bruker, produsent, eller min side hvis aktivFremTil dato er satt og dato er passert.                                                                  | 
| Oppgave | For noe som må bli utført                            | Produsent når oppgaven er utført ( inaktiver-event), eller av min side hvis aktivFremTil dato er satt eller varslet har vært aktivt i mer enn ett år. |
| Innboks | For varsler om digitale innbokser andre steder i NAV | Produsent                                                                                                                                             |

**NB! Det er viktig å huske å sende inaktiver-event for oppgave-varsler. Hvis ikke vil det se ut for personen som at oppgaven ikke er utført.**

Alle varsler slettes 1 år etter mottaksdato.

## Ekstern varsling

Produsent kan velge om bruker også skal varsles via eksterne kanaler (sms og epost). Produsent kan velge preferert kanal, og hvorvidt standardtekst skal overskrives.

Standardtekst er av typen: `Hei! Du har fått en ny <varseltype> fra NAV. Logg inn på NAV for å se hva varselet gjelder. Vennlig hilsen NAV`

### Revarsling

Varsler med typen oppgave eller innboks får automatisk revarsling dersom varselet ikke er ferdigstilt etter et bestemt antall dager. Oppgaver blir revarsler etter 7 dager, og innboks blir revarslet etter 4 dager.

### Overskriving av standardtekster

Dersom en velger å overskrive standardtekster for epost/sms, er det anbefalt å overskrive samtlige tekster. Dette er fordi bruker kan motta varsler via annen kanal enn preferansen.

Eksterne varseltekster skal ikke inneholde lenker. Det er også produsentens ansvar å ikke sende sensitiv informasjon på epost og sms.

Tekst i sms er begrenset til 160 tegn. Tittel for epost er maksimalt 40 tegn, og tekst er maksimalt 4000 tegn. Tekst i epost kan og bør inneholde markup.

### Betinget sms

Fra og med versjon 2.1.0 av varsel-builder kan en velge BETINGET_SMS som preferert kanal. Dette gjør at systemet velger
sms eller epost som kanal dynamisk basert på tidspunkt ved sending. 

Tidsrommet der systemet velger sms fremfor epost er basert på reglene til altinn - utgangsvis 09:00-17:15,
innskrenket til 09:00-17:00 for å ta hensyn til forsinkelser i systemet.

Varsler produsert med både SMS og EPOST som preferert kanal (fra eldre buildere), vil fungere på samme måte.

### Utsatt sending

Produsent kan velge om ekstern varsling skal utsettes til et gitt tidspunkt. Dersom det underliggende varselet inaktiveres før dette,
vil det ikke sendes ekstern varsling.

Etter ekstern varsling er sendt gjelder vanlige regler for revarsling. Hvis en opprettet Oppgave med utsatt sending av ekstern varsling på 10 dager,
vil bruker evt revarsles etter ytterligere 7 dager - 17 dager etter varselet ble opprettet.

### Batching

Produsent kan bestemme om ekstern varsling kan holdes igjen og batches sammen med andre varsler som bestilles innen et gitt tidsrom. Hensikten med dette
er å skjerme bruker for støy i tilfeller der de ville fått mange sms-er og eposter over en kort periode. Eksterne varsler holdes igjen i opptil én time.

For varsler opprettet med eldre buildere, eller sendt til legacy-topics, gjelder følgende defaults:

| type    | batches                                    |
|---------|--------------------------------------------|
| Beskjed | Hvis sms-tekst og epost-tekst ikke er satt | 
| Oppgave | Nei                                        |
| Innboks | Nei                                        |

Varsler med utsatt sending vil aldri batches.

## Kafka, schemas og buildere

Min side varsler bruker ikke lenger Avro for schema-validering og serialisering. Produsenter sender eventer direkte på json-format. Vi tilbyr to sett med buildere for henholdsvis java- og kotlin-prosjekter. Det er ikke strengt nødvendig å bruke disse, men sterkt anbefalt. Builderne sørger for at format er riktig og gjør forhåndsvalidering av innhold. Det er også anbefalt å bruke varselId som kafka-nøkkel, for å opprettholde kronologi per enkelt varsel.

Builderne finnes i følgende bibliotek:

### Github Maven repository

- kotlin: no.nav.tms.varsel:kotlin-builder:2.1.1
- java: no.nav.tms.varsel:java-builder:2.1.1

Vi publiserer disse artifaktene til githubs package-repository. Husk å legge til én av disse repositories i ditt prosjekt:

 - `https://maven.pkg.github.com/navikt/tms-varsel-authority` (krever autentisering)
 - `https://github-package-registry-mirror.gc.nav.no/cached/maven-release` (NAIS sin mirror. Krever ingen autentisering) 

## Oppretting av varsel

For å gi varsel til bruker sender en et opprett-varsel event.

### Opprett-varsel felter

| felt            | påkrevd          | beskrivelse                                                                                | restriksjoner                                                                 | tillegginfo                                                                                                                                                                                                                 |
|-----------------|------------------|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| type            | ja               | Type på varsel (beskjed, oppgave, innboks)                                                 | Må være én av `beskjed`, `oppgave`, `innboks`                                 |                                                                                                                                                                                                                             |
| varselId        | ja               | Id til varselet. Produsenten bruker samme Id til å inaktivere varsel                       | Må være UUID eller ULID                                                       |                                                                                                                                                                                                                             |
| ident           | ja               | Fodselsnummer (evt. d-nummer eller tilsvarende) til mottaker av varsel                     | Må ha 11 siffer                                                               |                                                                                                                                                                                                                             |
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
      "epostVarslingstekst": "epostTekst",
      "kanBatches": false
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
   eksternVarsling {
      preferertKanal = EksternKanal.SMS
   }
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
   .withEksternVarsling(
       OpprettVarselBuilder.eksternVarsling()
               .withPreferertKanal(EksternKanal.SMS)
   )
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

## Buildere i tester

Dersom produsent ikke er satt eksplisitt vil builderene forsøke å hente dette basert på miljøvariablene [`NAIS_CLUSTER_NAME`, `NAIS_NAMESPACE`, `NAIS_APP_NAME`].

For apper som kjører på nais vil disse være satt automatisk. Hvis en ønsker å kjøre tester med automatisk henting av produsent,
kan en manuelt legge til disse variablene ved hjelp av `BuilderEnvironment.extend(<map med variabler>)`.

## Overvåking av varsler

### Logging

Hendelser blir logget til kibana med custom felter for filtrering

Alle varsler: `x_contenttype:"varsel"`
Sendt fra bestemt team: `x_initiated_by: "<namespace>"`
Spesifikt varsel: `x_minside_id :"<varselId>"`

### Kafka

Produsenter kan lytte på topic `aapen-varsel-hendelse-v1` for å følge med på status på varsler. Hendelser som kan skje for varsler er `opprettet`, `inaktivert`, `slettet` og `eksternStatusOppdatert`.

#### Opprettet, inaktivert, og slettet

Beskriver intern endring i status for et varsel.

- `opprettet`: Opprett-varsel event er validert og varsel er opprettet i database.
- `inaktivert`: Varsel er inaktivert av f. eks. produsent eller bruker selv.
- `slettet`: Varsel er slettet og ikke lenger synlig for bruker eller saksbehandler.

Eksempel for oppgave-varsel først opprettet av `team-test:demo-app` med id `11223344-aaaa-bbbb-cccc-112233445566`.

```json
{
  "@event_name": "<opprettet|inaktivert|slettet>",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

#### Ekstern status oppdatert

Beskriver ekstern endring i status for et varsel (sms og epost). Kommer med ulike 3 statuser.

- `venter`: Bestilling av varsling på sms/epost er lagt til i intern kø
- `bestilt`: Bestilling av ekstern varsling er oversendt til altinn for distribusjon.
- `sendt`: Ekstern varsling er bekreftet sendt via bestemt kanal (sms eller epost), og om det er renotifikasjon.
- `feilet`: Ekstern varsling feilet. Kommer med feilmelding.
- `kansellert`: Det underliggende varselet ble inaktivert før bestilling ble oversendt til altinn.
- `ferdigstilt`: Kommer når varsling er ferdigstilt. For eksempel når alle renotifikasjoner er utført eller stoppet.

Eksempler:

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "venter",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "bestilt",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "sendt",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "kanal": "SMS",
  "renotifikasjon": false,
  "sendtSomBatch": false,
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "feilet",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "feilmelding": "mottaker har reservert seg mot digital kommunikasjon",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "kansellert",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```

```json
{
  "@event_name": "eksternStatusOppdatert",
  "status": "ferdigstilt",
  "varseltype": "oppgave",
  "varselId": "11223344-aaaa-bbbb-cccc-112233445566",
  "melding": "Varsling er ferdigstilt og renotifikasjon er stanset.",
  "namespace": "team-test",
  "appnavn": "demo-app"
}
```
