package com.example.domain

import com.google.firebase.Timestamp
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

object MedicineUtils {

    fun cleanDisplayInput(input: String): String =
        input.trim().replace(Regex("\\s+"), " ")

    fun normalizeBrandName(brandName: String): String =
        cleanDisplayInput(brandName).lowercase(Locale.ROOT)

    fun generateMedicineDocId(brandName: String): String {
        val normalized = normalizeBrandName(brandName)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return "med_$hex"
    }

    fun calculatePricePerPiece(boxPrice: Double, piecesPerBox: Long): Double {
        if (boxPrice <= 0.0 || piecesPerBox <= 0L) return 0.0
        val raw = boxPrice / piecesPerBox.toDouble()
        return (raw * 10000.0).roundToLong() / 10000.0
    }

    fun formatBdt(amount: Double, showCurrencyWord: Boolean = false): String {
        val formatted = if (amount % 1.0 == 0.0) {
            DecimalFormat("#,##0").format(amount.roundToLong())
        } else {
            DecimalFormat("#,##0.##").format(amount)
        }
        return if (showCurrencyWord) "$formatted BDT" else "৳$formatted"
    }

    fun formatTimestamp(timestamp: Timestamp?, isBengali: Boolean): String {
        if (timestamp == null) return if (isBengali) "এইমাত্র" else "Just now"
        val locale = if (isBengali) Locale("bn", "BD") else Locale.US
        val pattern = "dd MMM yyyy, hh:mm a"
        return SimpleDateFormat(pattern, locale).format(timestamp.toDate())
    }

    fun formatShortDate(timestamp: Timestamp?, isBengali: Boolean): String {
        if (timestamp == null) return if (isBengali) "আজ" else "Today"
        val locale = if (isBengali) Locale("bn", "BD") else Locale.US
        return SimpleDateFormat("dd MMM yyyy", locale).format(timestamp.toDate())
    }
}
