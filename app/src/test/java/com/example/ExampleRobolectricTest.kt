package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.MedicineUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Supplement`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Supplement", appName)
    }

    @Test
    fun `price per piece calculator divides box price by pieces`() {
        assertEquals(12.0, MedicineUtils.calculatePricePerPiece(120.0, 10L), 0.0001)
        assertEquals(1.2, MedicineUtils.calculatePricePerPiece(120.0, 100L), 0.0001)
        assertEquals(0.0, MedicineUtils.calculatePricePerPiece(120.0, 0L), 0.0001)
    }

    @Test
    fun `brand normalization ignores case and extra whitespace`() {
        val idA = MedicineUtils.generateMedicineDocId("Napa 500mg")
        val idB = MedicineUtils.generateMedicineDocId("  napa   500MG ")
        assertEquals(idA, idB)
        assertEquals("napa 500mg", MedicineUtils.normalizeBrandName("  NAPA   500mg "))
    }
}
