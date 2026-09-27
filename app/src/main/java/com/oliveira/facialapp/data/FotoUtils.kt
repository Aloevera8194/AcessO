package com.oliveira.facialapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object FotoUtils {

    /** Lê a imagem (câmera ou galeria), corrige a rotação real (a câmera salva o dado
     *  de rotação separado da imagem em si), redimensiona para no máx. 640px de largura
     *  e comprime em JPEG até ficar abaixo de ~200KB (limite comum nesses terminais). */
    fun prepararFoto(context: Context, uri: Uri): File {
        val bitmapOriginal = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("Não foi possível ler a imagem")

        val orientacao = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bitmapCorrigido = aplicarRotacao(bitmapOriginal, orientacao)

        val larguraMax = 640
        val bitmap = if (bitmapCorrigido.width > larguraMax) {
            val proporcao = larguraMax.toFloat() / bitmapCorrigido.width
            Bitmap.createScaledBitmap(bitmapCorrigido, larguraMax, (bitmapCorrigido.height * proporcao).toInt(), true)
        } else {
            bitmapCorrigido
        }

        val pasta = File(context.cacheDir, "fotos").apply { mkdirs() }
        val arquivo = File(pasta, "face_${System.currentTimeMillis()}.jpg")

        var qualidade = 90
        var bytesEstimados: Int
        do {
            FileOutputStream(arquivo).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, qualidade, out)
            }
            bytesEstimados = arquivo.length().toInt()
            qualidade -= 10
        } while (bytesEstimados > 200_000 && qualidade > 20)

        return arquivo
    }

    private fun aplicarRotacao(bitmap: Bitmap, orientacao: Int): Bitmap {
        val matrix = Matrix()
        when (orientacao) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun novoArquivoCamera(context: Context): File {
        val pasta = File(context.cacheDir, "fotos").apply { mkdirs() }
        return File(pasta, "captura_${System.currentTimeMillis()}.jpg")
    }
}
