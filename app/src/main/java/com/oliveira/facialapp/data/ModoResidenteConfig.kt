package com.oliveira.facialapp.data

import android.content.Context

/** Quando o app é instalado no celular de um morador específico, o instalador pode,
 *  dentro das Configurações (senha), travar o apartamento desse aparelho. A partir
 *  daí, o campo de apartamento no cadastro e na busca vem preenchido e bloqueado. */
class ModoResidenteConfig(context: Context) {
    private val prefs = context.getSharedPreferences("facialapp_prefs", Context.MODE_PRIVATE)

    var apartamentoFixo: String
        get() = prefs.getString("apartamento_fixo", "") ?: ""
        set(value) = prefs.edit().putString("apartamento_fixo", value).apply()

    val ativo: Boolean get() = apartamentoFixo.isNotBlank()
}
