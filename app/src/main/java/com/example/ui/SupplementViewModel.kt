package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MedicineRepository
import com.example.data.NetworkMonitor
import com.example.data.PreferencesRepository
import com.example.data.UpdateNotifier
import com.example.domain.AddMedicineOutcome
import com.example.domain.Medicine
import com.example.domain.MedicineCategory
import com.example.domain.MedicineUpdate
import com.example.domain.MedicineUtils
import com.example.domain.UiState
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchMatchType {
    BRAND, GENERIC, COMPANY
}

data class SearchSuggestion(
    val medicine: Medicine,
    val matchType: SearchMatchType
)

class SupplementViewModel(
    private val repository: MedicineRepository,
    private val preferencesRepository: PreferencesRepository,
    networkMonitor: NetworkMonitor,
    private val currentUserId: String,
    private val appContext: Context
) : ViewModel() {

    val medicinesState: StateFlow<UiState<List<Medicine>>> = repository.observeMedicines()
        .map<List<Medicine>, UiState<List<Medicine>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing medicines", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load medicines"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val updatesState: StateFlow<UiState<List<MedicineUpdate>>> = repository.observeMedicineUpdates()
        .map<List<MedicineUpdate>, UiState<List<MedicineUpdate>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing updates", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load updates"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val isOnline: StateFlow<Boolean> = networkMonitor.observeIsOnline()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true
        )

    val language: StateFlow<AppLanguage> = preferencesRepository.languageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppLanguage.ENGLISH
        )

    val notificationsEnabled: StateFlow<Boolean> = preferencesRepository.notificationsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MedicineCategory?>(null)
    val selectedCategory: StateFlow<MedicineCategory?> = _selectedCategory.asStateFlow()

    private var lastNotifiedUpdateId: String? = null
    private var initialSnapshotObserved = false

    val unreadUpdatesCount: StateFlow<Int> = combine(
        updatesState,
        preferencesRepository.lastSeenUpdateMillisFlow
    ) { state, lastSeenMillis ->
        val list = (state as? UiState.Success)?.data ?: return@combine 0
        list.count { update ->
            val millis = update.createdAt?.toDate()?.time ?: 0L
            millis > lastSeenMillis && update.contributorUid != currentUserId
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = 0
    )

    val filteredMedicines: StateFlow<List<Medicine>> = combine(
        medicinesState,
        _searchQuery,
        _selectedCategory
    ) { state, query, category ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val trimmed = MedicineUtils.cleanDisplayInput(query)
        list.filter { med ->
            val matchesCategory = category == null ||
                MedicineCategory.fromKey(med.category) == category
            val matchesQuery = if (trimmed.isEmpty()) {
                true
            } else {
                med.brandName.contains(trimmed, ignoreCase = true) ||
                    med.genericName.contains(trimmed, ignoreCase = true) ||
                    med.companyName.contains(trimmed, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val searchSuggestions: StateFlow<List<SearchSuggestion>> = combine(
        medicinesState,
        _searchQuery
    ) { state, query ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val trimmed = MedicineUtils.cleanDisplayInput(query)
        if (trimmed.isEmpty()) {
            emptyList()
        } else {
            list.mapNotNull { med ->
                when {
                    med.brandName.contains(trimmed, ignoreCase = true) ->
                        SearchSuggestion(med, SearchMatchType.BRAND)
                    med.genericName.contains(trimmed, ignoreCase = true) ->
                        SearchSuggestion(med, SearchMatchType.GENERIC)
                    med.companyName.contains(trimmed, ignoreCase = true) ->
                        SearchSuggestion(med, SearchMatchType.COMPANY)
                    else -> null
                }
            }.take(6)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            combine(
                updatesState,
                notificationsEnabled,
                language
            ) { state, enabled, lang ->
                Triple(state, enabled, lang)
            }.collect { (state, enabled, lang) ->
                val updates = (state as? UiState.Success)?.data ?: return@collect
                val newest = updates.firstOrNull() ?: return@collect
                if (!initialSnapshotObserved) {
                    initialSnapshotObserved = true
                    lastNotifiedUpdateId = newest.id
                    return@collect
                }
                if (newest.id != lastNotifiedUpdateId && newest.contributorUid != currentUserId) {
                    lastNotifiedUpdateId = newest.id
                    if (enabled) {
                        val strings = stringsFor(lang)
                        UpdateNotifier.showUpdateNotification(
                            context = appContext,
                            title = "${newest.brandName} • ${strings.appName}",
                            body = "${newest.contributorName}: ${newest.changeSummary}",
                            notificationId = newest.id.hashCode()
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: MedicineCategory?) {
        _selectedCategory.value = category
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun markUpdatesAsSeen() {
        val updates = (updatesState.value as? UiState.Success)?.data ?: return
        val latestMillis = updates.maxOfOrNull { it.createdAt?.toDate()?.time ?: 0L }
            ?: System.currentTimeMillis()
        viewModelScope.launch {
            preferencesRepository.markUpdatesSeen(maxOf(latestMillis, System.currentTimeMillis()))
        }
    }

    fun findDuplicateCandidate(brandNameInput: String): Medicine? {
        val normalized = MedicineUtils.normalizeBrandName(brandNameInput)
        if (normalized.isEmpty()) return null
        val medicines = (medicinesState.value as? UiState.Success)?.data ?: return null
        return medicines.firstOrNull {
            it.normalizedBrand == normalized ||
                MedicineUtils.normalizeBrandName(it.brandName) == normalized
        }
    }

    fun getMedicineById(medicineId: String): Medicine? {
        val medicines = (medicinesState.value as? UiState.Success)?.data ?: return null
        return medicines.firstOrNull { it.id == medicineId }
    }

    fun getUpdatesForMedicine(medicineId: String): List<MedicineUpdate> {
        val updates = (updatesState.value as? UiState.Success)?.data ?: return emptyList()
        return updates.filter { it.medicineId == medicineId }
    }

    suspend fun addMedicine(
        brandName: String,
        genericName: String,
        companyName: String,
        category: MedicineCategory,
        boxPrice: Double,
        piecesPerBox: Long,
        contributorFallbackName: String
    ): Result<AddMedicineOutcome> = repository.addMedicine(
        brandName = brandName,
        genericName = genericName,
        companyName = companyName,
        category = category,
        boxPrice = boxPrice,
        piecesPerBox = piecesPerBox,
        contributorFallbackName = contributorFallbackName
    )

    suspend fun submitCorrection(
        medicineId: String,
        genericName: String,
        companyName: String,
        category: MedicineCategory,
        boxPrice: Double,
        piecesPerBox: Long,
        note: String,
        contributorFallbackName: String
    ): Result<Unit> = repository.submitCorrection(
        medicineId = medicineId,
        genericName = genericName,
        companyName = companyName,
        category = category,
        boxPrice = boxPrice,
        piecesPerBox = piecesPerBox,
        note = note,
        contributorFallbackName = contributorFallbackName
    )

    private companion object {
        const val TAG = "SupplementVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
