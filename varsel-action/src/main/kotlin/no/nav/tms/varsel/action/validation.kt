package no.nav.tms.varsel.action

import java.net.MalformedURLException
import java.net.URL

private const val BASE_16 = "[0-9a-fA-F]"
private const val BASE_32_ULID = "[0-9ABCDEFGHJKMNPQRSTVWXYZabcdefghjkmnpqrstvwxyz]"

private val UUID_PATTERN = "^$BASE_16{8}-$BASE_16{4}-$BASE_16{4}-$BASE_16{4}-$BASE_16{12}$".toRegex()
private val ULID_PATTERN = "^[0-7]$BASE_32_ULID{25}$".toRegex()

const val VarselActionVersion = "v2.1"

object OpprettVarselValidation {

    private val validators: List<OpprettVarselValidator> = listOf(
        IdentValidator,
        VarselIdValidator,
        OpprettVarselLanguageCodeValidator,
        OpprettVarselTekstLengthValidator,
        OpprettVarselDefaultTekstValidator,
        OpprettVarselTekstI18nValidator,
        OpprettVarselLinkContentValidator,
        OpprettVarselLinkRequiredValidator,
        AktivFremTilSupportedValidator,
        SmstekstValidator,
        EposttittelValidator,
        EposttekstValidator
    )

    fun validate(opprettVarsel: OpprettVarsel) = validators.validate(opprettVarsel)
}

private fun <T> List<Validator<T>>.validate(action: T) {
        val errors = map {
            it.validate(action)
        }.filterNot { it.isValid }

        if (errors.size > 1) {
            throw VarselValidationException(
                message = "Fant ${errors.size} feil ved validering av varsel-action",
                explanation = errors.mapNotNull { it.explanation }
            )
        } else if (errors.size == 1) {
            throw VarselValidationException(
                message = "Feil ved validering av varsel-action: ${errors.first().explanation}",
                explanation = errors.mapNotNull { it.explanation }
            )
        }
    }

class VarselValidationException(message: String, val explanation: List<String> = emptyList()): IllegalArgumentException(message)

private interface Validator<T> {
    val description: String

    fun assertTrue(validatorFunction: () -> Boolean) = if (validatorFunction()) {
        ValidatorResult(true)
    } else {
        ValidatorResult(false, description)
    }

    fun validate(varselAction: T): ValidatorResult
}

private interface OpprettVarselValidator: Validator<OpprettVarsel>

private data class ValidatorResult(
    val isValid: Boolean,
    val explanation: String? = null
)

private object IdentValidator: OpprettVarselValidator {
    override val description: String = "Fodselsnummer må være 11 tegn"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.ident.length == 11
    }
}

private object VarselIdValidator: OpprettVarselValidator {
    override val description: String = "Eventid må være gyldig UUID eller ULID"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.varselId.let {
            UUID_PATTERN.matches(it) || ULID_PATTERN.matches(it)
        }
    }
}

private object OpprettVarselTekstLengthValidator: OpprettVarselValidator {
    override val description = TekstLengthValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.tekster.all { tekst ->
            TekstLengthValidator.validate(varselAction.type, tekst)
        }
    }
}

private object OpprettVarselLanguageCodeValidator: OpprettVarselValidator {
    override val description = LanguageCodeValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.tekster.all {
            LanguageCodeValidator.validate(it)
        }
    }
}

private object LanguageCodeValidator {
    const val description = "Tekst må ha gyldig ISO 639 språkkode"

    private val validPattern = "^[a-zA-Z]{2,8}$".toRegex()

    fun validate(tekst: Tekst): Boolean {
        return validPattern.matches(tekst.spraakkode)
    }
}

private object TekstLengthValidator {
    private const val maxTextLengthBeskjed = 300
    private const val maxTextLengthOppgaveAndInnboks = 500

    const val description: String = "Tekst kan ikke være null, og over makslengde"

    fun validate(type: Varseltype, tekst: Tekst): Boolean {
        val maxLength = when (type) {
            Varseltype.Beskjed -> maxTextLengthBeskjed
            else -> maxTextLengthOppgaveAndInnboks
        }
        return tekst.tekst.length <= maxLength
    }
}

