package io.cosgriff.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeTest {
    @Test
    fun `a UPC-A with a valid check digit is a product`() {
        assertEquals(ScannedCode.Product("096619321087"), readRetailCode("096619321087"))
    }

    @Test
    fun `an EAN-13 is a product`() {
        assertEquals(ScannedCode.Product("4006381333931"), readRetailCode("4006381333931"))
    }

    @Test
    fun `an EAN-13 starting with 0 is kept as its UPC-A`() {
        assertEquals(ScannedCode.Product("096619321087"), readRetailCode("0096619321087"))
    }

    @Test
    fun `a wrong check digit is invalid`() {
        assertEquals(ScannedCode.Invalid, readRetailCode("096619321088"))
    }

    @Test
    fun `other lengths and letters are invalid`() {
        assertEquals(ScannedCode.Invalid, readRetailCode("12345678"))
        assertEquals(ScannedCode.Invalid, readRetailCode("09661932108X"))
        assertEquals(ScannedCode.Invalid, readRetailCode(""))
    }

    @Test
    fun `store-packed labels are variable weight`() {
        // UPC-A number system 2.
        assertEquals(ScannedCode.VariableWeight("212345006993"), readRetailCode("212345006993"))
        // EAN-13 prefix 20-29.
        assertEquals(ScannedCode.VariableWeight("2912345012342"), readRetailCode("2912345012342"))
    }
}
