package com.talos.guardian.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object QRCodeGenerator {

    /**
     * Generates a QR Code Bitmap from the given content string.
     * 
     * @param content The string to encode (e.g., Parent UID).
     * @param width The width of the QR code in pixels.
     * @param height The height of the QR code in pixels.
     * @return A Bitmap representing the QR code, or null if generation fails.
     */
    fun generateQRCode(content: String, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height
            )

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
