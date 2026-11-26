package com.example

import com.example.domain.MedicineUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun pricePerPiece_dividesBoxPriceByPieces() {
        assertEquals(12.0, MedicineUtils.calculatePricePerPiece(120.0, 10L), 0.0001)
        assertEquals(1.2, MedicineUtils.calculatePricePerPiece(120.0, 100L), 0.0001)
        assertEquals(0.0, MedicineUtils.calculatePricePerPiece(120.0, 0L), 0.0001)
    }

    @Test
    fun brandNormalization_ignoresCaseAndExtraWhitespace() {
        val idA = MedicineUtils.generateMedicineDocId("Napa 500mg")
        val idB = MedicineUtils.generateMedicineDocId("  napa   500MG ")
        assertEquals(idA, idB)
        assertEquals("napa 500mg", MedicineUtils.normalizeBrandName("  NAPA   500mg "))
    }
}
