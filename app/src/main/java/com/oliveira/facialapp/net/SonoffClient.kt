package com.oliveira.facialapp.net

import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.model.ipEfetivo
import com.oliveira.facialapp.model.senhaEfetiva
import com.oliveira.facialapp.model.usuarioEfetivo
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Aciona um Sonoff Mini com firmware Tasmota via HTTP (/cm?cmnd=...). */
class SonoffClient(private val predio: Predio, private val equipamento: Equipamento) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    class SonoffException(message: String) : IOException(message)

    /** Pulso: alterna o relé e desliga depois. O Delay do Tasmota é em décimos de segundo. */
    fun acionar(segundos: Double = 1.0) {
        val decimos = (segundos * 10).toInt().coerceAtLeast(1)
        enviar("Backlog Power Toggle;Delay $decimos;Power1 off")
    }

    private fun enviar(comando: String): String {
        val url = try {
            "http://${equipamento.ipEfetivo(predio)}:${equipamento.porta}/cm".toHttpUrl().newBuilder()
                .addQueryParameter("user", equipamento.usuarioEfetivo(predio))
                .addQueryParameter("password", equipamento.senhaEfetiva(predio))
                .addQueryParameter("cmnd", comando)
                .build()
        } catch (e: IllegalArgumentException) {
            throw SonoffException("IP ou porta inválidos")
        }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resposta ->
            val texto = resposta.body?.string() ?: ""
            if (resposta.code == 401) throw SonoffException("Usuário ou senha do Sonoff incorretos")
            if (!resposta.isSuccessful) throw SonoffException("HTTP ${resposta.code}: $texto")
            return texto
        }
    }
}
