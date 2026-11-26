package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MedicineCategory(val key: String) {
    TABLET("Tablet"),
    CAPSULE("Capsule"),
    SYRUP("Syrup"),
    INJECTION("Injection"),
    SOAP("Soap"),
    SHAMPOO("Shampoo"),
    LOTION("Lotion"),
    OTHER("Other");

    companion object {
        fun fromKey(raw: String?): MedicineCategory =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class Medicine(
    @DocumentId val id: String = "",
    val brandName: String = "",
    val normalizedBrand: String = "",
    val genericName: String = "",
    val companyName: String = "",
    val category: String = MedicineCategory.TABLET.key,
    val boxPrice: Double = 0.0,
    val piecesPerBox: Long = 1L,
    val pricePerPiece: Double = 0.0,
    val status: String = "approved",
    val createdByUid: String = "",
    val createdByName: String = "",
    val lastUpdatedByUid: String = "",
    val lastUpdatedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MedicineUpdate(
    @DocumentId val id: String = "",
    val medicineId: String = "",
    val brandName: String = "",
    val genericName: String = "",
    val companyName: String = "",
    val category: String = MedicineCategory.TABLET.key,
    val boxPrice: Double = 0.0,
    val piecesPerBox: Long = 1L,
    val pricePerPiece: Double = 0.0,
    val actionType: String = "CREATE",
    val changeSummary: String = "",
    val status: String = "approved",
    val contributorUid: String = "",
    val contributorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface AddMedicineOutcome {
    data class Created(val medicineId: String) : AddMedicineOutcome
    data class DuplicateFound(val existingMedicine: Medicine) : AddMedicineOutcome
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
