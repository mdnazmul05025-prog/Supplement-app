package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.AddMedicineOutcome
import com.example.domain.Medicine
import com.example.domain.MedicineCategory
import com.example.domain.MedicineUtils
import com.example.localization.LocalAppStrings
import com.example.ui.components.categoryIcon
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicineScreen(
    existingToEdit: Medicine?,
    prefillBrandName: String,
    isOnline: Boolean,
    findDuplicateCandidate: (String) -> Medicine?,
    onSubmitNew: suspend (
        brand: String,
        generic: String,
        company: String,
        category: MedicineCategory,
        boxPrice: Double,
        pieces: Long,
        fallbackName: String
    ) -> Result<AddMedicineOutcome>,
    onSubmitCorrection: suspend (
        medicineId: String,
        generic: String,
        company: String,
        category: MedicineCategory,
        boxPrice: Double,
        pieces: Long,
        note: String,
        fallbackName: String
    ) -> Result<Unit>,
    onNavigateBack: () -> Unit,
    onOpenExistingMedicine: (String) -> Unit,
    onSwitchToEditExisting: (String) -> Unit,
    onShowSuccess: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val isEditMode = existingToEdit != null

    var brandName by remember(existingToEdit?.id, prefillBrandName) {
        mutableStateOf(existingToEdit?.brandName ?: prefillBrandName)
    }
    var genericName by remember(existingToEdit?.id) {
        mutableStateOf(existingToEdit?.genericName.orEmpty())
    }
    var companyName by remember(existingToEdit?.id) {
        mutableStateOf(existingToEdit?.companyName.orEmpty())
    }
    var category by remember(existingToEdit?.id) {
        mutableStateOf(
            if (existingToEdit != null) MedicineCategory.fromKey(existingToEdit.category)
            else MedicineCategory.TABLET
        )
    }
    var boxPriceText by remember(existingToEdit?.id) {
        mutableStateOf(
            if (existingToEdit != null && existingToEdit.boxPrice > 0) {
                if (existingToEdit.boxPrice % 1.0 == 0.0) existingToEdit.boxPrice.toLong().toString()
                else existingToEdit.boxPrice.toString()
            } else ""
        )
    }
    var piecesText by remember(existingToEdit?.id) {
        mutableStateOf(
            if (existingToEdit != null && existingToEdit.piecesPerBox > 0) {
                existingToEdit.piecesPerBox.toString()
            } else ""
        )
    }
    var correctionNote by remember(existingToEdit?.id) { mutableStateOf("") }

    var formError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var duplicateDetectedDialog by remember { mutableStateOf<Medicine?>(null) }

    LaunchedEffect(existingToEdit?.id) {
        formError = null
        duplicateDetectedDialog = null
    }

    val liveDuplicateMatch = if (!isEditMode) findDuplicateCandidate(brandName) else null
    val parsedBoxPrice = boxPriceText.trim().toDoubleOrNull() ?: 0.0
    val parsedPieces = piecesText.trim().toLongOrNull() ?: 0L
    val calculatedPerPiece = MedicineUtils.calculatePricePerPiece(parsedBoxPrice, parsedPieces)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) strings.editMedicineTitle else strings.addMedicineTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("add_edit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 760.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!isOnline) {
                        Surface(
                            color = AmberWarningContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberWarning
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.offlineTransactionWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberWarning
                                )
                            }
                        }
                    }

                    if (liveDuplicateMatch != null) {
                        DuplicateFoundBanner(
                            existing = liveDuplicateMatch,
                            onViewExisting = { onOpenExistingMedicine(liveDuplicateMatch.id) },
                            onEditExisting = { onSwitchToEditExisting(liveDuplicateMatch.id) }
                        )
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            FormInputsCard(
                                isEditMode = isEditMode,
                                brandName = brandName,
                                onBrandNameChange = { brandName = it; formError = null },
                                genericName = genericName,
                                onGenericNameChange = { genericName = it; formError = null },
                                companyName = companyName,
                                onCompanyNameChange = { companyName = it; formError = null },
                                category = category,
                                onCategoryChange = { category = it },
                                boxPriceText = boxPriceText,
                                onBoxPriceChange = { boxPriceText = filterDecimal(it); formError = null },
                                piecesText = piecesText,
                                onPiecesChange = { piecesText = it.filter { ch -> ch.isDigit() }; formError = null },
                                correctionNote = correctionNote,
                                onCorrectionNoteChange = { correctionNote = it },
                                modifier = Modifier.weight(1.3f)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                LivePriceCalculatorCard(
                                    boxPrice = parsedBoxPrice,
                                    piecesPerBox = parsedPieces,
                                    pricePerPiece = calculatedPerPiece
                                )
                                SubmitSection(
                                    isEditMode = isEditMode,
                                    isSubmitting = isSubmitting,
                                    hasLiveDuplicate = liveDuplicateMatch != null,
                                    formError = formError,
                                    onSubmit = {
                                        val cleanBrand = MedicineUtils.cleanDisplayInput(brandName)
                                        val cleanGeneric = MedicineUtils.cleanDisplayInput(genericName)
                                        val cleanCompany = MedicineUtils.cleanDisplayInput(companyName)
                                        when {
                                            cleanBrand.isEmpty() -> formError = strings.validationBrandRequired
                                            cleanGeneric.isEmpty() -> formError = strings.validationGenericRequired
                                            cleanCompany.isEmpty() -> formError = strings.validationCompanyRequired
                                            parsedBoxPrice <= 0.0 -> formError = strings.validationBoxPriceInvalid
                                            parsedPieces < 1L -> formError = strings.validationPiecesInvalid
                                            else -> {
                                                isSubmitting = true
                                                formError = null
                                                scope.launch {
                                                    if (isEditMode && existingToEdit != null) {
                                                        val res = onSubmitCorrection(
                                                            existingToEdit.id,
                                                            cleanGeneric,
                                                            cleanCompany,
                                                            category,
                                                            parsedBoxPrice,
                                                            parsedPieces,
                                                            correctionNote,
                                                            strings.contributorFallback
                                                        )
                                                        isSubmitting = false
                                                        res.onSuccess {
                                                            onShowSuccess(strings.correctionSavedSuccess)
                                                            onNavigateBack()
                                                        }.onFailure { err ->
                                                            formError = err.localizedMessage ?: strings.offlineTransactionWarning
                                                        }
                                                    } else {
                                                        val res = onSubmitNew(
                                                            cleanBrand,
                                                            cleanGeneric,
                                                            cleanCompany,
                                                            category,
                                                            parsedBoxPrice,
                                                            parsedPieces,
                                                            strings.contributorFallback
                                                        )
                                                        isSubmitting = false
                                                        res.onSuccess { outcome ->
                                                            when (outcome) {
                                                                is AddMedicineOutcome.Created -> {
                                                                    onShowSuccess(strings.medicineAddedSuccess)
                                                                    onOpenExistingMedicine(outcome.medicineId)
                                                                }
                                                                is AddMedicineOutcome.DuplicateFound -> {
                                                                    duplicateDetectedDialog = outcome.existingMedicine
                                                                }
                                                            }
                                                        }.onFailure { err ->
                                                            formError = err.localizedMessage ?: strings.offlineTransactionWarning
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        FormInputsCard(
                            isEditMode = isEditMode,
                            brandName = brandName,
                            onBrandNameChange = { brandName = it; formError = null },
                            genericName = genericName,
                            onGenericNameChange = { genericName = it; formError = null },
                            companyName = companyName,
                            onCompanyNameChange = { companyName = it; formError = null },
                            category = category,
                            onCategoryChange = { category = it },
                            boxPriceText = boxPriceText,
                            onBoxPriceChange = { boxPriceText = filterDecimal(it); formError = null },
                            piecesText = piecesText,
                            onPiecesChange = { piecesText = it.filter { ch -> ch.isDigit() }; formError = null },
                            correctionNote = correctionNote,
                            onCorrectionNoteChange = { correctionNote = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        LivePriceCalculatorCard(
                            boxPrice = parsedBoxPrice,
                            piecesPerBox = parsedPieces,
                            pricePerPiece = calculatedPerPiece
                        )

                        SubmitSection(
                            isEditMode = isEditMode,
                            isSubmitting = isSubmitting,
                            hasLiveDuplicate = liveDuplicateMatch != null,
                            formError = formError,
                            onSubmit = {
                                val cleanBrand = MedicineUtils.cleanDisplayInput(brandName)
                                val cleanGeneric = MedicineUtils.cleanDisplayInput(genericName)
                                val cleanCompany = MedicineUtils.cleanDisplayInput(companyName)
                                when {
                                    cleanBrand.isEmpty() -> formError = strings.validationBrandRequired
                                    cleanGeneric.isEmpty() -> formError = strings.validationGenericRequired
                                    cleanCompany.isEmpty() -> formError = strings.validationCompanyRequired
                                    parsedBoxPrice <= 0.0 -> formError = strings.validationBoxPriceInvalid
                                    parsedPieces < 1L -> formError = strings.validationPiecesInvalid
                                    else -> {
                                        isSubmitting = true
                                        formError = null
                                        scope.launch {
                                            if (isEditMode && existingToEdit != null) {
                                                val res = onSubmitCorrection(
                                                    existingToEdit.id,
                                                    cleanGeneric,
                                                    cleanCompany,
                                                    category,
                                                    parsedBoxPrice,
                                                    parsedPieces,
                                                    correctionNote,
                                                    strings.contributorFallback
                                                )
                                                isSubmitting = false
                                                res.onSuccess {
                                                    onShowSuccess(strings.correctionSavedSuccess)
                                                    onNavigateBack()
                                                }.onFailure { err ->
                                                    formError = err.localizedMessage ?: strings.offlineTransactionWarning
                                                }
                                            } else {
                                                val res = onSubmitNew(
                                                    cleanBrand,
                                                    cleanGeneric,
                                                    cleanCompany,
                                                    category,
                                                    parsedBoxPrice,
                                                    parsedPieces,
                                                    strings.contributorFallback
                                                )
                                                isSubmitting = false
                                                res.onSuccess { outcome ->
                                                    when (outcome) {
                                                        is AddMedicineOutcome.Created -> {
                                                            onShowSuccess(strings.medicineAddedSuccess)
                                                            onOpenExistingMedicine(outcome.medicineId)
                                                        }
                                                        is AddMedicineOutcome.DuplicateFound -> {
                                                            duplicateDetectedDialog = outcome.existingMedicine
                                                        }
                                                    }
                                                }.onFailure { err ->
                                                    formError = err.localizedMessage ?: strings.offlineTransactionWarning
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    duplicateDetectedDialog?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicateDetectedDialog = null },
            title = { Text(strings.duplicateDetectedTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.duplicateDetectedBody)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = existing.brandName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("${strings.genericInfo}: ${existing.genericName}")
                    Text("${strings.manufacturerInfo}: ${existing.companyName}")
                    Text(
                        "${strings.pricePerPieceLabel}: ${MedicineUtils.formatBdt(existing.pricePerPiece, showCurrencyWord = true)}"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        duplicateDetectedDialog = null
                        onSwitchToEditExisting(existing.id)
                    }
                ) {
                    Text(strings.updateExistingRecord)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        duplicateDetectedDialog = null
                        onOpenExistingMedicine(existing.id)
                    }
                ) {
                    Text(strings.viewExistingRecord)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormInputsCard(
    isEditMode: Boolean,
    brandName: String,
    onBrandNameChange: (String) -> Unit,
    genericName: String,
    onGenericNameChange: (String) -> Unit,
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    category: MedicineCategory,
    onCategoryChange: (MedicineCategory) -> Unit,
    boxPriceText: String,
    onBoxPriceChange: (String) -> Unit,
    piecesText: String,
    onPiecesChange: (String) -> Unit,
    correctionNote: String,
    onCorrectionNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = brandName,
                onValueChange = onBrandNameChange,
                enabled = !isEditMode,
                label = { Text(strings.brandNameLabel) },
                placeholder = { Text(strings.brandNameHint) },
                supportingText = if (isEditMode) {
                    { Text(strings.brandLockedHint) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("brand_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = genericName,
                onValueChange = onGenericNameChange,
                label = { Text(strings.genericNameLabel) },
                placeholder = { Text(strings.genericNameHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generic_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = companyName,
                onValueChange = onCompanyNameChange,
                label = { Text(strings.companyNameLabel) },
                placeholder = { Text(strings.companyNameHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("company_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Column {
                Text(
                    text = strings.categoryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MedicineCategory.entries.forEach { cat ->
                        val selected = category == cat
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryChange(cat) },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIcon(cat),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(strings.categoryLabel(cat)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("category_chip_${cat.key}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = boxPriceText,
                    onValueChange = onBoxPriceChange,
                    label = { Text(strings.boxPriceLabel) },
                    placeholder = { Text("120") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("box_price_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = piecesText,
                    onValueChange = onPiecesChange,
                    label = { Text(strings.piecesPerBoxLabel) },
                    placeholder = { Text("10") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pieces_per_box_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (isEditMode) {
                OutlinedTextField(
                    value = correctionNote,
                    onValueChange = onCorrectionNoteChange,
                    label = { Text(strings.changeSummaryNoteLabel) },
                    placeholder = { Text(strings.changeSummaryNoteHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("correction_note_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LivePriceCalculatorCard(
    boxPrice: Double,
    piecesPerBox: Long,
    pricePerPiece: Double
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_price_calculator_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.autoPriceCalculator,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.priceFormulaLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.pricePerPieceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (boxPrice > 0 && piecesPerBox > 0) {
                            Text(
                                text = "${MedicineUtils.formatBdt(boxPrice)} ÷ $piecesPerBox ${strings.pcsShort}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = if (pricePerPiece > 0.0) {
                            MedicineUtils.formatBdt(pricePerPiece, showCurrencyWord = true)
                        } else {
                            "— BDT"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("calculated_price_per_piece")
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateFoundBanner(
    existing: Medicine,
    onViewExisting: () -> Unit,
    onEditExisting: () -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_warning_banner"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = AmberWarningContainer),
        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = AmberWarning
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.duplicateDetectedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.duplicateDetectedBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${existing.brandName} • ${existing.genericName} • ${MedicineUtils.formatBdt(existing.pricePerPiece)}${strings.perPieceShort}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onViewExisting,
                    modifier = Modifier.testTag("duplicate_view_existing_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.viewExistingRecord)
                }
                Button(
                    onClick = onEditExisting,
                    modifier = Modifier.testTag("duplicate_edit_existing_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.updateExistingRecord)
                }
            }
        }
    }
}

@Composable
private fun SubmitSection(
    isEditMode: Boolean,
    isSubmitting: Boolean,
    hasLiveDuplicate: Boolean,
    formError: String?,
    onSubmit: () -> Unit
) {
    val strings = LocalAppStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (formError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting && !hasLiveDuplicate,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_medicine_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditMode) strings.submitCorrection else strings.submitMedicine,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun filterDecimal(raw: String): String {
    var dotSeen = false
    return buildString {
        for (ch in raw) {
            if (ch.isDigit()) {
                append(ch)
            } else if (ch == '.' && !dotSeen) {
                dotSeen = true
                append(ch)
            }
        }
    }
}