private object OpprettVarselTekstI18nValidator: OpprettVarselValidator {
    override val description = TekstI18nValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        TekstI18nValidator.validate(varselAction.tekster)
    }
}

private object TekstI18nValidator {
    const val description = "Kan kun ha opp til 1 tekst per språkkode"

    fun validate(tekster: List<Tekst>): Boolean {
        return tekster.groupingBy { it.spraakkode }
            .eachCount()
            .all { it.value == 1 }
    }
}

private object OpprettVarselDefaultTekstValidator: OpprettVarselValidator {
    override val description = TekstDefaultValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        TekstDefaultValidator.validate(varselAction.tekster)
    }
}

private object TekstDefaultValidator {
    const val description: String = "Presist 1 tekst må være satt som default hvis det finnes flere tekster"

    fun validate(tekster: List<Tekst>): Boolean {
        return tekster.size == 1 || tekster.count { it.default } == 1
    }
}

private object OpprettVarselLinkRequiredValidator: OpprettVarselValidator {
    override val description = LinkRequiredValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        LinkRequiredValidator.validate(varselAction.type, varselAction.link)
    }
}

private object LinkRequiredValidator {
    const val description: String = "link er påkrevd for innboks og oppgave"

    fun validate(type: Varseltype, link: String?): Boolean {
        return when(type) {
            Varseltype.Beskjed -> true
            Varseltype.Innboks, Varseltype.Oppgave -> link != null
        }
    }
}

private object OpprettVarselLinkContentValidator: OpprettVarselValidator {
    override val description = LinkContentValidator.description

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        LinkContentValidator.validate(varselAction.link)
    }
}

private object LinkContentValidator {
    private const val MAX_LENGTH_LINK = 200
    const val description: String = "Link må være gyldig URL og maks $MAX_LENGTH_LINK tegn"

    fun validate(link: String?): Boolean {
        return link == null || isValidURL(link)
    }

    private fun isValidURL(link: String) =
        link.length <= MAX_LENGTH_LINK && try {
            URL(link)
            true
        } catch (e: MalformedURLException) {
            false
        }
}

private object AktivFremTilSupportedValidator: OpprettVarselValidator {
    override val description = "Innboks støtter ikke aktivFremTil"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        when(varselAction.type) {
            Varseltype.Innboks -> varselAction.aktivFremTil == null
            else -> true
        }
    }
}

private object SmstekstValidator: OpprettVarselValidator {
    private const val MAX_LENGTH_SMS_VARSLINGSTEKST = 160
    override val description: String =
        "Sms-varsel kan ikke være tom string, og maks $MAX_LENGTH_SMS_VARSLINGSTEKST tegn"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.eksternVarsling
            ?.smsVarslingstekst
            ?.let {
            it.isNotBlank() && it.length <= MAX_LENGTH_SMS_VARSLINGSTEKST
        } ?: true
    }
}

private object EposttekstValidator: OpprettVarselValidator {
    private const val MAX_LENGTH_EPOST_VARSLINGSTEKST = 4000
    override val description: String =
        "Epost-tekst kan ikke være tom string, og maks $MAX_LENGTH_EPOST_VARSLINGSTEKST tegn"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.eksternVarsling
            ?.epostVarslingstekst
            ?.let {
                it.isNotBlank() && it.length <= MAX_LENGTH_EPOST_VARSLINGSTEKST
            } ?: true
    }
}

private object EposttittelValidator: OpprettVarselValidator {
    private const val MAX_LENGTH_EPOST_VARSLINGSTTITTEL = 40
    override val description: String =
        "Epost-tittel kan ikke være tom string, og maks $MAX_LENGTH_EPOST_VARSLINGSTTITTEL tegn"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.eksternVarsling
            ?.epostVarslingstittel
            ?.let {
                it.isNotBlank() && it.length <= MAX_LENGTH_EPOST_VARSLINGSTTITTEL
            } ?: true
    }
}
