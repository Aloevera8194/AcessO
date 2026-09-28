package com.oliveira.facialapp.net

import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.model.UsuarioCadastro

/**
 * Cliente para leitores Control iD (ex: iDUHF).
 * Ainda não implementado: falta a documentação da API do fabricante.
 */
class IduhfClient(private val predio: Predio, private val equipamento: Equipamento) {

    fun criarUsuario(usuario: UsuarioCadastro) {
        throw Exception("Integração com Control iD (${equipamento.nome}) ainda não implementada")
    }

    fun atualizarUsuario(usuario: UsuarioCadastro) {
        throw Exception("Integração com Control iD (${equipamento.nome}) ainda não implementada")
    }
}
