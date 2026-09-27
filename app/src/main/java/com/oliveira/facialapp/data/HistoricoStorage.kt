package com.oliveira.facialapp.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.oliveira.facialapp.model.RegistroCadastro
import java.io.File

class HistoricoStorage(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val arquivo = File(context.filesDir, "historico.json")

    fun listarTudo(): MutableList<RegistroCadastro> {
        if (!arquivo.exists()) return mutableListOf()
        val tipo = object : TypeToken<MutableList<RegistroCadastro>>() {}.type
        return try {
            gson.fromJson(arquivo.readText(), tipo) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun registrar(registro: RegistroCadastro) {
        val lista = listarTudo()
        // substitui um registro anterior do mesmo apartamento no mesmo equipamento, se houver
        lista.removeAll { it.predioId == registro.predioId && it.equipamentoId == registro.equipamentoId && it.apartamento == registro.apartamento }
        lista.add(registro)
        arquivo.writeText(gson.toJson(lista))
    }

    fun buscarPorPredio(predioId: String): List<RegistroCadastro> =
        listarTudo().filter { it.predioId == predioId }.sortedByDescending { it.dataHora }

    fun buscarPorApartamento(predioId: String, apartamento: String): List<RegistroCadastro> =
        buscarPorPredio(predioId).filter { it.apartamento.contains(apartamento, ignoreCase = true) }
}
