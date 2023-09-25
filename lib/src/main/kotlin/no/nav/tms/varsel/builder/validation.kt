package no.nav.tms.varsel.builder

import java.net.MalformedURLException
import java.net.URL
import java.util.UUID

private const val BASE_16 = "[0-9a-fA-F]"
private const val BASE_32_ULID = "[0-9ABCDEFGHJKMNPQRSTVWXYZabcdefghjkmnpqrstvwxyz]"

private val UUID_PATTERN = "^$BASE_16{8}-$BASE_16{4}-$BASE_16{4}-$BASE_16{4}-$BASE_16{12}$".toRegex()
private val ULID_PATTERN = "^[0-7]$BASE_32_ULID{25}$".toRegex()

class VarselValidation(opprettopprettVarsel: OpprettVarsel) {



    val failedValidators: List<OpprettVarselValidator> = getFailedVarselValidators(opprettVarsel)

    fun isValid(): Boolean = failedValidators.isEmpty()

    private fun getFailedVarselValidators(opprettVarsel: OpprettVarsel) = listOf(
        TekstValidator(),
        LinkValidator(),
        SikkerhetsnivaaValidator(),
        PrefererteKanalerValidator(),
        SmstekstValidator(),
        EposttekstValidator(),
        EposttittelValidator(),
        IdentValidator(),
        VarselIdValidator()

    ).filter { !it.validate(opprettVarsel) }
}

interface OpprettVarselValidator {
    val description: String

    fun validate(opprettVarsel: VarselBuilder.OpprettVarselInstance): Boolean
}

class IdentValidator : OpprettVarselValidator {
    override val description: String = "Fodselsnummer må være 11 tegn"

    override fun validate(opprettVarsel: VarselBuilder.OpprettVarselInstance): Boolean =
        opprettVarsel.ident?.length == 11
}

class VarselIdValidator : OpprettVarselValidator {
    override val description: String = "Eventid må være gyldig UUID eller ULID"

    override fun validate(opprettVarsel: VarselBuilder.OpprettVarselInstance): Boolean =
        opprettVarsel.varselId?.let {
            it.isValidUuid() || it.isValidUlid()
        } ?: false

    private fun String.isValidUuid(): Boolean =
        try {
            UUID_PATTERN.matches(this)
        } catch (e: IllegalArgumentException) {
            false
        }

    private fun String.isValidUlid(): Boolean =
        try {
            ULID_PATTERN.matches(this)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
}

class TekstValidator : OpprettVarselValidator {
    private val maxTextLengthBeskjed = 300
    private val maxTextLengthOppgaveAndInnboks = 500

    private val fieldName = "tekst"
    override val description: String = "Tekst kan ikke være null, og over makslengde"

    override fun validate(opprettVarsel: VarselBuilder.OpprettVarselInstance): Boolean {
        val maxLength = when (opprettVarsel.type) {
            VarselType.Beskjed -> maxTextLengthBeskjed
            else -> maxTextLengthOppgaveAndInnboks
        }
        return record.isNotNull(fieldName) && (record.get(fieldName) as String).length <= maxLength
    }
}

class LinkValidator : OpprettVarselValidator {
    private val MAX_LENGTH_LINK = 200
    private val fieldName = "link"
    override val description: String = "Link må være gyldig URL og maks $MAX_LENGTH_LINK tegn"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean {
        return record.isNull(fieldName)
            || isEmptyBeskjedLink(record)
            || isValidURL(record.get(fieldName) as String)
    }

    private fun isEmptyBeskjedLink(opprettVarsel: OpprettVarsel): Boolean {
        return record.schema.name == "BeskjedInput" && (record.get(fieldName) as String) == ""
    }

    private fun isValidURL(link: String) =
        link.length <= MAX_LENGTH_LINK && try {
            URL(link)
            true
        } catch (e: MalformedURLException) {
            false
        }
}

class SikkerhetsnivaaValidator : OpprettVarselValidator {
    private val fieldName = "sikkerhetsnivaa"
    override val description: String = "Sikkerhetsnivaa må være 3 eller 4, default er 4"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean =
        record.isNull(fieldName) || (record.get(fieldName) as Int) in listOf(3, 4)
}

class PrefererteKanalerValidator : OpprettVarselValidator {
    private val fieldName = "prefererteKanaler"
    override val description: String = "Preferte kanaler kan bare inneholde SMS og EPOST"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean =
        record.isNull(fieldName) || (record.get(fieldName) as List<*>).all { it in listOf("SMS", "EPOST") }
}

class SmstekstValidator : OpprettVarselValidator {
    private val MAX_LENGTH_SMS_VARSLINGSTEKST = 160
    private val fieldName = "smsVarslingstekst"
    override val description: String =
        "Sms-varsel kan ikke være tom string, og maks $MAX_LENGTH_SMS_VARSLINGSTEKST tegn"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean =
        record.isNull(fieldName) || (record.get(fieldName) as String).trim().let {
            it.isNotEmpty() && it.length <= MAX_LENGTH_SMS_VARSLINGSTEKST
        }
}

class EposttekstValidator : OpprettVarselValidator {
    private val MAX_LENGTH_EPOST_VARSLINGSTEKST = 4000
    private val fieldName = "epostVarslingstekst"
    override val description: String =
        "Epost-tekst kan ikke være tom string, og maks $MAX_LENGTH_EPOST_VARSLINGSTEKST tegn"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean =
        record.isNull(fieldName) || (record.get(fieldName) as String).trim().let {
            it.isNotEmpty() && it.length <= MAX_LENGTH_EPOST_VARSLINGSTEKST
        }
}

class EposttittelValidator : OpprettVarselValidator {
    private val MAX_LENGTH_EPOST_VARSLINGSTTITTEL = 40
    private val fieldName = "epostVarslingstittel"
    override val description: String =
        "Epost-tittel kan ikke være tom string, og maks $MAX_LENGTH_EPOST_VARSLINGSTTITTEL tegn"

    override fun validate(opprettVarsel: OpprettVarsel): Boolean =
        record.isNull(fieldName) || (record.get(fieldName) as String).trim().let {
            it.isNotEmpty() && it.length <= MAX_LENGTH_EPOST_VARSLINGSTTITTEL
        }
}

private fun GenericRecord.isNotNull(fieldName: String): Boolean = hasField(fieldName) && get(fieldName) != null
private fun GenericRecord.isNull(fieldName: String): Boolean = !hasField(fieldName) || get(fieldName) == null
