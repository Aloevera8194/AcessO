package com.oliveira.facialapp.model

import java.util.UUID

data class Equipamento(
    var id: String = UUID.randomUUID().toString(),
    var nome: String = "",
    var porta: String = "80",
    var usuario: String = "",
    var senha: String = ""
)

data class IduhfConfig(
    var habilitado: Boolean = false,
    var ip: String = "",
    var porta: String = "80",
    var usuario: String = "",
    var senha: String = ""
)

data class Predio(
    var id: String = UUID.randomUUID().toString(),
    var nome: String = "",
    var ip: String = "",
    var usuarioPadrao: String = "admin",
    var senhaPadrao: String = "",
    var equipamentos: MutableList<Equipamento> = mutableListOf(),
    var iduhf: IduhfConfig = IduhfConfig()
)

fun Equipamento.usuarioEfetivo(predio: Predio): String =
    usuario.ifBlank { predio.usuarioPadrao }

fun Equipamento.senhaEfetiva(predio: Predio): String =
    senha.ifBlank { predio.senhaPadrao }

data class TagUhf(
    var area: String = "",
    var id: String = ""
)

data class UsuarioCadastro(
    var employeeNo: String = "",
    var apartamento: String = "",
    var nome: String = "",
    var cartoes: MutableList<String> = MutableList(5) { "" },
    var tagsUhf: MutableList<TagUhf> = MutableList(5) { TagUhf() },
    var validadeInicio: String = "",
    var validadeFim: String = "",
    var fotoCaminho: String = ""
)

enum class ResultadoStatus { SUCESSO, FALHA }

data class ResultadoEnvio(
    val equipamento: Equipamento,
    var status: ResultadoStatus,
    var mensagem: String = ""
)

data class RegistroCadastro(
    var predioId: String = "",
    var equipamentoId: String = "",
    var equipamentoNome: String = "",
    var apartamento: String = "",
    var employeeNo: String = "",
    var nome: String = "",
    var dataHora: String = ""
)
