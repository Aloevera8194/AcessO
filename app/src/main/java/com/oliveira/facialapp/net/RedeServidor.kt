package com.oliveira.facialapp.net

import com.oliveira.facialapp.data.SessaoStorage
import java.net.HttpURLConnection
import java.net.URL

// Campo do servidor: "local;externo:porta;chave"
// Ex.: 192.168.3.51;IP-EXTERNO:3556;CHAVE
object RedeServidor {
    @Volatile private var cacheBruto = ""
    @Volatile private var cacheBase = ""
    @Volatile private var cacheEm = 0L

    private fun comHttp(h: String): String {
        var s = h.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s
    }

    // O servidor do condomínio responde em JSON, mesmo para rota inexistente
    private fun ehMeuServidor(base: String): Boolean {
        return try {
            val con = URL("$base/api/index.php?r=ping").openConnection() as HttpURLConnection
            con.connectTimeout = 1500
            con.readTimeout = 1500
            try {
                val codigo = con.responseCode
                val fluxo = if (codigo in 200..299) con.inputStream else con.errorStream
                val texto = fluxo?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: ""
                texto.trimStart().startsWith("{")
            } finally {
                con.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun base(sessao: SessaoStorage): String {
        val bruto = sessao.servidor
        val partes = bruto.split(";").map { it.trim() }
        val local = comHttp(partes[0])
        val externo = partes.getOrNull(1).orEmpty()
        if (externo.isEmpty()) return local
        val agora = System.currentTimeMillis()
        if (bruto == cacheBruto && cacheBase.isNotEmpty() && agora - cacheEm < 20000) return cacheBase
        val chave = partes.getOrNull(2).orEmpty()
        val remoto = comHttp(externo) + (if (chave.isNotEmpty()) "/k/$chave" else "") + "/cond"
        val escolhida = if (local.isNotEmpty() && ehMeuServidor(local)) local else remoto
        cacheBruto = bruto
        cacheBase = escolhida
        cacheEm = agora
        return escolhida
    }
}
