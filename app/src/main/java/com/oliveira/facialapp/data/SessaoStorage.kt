package com.oliveira.facialapp.data

import android.content.Context
import org.json.JSONObject

class SessaoStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sessao", Context.MODE_PRIVATE)

    var servidor: String
        get() = prefs.getString("servidor", PADRAO) ?: PADRAO
        set(v) {
            var s = v.trim().trimEnd('/')
            if (s.isNotEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) {
                s = "http://$s"
            }
            prefs.edit().putString("servidor", s).apply()
        }

    val token: String? get() = prefs.getString("token", null)

    fun logado(): Boolean = !token.isNullOrEmpty()

    fun salvarLogin(token: String, usuario: JSONObject) {
        prefs.edit().putString("token", token).putString("usuario", usuario.toString()).apply()
    }

    fun usuario(): JSONObject? =
        prefs.getString("usuario", null)?.let {
            try { JSONObject(it) } catch (e: Exception) { null }
        }

    val perfil: String get() = usuario()?.optString("perfil") ?: ""
    val nomeUsuario: String get() = usuario()?.optString("nome") ?: ""

    fun limpar() {
        prefs.edit().remove("token").remove("usuario").apply()
    }

    companion object {
        const val PADRAO = "http://192.168.3.51"
    }
}
