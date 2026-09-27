package com.oliveira.facialapp.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.oliveira.facialapp.model.Predio
import java.io.File

class ConfigStorage(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val arquivo = File(context.filesDir, "predios.json")

    fun listarPredios(): MutableList<Predio> {
        if (!arquivo.exists()) return mutableListOf()
        val tipo = object : TypeToken<MutableList<Predio>>() {}.type
        return try {
            gson.fromJson(arquivo.readText(), tipo) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun salvarPredios(predios: List<Predio>) {
        arquivo.writeText(gson.toJson(predios))
    }

    fun salvarPredio(predio: Predio) {
        val lista = listarPredios()
        val idx = lista.indexOfFirst { it.id == predio.id }
        if (idx >= 0) lista[idx] = predio else lista.add(predio)
        salvarPredios(lista)
    }

    fun excluirPredio(predioId: String) {
        val lista = listarPredios()
        lista.removeAll { it.id == predioId }
        salvarPredios(lista)
    }
}

/** Gera o código do usuário (employeeNo): número do apartamento + dígitos sequenciais
 *  até completar 7 caracteres (ex: apto 701 -> 7011234). Guarda o próximo sequencial. */
class ContadorUsuario(context: Context) {
    private val prefs = context.getSharedPreferences("facialapp_prefs", Context.MODE_PRIVATE)

    fun proximoCodigo(apartamento: String): String {
        val aptoLimpo = apartamento.filter { it.isDigit() }
        val proximo = prefs.getInt("proximo_numero", 1)
        if (aptoLimpo.isBlank()) return ""
        if (aptoLimpo.length >= 7) return aptoLimpo.take(7)
        val digitosRestantes = 7 - aptoLimpo.length
        val sufixo = proximo.toString().padStart(digitosRestantes, '0').takeLast(digitosRestantes)
        return aptoLimpo + sufixo
    }

    fun confirmarUso() {
        val proximo = prefs.getInt("proximo_numero", 1)
        prefs.edit().putInt("proximo_numero", proximo + 1).apply()
    }
}
