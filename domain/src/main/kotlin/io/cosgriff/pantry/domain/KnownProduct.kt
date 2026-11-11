package io.cosgriff.pantry.domain

/**
 * A barcode the user has named (#12), remembered on the phone so the next scan of it fills in the name, where it
 * goes and its unit without any lookup. [location] is `null` once the location it pointed at is deleted.
 */
data class KnownProduct(
    val barcode: String,
    val name: String,
    val location: Location?,
    val unit: QuantityUnit,
) {
    init {
        require(barcode.isNotBlank()) { "Barcode must not be blank" }
        require(name.isNotBlank()) { "Name must not be blank" }
    }
}
