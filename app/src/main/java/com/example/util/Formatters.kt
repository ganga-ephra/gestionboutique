package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Formatters {
    private val frenchSymbols = DecimalFormatSymbols(Locale.FRANCE).apply {
        groupingSeparator = ' '
    }
    private val intFormatter = DecimalFormat("#,##0", frenchSymbols)

    fun formatFcfa(amount: Long): String = "${intFormatter.format(amount)} FCFA"

    fun formatInt(value: Int): String = intFormatter.format(value)

    fun businessDateFromUtc(utcMillis: Long, zoneId: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = safeTimeZone(zoneId)
        return sdf.format(Date(utcMillis))
    }

    fun formatDisplayDate(dateMetier: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
            val parsed = parser.parse(dateMetier) ?: return dateMetier
            formatter.format(parsed)
        } catch (_: Exception) {
            dateMetier
        }
    }

    fun formatDateTimeUtc(utcMillis: Long, zoneId: String): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        sdf.timeZone = safeTimeZone(zoneId)
        return sdf.format(Date(utcMillis))
    }

    fun formatTimeUtc(utcMillis: Long, zoneId: String): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
        sdf.timeZone = safeTimeZone(zoneId)
        return sdf.format(Date(utcMillis))
    }

    fun compactDateForNumber(dateMetier: String): String = dateMetier.replace("-", "")

    fun safeTimeZone(zoneId: String): TimeZone {
        return if (zoneId.isBlank()) {
            TimeZone.getTimeZone("Africa/Brazzaville")
        } else {
            TimeZone.getTimeZone(zoneId)
        }
    }

    /**
     * Protects CSV cells against spreadsheet formula injection (T18)
     * while preserving UTF-8 accented characters and proper RFC-4180 quoting.
     */
    fun csvSafeCell(rawValue: String): String {
        val normalized = rawValue.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val trimmedStart = normalized.trimStart()
        val protectedValue = if (
            trimmedStart.startsWith("=") ||
            trimmedStart.startsWith("+") ||
            trimmedStart.startsWith("-") ||
            trimmedStart.startsWith("@") ||
            normalized.startsWith("\t")
        ) {
            "'$normalized"
        } else {
            normalized
        }
        val escaped = protectedValue.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
