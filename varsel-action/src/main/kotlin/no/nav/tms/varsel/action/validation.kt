package no.nav.tms.varsel.action

import java.net.URI

const val VarselActionVersion = "v2.2"

class VarselIdException(msg: String): IllegalArgumentException(msg)

object VarselIdValidator {
    private const val BASE_16 = "[0-9a-fA-F]"
    private const val BASE_32_ULID = "[0-9ABCDEFGHJKMNPQRSTVWXYZabcdefghjkmnpqrstvwxyz]"

    private val UUID_PATTERN = "^$BASE_16{8}-$BASE_16{4}-$BASE_16{4}-$BASE_16{4}-$BASE_16{12}$".toRegex()
    private val ULID_PATTERN = "^[0-7]$BASE_32_ULID{25}$".toRegex()

    fun validate(varselId: String) {
        if (!UUID_PATTERN.matches(varselId) && !ULID_PATTERN.matches(varselId)) {
            throw VarselIdException("varselId must be either UUID or ULID")
        }
    }
}

object OpprettVarselValidation {

    private val validators: List<OpprettVarselValidator> = listOf(
        IdentValidator,
        OpprettVarselVarselIdValidator,
        OpprettVarselLanguageCodeValidator,
        OpprettVarselTekstLengthValidator,
        OpprettVarselDefaultTekstValidator,
        OpprettVarselTekstI18nValidator,
        OpprettVarselLinkRequiredValidator,
        OpprettVarselLinkFormatValidator,
        OpprettVarselLinkContentValidator,
        AktivFremTilSupportedValidator,
        SmstekstValidator,
        EposttittelValidator,
        EposttekstValidator,
        ForbyLinkIEksternVarslingValidator
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

private object OpprettVarselVarselIdValidator: OpprettVarselValidator {
    override val description: String = "Eventid må være gyldig UUID eller ULID"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.varselId.let { varselId ->
            try {
                VarselIdValidator.validate(varselId)
                true
            } catch (e: VarselIdException) {
                false
            }
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
    override val description: String = "link er påkrevd for innboks og oppgave"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        when(varselAction.type) {
            Varseltype.Beskjed -> true
            Varseltype.Innboks, Varseltype.Oppgave -> varselAction.link != null
        }
    }
}

private object OpprettVarselLinkFormatValidator: OpprettVarselValidator {
    private const val MAX_LENGTH_LINK = 200
    override val description: String = "Link må være gyldig URL og maks $MAX_LENGTH_LINK tegn"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.link == null || isValidURL(varselAction.link)
    }

    private fun isValidURL(link: String) =
        link.length <= MAX_LENGTH_LINK && try {
            URI.create(link).toURL()
            true
        } catch (e: IllegalArgumentException) {
            false
        }
}

private object OpprettVarselLinkContentValidator: OpprettVarselValidator {
    private val navDomainPattern = "https://(?:[a-z0-9-]{0,61}\\.)*nav\\.no(\\z|[/?])".toRegex()

    override val description: String = "Link må lede til et nav.no domene"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        varselAction.link == null || navDomainPattern.containsMatchIn(varselAction.link)
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

private object ForbyLinkIEksternVarslingValidator: OpprettVarselValidator {
    private const val URL_CHARACTERS = "[-a-zA-Z0-9@:%_\\+.~#?&//=]"
    private val linkLikePattern = "(https?://$URL_CHARACTERS+|$URL_CHARACTERS{2,256}\\.[a-z]{2,4}\\b(/$URL_CHARACTERS*)?)".toRegex()

    override val description = "Tekst i SMS/Epost kan ikke inneholde link, eller tekst som ser ut som link"

    override fun validate(varselAction: OpprettVarsel) = assertTrue {
        smsHarIkkeLink(varselAction) && epostHarIkkeLink(varselAction)
    }

    private fun smsHarIkkeLink(varselAction: OpprettVarsel): Boolean {
        val smsTekst = varselAction.eksternVarsling?.smsVarslingstekst

        return smsTekst == null || linkLikePattern.containsMatchIn(smsTekst).not()
    }

    private fun epostHarIkkeLink(varselAction: OpprettVarsel): Boolean {
        val epostTekst = varselAction.eksternVarsling?.epostVarslingstekst

        return epostTekst == null || linkLikePattern.containsMatchIn(epostTekst).not()
    }
}
