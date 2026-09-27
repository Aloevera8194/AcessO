package com.oliveira.facialapp.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrUtils {
    /** Gera o QR code localmente (sem internet) a partir do texto (aqui, o número do cartão). */
    fun gerarQr(texto: String, tamanho: Int = 600): Bitmap {
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamanho, tamanho)
        val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.RGB_565)
        for (x in 0 until tamanho) {
            for (y in 0 until tamanho) {
                bitmap.setPixel(x, y, if (matriz.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
