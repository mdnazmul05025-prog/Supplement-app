package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AddMedicineOutcome
import com.example.domain.Medicine
import com.example.domain.MedicineCategory
import com.example.domain.MedicineUpdate
import com.example.domain.MedicineUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MedicineRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun currentContributorName(fallbackName: String): String {
        val raw = auth.currentUser?.displayName?.trim().orEmpty()
        val cleaned = MedicineUtils.cleanDisplayInput(raw)
        return if (cleaned.isNotEmpty()) cleaned.take(80) else fallbackName.take(80)
    }

    fun observeMedicines(): Flow<List<Medicine>> = flow {
        val uid = requireUserId()
        val path = "medicines"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "approved")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Medicine::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMedicineUpdates(): Flow<List<MedicineUpdate>> = flow {
        val uid = requireUserId()
        val path = "medicine_updates"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "approved")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MedicineUpdate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMedicine(
        brandName: String,
        genericName: String,
        companyName: String,
        category: MedicineCategory,
        boxPrice: Double,
        piecesPerBox: Long,
        contributorFallbackName: String
    ): Result<AddMedicineOutcome> {
        val cleanBrand = MedicineUtils.cleanDisplayInput(brandName).take(120)
        val normalizedBrand = MedicineUtils.normalizeBrandName(cleanBrand).take(120)
        val cleanGeneric = MedicineUtils.cleanDisplayInput(genericName).take(160)
        val cleanCompany = MedicineUtils.cleanDisplayInput(companyName).take(140)
        val docId = MedicineUtils.generateMedicineDocId(cleanBrand)
        val docPath = "medicines/$docId"

        return try {
            val uid = requireUserId()
            require(cleanBrand.isNotEmpty()) { "Brand name is required" }
            require(cleanGeneric.isNotEmpty()) { "Generic name is required" }
            require(cleanCompany.isNotEmpty()) { "Company name is required" }
            require(boxPrice > 0.0 && boxPrice <= 1_000_000.0) { "Invalid box price" }
            require(piecesPerBox in 1L..10_000L) { "Invalid pieces per box" }

            val pricePerPiece = MedicineUtils.calculatePricePerPiece(boxPrice, piecesPerBox)
            require(pricePerPiece > 0.0 && pricePerPiece <= 1_000_000.0) { "Invalid calculated price" }

            val contributorName = currentContributorName(contributorFallbackName)
            val medRef = db.collection("medicines").document(docId)
            val updateId = "upd_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val updateRef = db.collection("medicine_updates").document(updateId)

            val outcome = db.runTransaction { transaction ->
                val existingSnap = transaction.get(medRef)
                if (existingSnap.exists()) {
                    val existingMedicine = existingSnap.toObject(
                        Medicine::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: Medicine(
                        id = existingSnap.id,
                        brandName = existingSnap.getString("brandName").orEmpty(),
                        normalizedBrand = existingSnap.getString("normalizedBrand").orEmpty(),
                        genericName = existingSnap.getString("genericName").orEmpty(),
                        companyName = existingSnap.getString("companyName").orEmpty(),
                        category = existingSnap.getString("category") ?: MedicineCategory.OTHER.key,
                        boxPrice = existingSnap.getDouble("boxPrice") ?: 0.0,
                        piecesPerBox = existingSnap.getLong("piecesPerBox") ?: 1L,
                        pricePerPiece = existingSnap.getDouble("pricePerPiece") ?: 0.0,
                        status = existingSnap.getString("status") ?: "approved",
                        createdByUid = existingSnap.getString("createdByUid").orEmpty(),
                        createdByName = existingSnap.getString("createdByName").orEmpty(),
                        lastUpdatedByUid = existingSnap.getString("lastUpdatedByUid").orEmpty(),
                        lastUpdatedByName = existingSnap.getString("lastUpdatedByName").orEmpty(),
                        createdAt = existingSnap.getTimestamp("createdAt"),
                        updatedAt = existingSnap.getTimestamp("updatedAt")
                    )
                    return@runTransaction AddMedicineOutcome.DuplicateFound(existingMedicine)
                }

                val medicinePayload = mapOf(
                    "brandName" to cleanBrand,
                    "normalizedBrand" to normalizedBrand,
                    "genericName" to cleanGeneric,
                    "companyName" to cleanCompany,
                    "category" to category.key,
                    "boxPrice" to boxPrice,
                    "piecesPerBox" to piecesPerBox,
                    "pricePerPiece" to pricePerPiece,
                    "status" to "approved",
                    "createdByUid" to uid,
                    "createdByName" to contributorName,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to contributorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val updatePayload = mapOf(
                    "medicineId" to docId,
                    "brandName" to cleanBrand,
                    "genericName" to cleanGeneric,
                    "companyName" to cleanCompany,
                    "category" to category.key,
                    "boxPrice" to boxPrice,
                    "piecesPerBox" to piecesPerBox,
                    "pricePerPiece" to pricePerPiece,
                    "actionType" to "CREATE",
                    "changeSummary" to "${category.key} • ${MedicineUtils.formatBdt(boxPrice)} / $piecesPerBox pcs",
                    "status" to "approved",
                    "contributorUid" to uid,
                    "contributorName" to contributorName,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                transaction.set(medRef, medicinePayload)
                transaction.set(updateRef, updatePayload)
                AddMedicineOutcome.Created(docId)
            }.await()

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitCorrection(
        medicineId: String,
        genericName: String,
        companyName: String,
        category: MedicineCategory,
        boxPrice: Double,
        piecesPerBox: Long,
        note: String,
        contributorFallbackName: String
    ): Result<Unit> {
        val docPath = "medicines/$medicineId"
        return try {
            val uid = requireUserId()
            require(medicineId.isNotBlank()) { "Medicine ID is required" }

            val cleanGeneric = MedicineUtils.cleanDisplayInput(genericName).take(160)
            val cleanCompany = MedicineUtils.cleanDisplayInput(companyName).take(140)
            val cleanNote = MedicineUtils.cleanDisplayInput(note).take(300)

            require(cleanGeneric.isNotEmpty()) { "Generic name is required" }
            require(cleanCompany.isNotEmpty()) { "Company name is required" }
            require(boxPrice > 0.0 && boxPrice <= 1_000_000.0) { "Invalid box price" }
            require(piecesPerBox in 1L..10_000L) { "Invalid pieces per box" }

            val pricePerPiece = MedicineUtils.calculatePricePerPiece(boxPrice, piecesPerBox)
            require(pricePerPiece > 0.0 && pricePerPiece <= 1_000_000.0) { "Invalid calculated price" }

            val contributorName = currentContributorName(contributorFallbackName)
            val medRef = db.collection("medicines").document(medicineId)
            val updateId = "upd_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val updateRef = db.collection("medicine_updates").document(updateId)

            db.runTransaction { transaction ->
                val snap = transaction.get(medRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Medicine record no longer exists.")
                }
                val brandName = snap.getString("brandName").orEmpty()
                val oldBoxPrice = snap.getDouble("boxPrice") ?: 0.0
                val oldPieces = snap.getLong("piecesPerBox") ?: 1L
                val oldGeneric = snap.getString("genericName").orEmpty()

                val summary = buildString {
                    if (cleanNote.isNotEmpty()) {
                        append(cleanNote)
                    } else {
                        val parts = mutableListOf<String>()
                        if (oldBoxPrice != boxPrice || oldPieces != piecesPerBox) {
                            parts.add(
                                "${MedicineUtils.formatBdt(oldBoxPrice)}/$oldPieces → ${MedicineUtils.formatBdt(boxPrice)}/$piecesPerBox"
                            )
                        }
                        if (!oldGeneric.equals(cleanGeneric, ignoreCase = true)) {
                            parts.add("Generic: $cleanGeneric")
                        }
                        if (parts.isEmpty()) {
                            parts.add("${category.key} • ${MedicineUtils.formatBdt(pricePerPiece)}/pc")
                        }
                        append(parts.joinToString(" • "))
                    }
                }.take(300)

                transaction.update(
                    medRef,
                    mapOf(
                        "genericName" to cleanGeneric,
                        "companyName" to cleanCompany,
                        "category" to category.key,
                        "boxPrice" to boxPrice,
                        "piecesPerBox" to piecesPerBox,
                        "pricePerPiece" to pricePerPiece,
                        "lastUpdatedByUid" to uid,
                        "lastUpdatedByName" to contributorName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.set(
                    updateRef,
                    mapOf(
                        "medicineId" to medicineId,
                        "brandName" to brandName,
                        "genericName" to cleanGeneric,
                        "companyName" to cleanCompany,
                        "category" to category.key,
                        "boxPrice" to boxPrice,
                        "piecesPerBox" to piecesPerBox,
                        "pricePerPiece" to pricePerPiece,
                        "actionType" to "UPDATE",
                        "changeSummary" to summary,
                        "status" to "approved",
                        "contributorUid" to uid,
                        "contributorName" to contributorName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
