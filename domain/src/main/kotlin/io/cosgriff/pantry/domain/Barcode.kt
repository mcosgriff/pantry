package io.cosgriff.pantry.domain

/** What a scanned retail barcode is, for the Scan screen (#10). */
sealed interface ScannedCode {
    /** A product code. [code] is 12 digits for a UPC-A (an EAN-13 starting with 0 included), else 13. */
    data class Product(val code: String) : ScannedCode

    /**
     * A store-packed label (UPC-A starting with 2, EAN-13 starting 20–29): it carries the item and its weight or
     * price, not a product, so it is entered by hand (#13).
     */
    data class VariableWeight(val code: String) : ScannedCode

    /** Not a UPC-A or EAN-13, or its check digit is wrong. */
    data object Invalid : ScannedCode
}

/** Reads the text of a scanned UPC-A or EAN-13 barcode. */
fun readRetailCode(text: String): ScannedCode {
    val digits = text.trim()
    if (!digits.all(Char::isDigit) || (digits.length != 12 && digits.length != 13)) return ScannedCode.Invalid
    if (!hasValidCheckDigit(digits)) return ScannedCode.Invalid
    // An EAN-13 starting with 0 is a UPC-A with a leading zero; keep one form so the same product matches.
    val code = if (digits.length == 13 && digits[0] == '0') digits.substring(1) else digits
    // UPC-A number system 2 and EAN-13 prefixes 20-29 both start with 2.
    return if (code[0] == '2') ScannedCode.VariableWeight(code) else ScannedCode.Product(code)
}

/** GS1 check digit: digits weighted 3 and 1 alternately from the right, check digit excluded. */
internal fun hasValidCheckDigit(digits: String): Boolean {
    val body = digits.dropLast(1).reversed()
    val sum = body.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
    return (10 - sum % 10) % 10 == digits.last() - '0'
}
