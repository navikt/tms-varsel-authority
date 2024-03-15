# tms-varsel-authority

Prosjekt for håndtering av brukervarsler bestilt av fagsystem via kafka.

## Funksjon

`tms-varsel-authority` har følgende funksjon:

- Lese, validere, og opprette varsler basert på eksternt topic `min-side.aapen-brukervarsel-v1`
- Lytte på oppdateringer om ekstern varsling fra internt topic `min-side.brukervarsel-v1` sendt fra [tms-doknotstatus-converter](https://github.com/navikt/tms-doknotstatus-converter)
- Publisere oppdateringer for varsler på internt topic `min-side.brukervarsel-v1`
  - Brukes av [tms-varsel-event-gateway](https://github.com/navikt/tms-varsel-event-gateway) til å filtrere og publisere videre på eksternt topic `min-side.aapen-varsel-hendelse-v1` 
  - Brukes av [tms-ekstern-varselbestiller](https://github.com/navikt/tms-ekstern-varselbestiller) til å bestille sms/epost etter varsel er persistert
- Tillate brukere å hente sine egne varsler via et api sikret med tokenx-autentisering
- Tillate baksystem å hente varsler og metadata for vilkårlig bruker via et api sikret med azure-autentisering
- Inaktivere eller slette eldre varsler etter behov

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på github.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-personbruker.
