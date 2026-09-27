package com.oliveira.facialapp.model

import java.util.UUID

data class Equipamento(
    var id: String = UUID.randomUUID().toString(),
    var nome: String = "",           // apelido, ex: "Catraca entrada"
    var porta: String = "80",
    var usuario: String = "",        // vazio = usa o usuário padrão do prédio
    var senha: String = ""           // vazio = usa a senha padrão do prédio
)

data class Predio(
    var id: String = UUID.randomUUID().toString(),
    var nome: String = "",
    var ip: String = "",             // IP fixo do prédio, compartilhado pelos equipamentos
    var usuarioPadrao: String = "admin",
    var senhaPadrao: String = "",
    var equipamentos: MutableList<Equipamento> = mutableListOf()
)

fun Equipamento.usuarioEfetivo(predio: Predio): String = usuario.ifBlank { predio.usuarioPadrao }
fun Equipamento.senhaEfetiva(predio: Predio): String = senha.ifBlank { predio.senhaPadrao }

data class UsuarioCadastro(
    var employeeNo: String = "",
    var apartamento: String = "",
    var nome: String = "",
    var cartao: String = "",
    var validadeInicio: String = "",  // formato ISAPI: yyyy-MM-ddTHH:mm:ss
    var validadeFim: String = "",
    var fotoCaminho: String = ""      // caminho local do arquivo jpg já preparado
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
