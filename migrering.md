# Migreringsguide 

## Topic
Alle meldinger skal produseres til __det samme topicet__
opprett pr i [github-repository](https://github.com/navikt/min-side-brukervarsel-topic-iac). 

## Endringer i meldingene
En del felter er fjernet, se [produsere varsler](https://tms-dokumentasjon.intern.nav.no/varsler/produsere) for nytt format og meldingsbyggere

#### meldingstyper
Tidligere har det vært fire meldingstyper (beskjed, oppgave, innboks, done). Disse er nå slått sammen til 
2 objekter; aktiver og inaktiver. Varseltypen skal spesifiseres i meldingen.

#### feltnavn
* eventId -> varselId
* sikkerhetsnivaa -> sensitivitet (3 -> substantial, 4 -> high)
* synligFremTil -> aktivFremTil
* fodselsnummer -> ident

#### tekst:
Det er anbefalt å legge til tekst for flere språk, alle tekster må ha en språkkode.
Om det er tekst på flere språk, må 1 være satt som default.


