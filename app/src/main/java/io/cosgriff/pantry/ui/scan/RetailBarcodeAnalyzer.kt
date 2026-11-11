package io.cosgriff.pantry.ui.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

/**
 * Reads UPC-A and EAN-13 barcodes out of camera frames with zxing-cpp, entirely on the phone (#10), and hands
 * each decoded text to [onRead]. While [paused] returns true, frames are dropped unread (the sheet is open).
 */
class RetailBarcodeAnalyzer(
    private val paused: () -> Boolean,
    private val onRead: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = BarcodeReader(
        BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.UPC_A, BarcodeReader.Format.EAN_13),
            tryHarder = true,
            tryRotate = true,
        ),
    )

    override fun analyze(image: ImageProxy) {
        image.use {
            if (paused()) return
            reader.read(it).firstOrNull { result -> result.error == null && !result.text.isNullOrEmpty() }
                ?.text
                ?.let(onRead)
        }
    }
}
