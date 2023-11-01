package no.nav.tms.varsel.action

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class OpprettVarselValidationTest {

    private val validOpprettBeskjedAction = opprettVarsel(Varseltype.Beskjed)
    private val validOpprettInnboksAction = opprettVarsel(Varseltype.Innboks)
    private val validOpprettOppgaveAction = opprettVarsel(Varseltype.Oppgave)

    private val text10Chars = "Laaaaaaang"

    @Test
    fun `godkjenner gyldig opprett-varsel event`() {
        shouldNotThrow<VarselValidationException> {
            OpprettVarselValidation.validate(validOpprettBeskjedAction)
            OpprettVarselValidation.validate(validOpprettInnboksAction)
            OpprettVarselValidation.validate(validOpprettOppgaveAction)
        }
    }

    @Test
    fun `feiler hvis varselId er ugyldig`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                varselId = "badId"
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis ident er ugyldig`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                ident = "badIdent"
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis link er ugyldig`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                link = "bad link"
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis link er for lang`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                link = "https://${text10Chars.repeat(20)}"
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis link mangler for annet enn beskjed`() {
        shouldNotThrow<VarselValidationException> {
            validOpprettBeskjedAction.copy(
                link = null
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
        shouldThrow<VarselValidationException> {
            validOpprettInnboksAction.copy(
                link = null
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                link = null
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis tekst mangler`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = emptyList()
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis tekst er for lang basert på type`() {
        shouldThrow<VarselValidationException> {
            validOpprettBeskjedAction.copy(
                tekster = listOf(Tekst("no", text10Chars.repeat(31), true))
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldThrow<VarselValidationException> {
            validOpprettInnboksAction.copy(
                tekster = listOf(Tekst("no", text10Chars.repeat(51), true))
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(Tekst("no", text10Chars.repeat(51), true))
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldNotThrow<VarselValidationException> {
            validOpprettInnboksAction.copy(
                tekster = listOf(Tekst("no", text10Chars.repeat(31), true))
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldNotThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(Tekst("no", text10Chars.repeat(31), true))
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis varsel har flere tekster men ingen default`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(
                    Tekst("no", "tekst1", false),
                    Tekst("en", "tekst2", false)
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis varsel har flere tekster satt som default`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(
                    Tekst("no", "tekst1", true),
                    Tekst("en", "tekst2", true),
                    Tekst("nn", "tekst3", false),
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis aktivFremTil er satt for innboks`() {
        shouldThrow<VarselValidationException> {
            validOpprettInnboksAction.copy(
                aktivFremTil = ZonedDateTime.now()
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis språkkode er ugyldig`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(
                    Tekst("ugyldig kode", "tekst1", true),
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis det finnes flere tekster med samme språkkode`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                tekster = listOf(
                    Tekst("no", "tekst1", true),
                    Tekst("no", "tekst2", false),
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    @Test
    fun `feiler hvis eksterne varseltekster er for lange`() {
        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                eksternVarsling = EksternVarslingBestilling(
                    smsVarslingstekst = text10Chars.repeat(17)
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                eksternVarsling = EksternVarslingBestilling(
                    epostVarslingstittel = text10Chars.repeat(5)
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }

        shouldThrow<VarselValidationException> {
            validOpprettOppgaveAction.copy(
                eksternVarsling = EksternVarslingBestilling(
                    epostVarslingstekst = text10Chars.repeat(401)
                )
            ).let {
                OpprettVarselValidation.validate(it)
            }
        }
    }

    private fun opprettVarsel(type: Varseltype) = OpprettVarsel(
        type = type,
        varselId = UUID.randomUUID().toString(),
        ident = "12345678910",
        sensitivitet = Sensitivitet.Substantial,
        link = "https://link",
        tekster = listOf(Tekst("no", "tekst", default = true)),
        eksternVarsling = EksternVarslingBestilling(
            prefererteKanaler = listOf(EksternKanal.SMS, EksternKanal.EPOST),
            smsVarslingstekst = "sms tekst",
            epostVarslingstittel = "epost tittel",
            epostVarslingstekst = "epost tekst"
        ),
        aktivFremTil = if (type == Varseltype.Innboks) {
            null
        } else {
            ZonedDateTime.now().plusDays(1)
        },
        produsent = Produsent("cluster", "namespace", "appnavn"),
        metadata = mapOf("meta" to "data")
    )
}
