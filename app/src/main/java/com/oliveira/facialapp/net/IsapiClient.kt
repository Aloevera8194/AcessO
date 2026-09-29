package com.oliveira.facialapp.net

import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.model.UsuarioCadastro
import com.oliveira.facialapp.model.senhaEfetiva
import com.oliveira.facialapp.model.usuarioEfetivo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.json.JSONException
import org.json.JSONObject

/** Fala com um terminal Hikvision via ISAPI (HTTP + Digest auth).
 *  Um desafio Digest NOVO é calculado a cada chamada (não reaproveita nonce/cliente),
 *  porque alguns firmwares derrubam a conexão do upload de foto se reaproveitar. */
class IsapiClient(private val predio: Predio, private val equipamento: Equipamento) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://${predio.ip}:${equipamento.porta}"
    private val usuario = equipamento.usuarioEfetivo(predio)
    private val senha = equipamento.senhaEfetiva(predio)

    class IsapiException(message: String) : IOException(message)

    private fun md5(texto: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(texto.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun executarComDigest(path: String, method: String, body: okhttp3.RequestBody?): String {
        val url = "$baseUrl$path"

        val reqSemAuth = Request.Builder().url(url).method(method, body).build()
        val respSemAuth = client.newCall(reqSemAuth).execute()

        if (respSemAuth.code != 401) {
            val texto = respSemAuth.body?.string() ?: ""
            respSemAuth.close()
            if (!respSemAuth.isSuccessful) throw IsapiException("HTTP ${respSemAuth.code}: $texto")
            return texto
        }

        val desafio = respSemAuth.header("WWW-Authenticate")
        respSemAuth.close()
        if (desafio == null || !desafio.startsWith("Digest", ignoreCase = true)) {
            throw IsapiException("Equipamento não pediu autenticação Digest (verifique usuário/senha)")
        }

        val campos = Regex("(\\w+)=\"?([^\",]+)\"?").findAll(desafio)
            .associate { it.groupValues[1] to it.groupValues[2].trim('"') }
        val realm = campos["realm"] ?: ""
        val nonce = campos["nonce"] ?: ""
        val qop = campos["qop"]
        val opaque = campos["opaque"]

        val ha1 = md5("$usuario:$realm:$senha")
        val ha2 = md5("$method:$path")
        val cnonce = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
        val nc = "00000001"

        val response = if (qop != null) {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val authHeader = buildString {
            append("Digest username=\"$usuario\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$path\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            append(", response=\"$response\"")
            if (opaque != null) append(", opaque=\"$opaque\"")
        }

        val reqComAuth = Request.Builder().url(url).method(method, body).header("Authorization", authHeader).build()
        val respComAuth = client.newCall(reqComAuth).execute()
        val texto = respComAuth.body?.string() ?: ""
        respComAuth.close()
        if (!respComAuth.isSuccessful) throw IsapiException("HTTP ${respComAuth.code}: $texto")
        return texto
    }

    /** O ISAPI costuma responder HTTP 200 mesmo quando a operação falhou de verdade —
     *  o resultado real vem no campo "statusCode" do corpo (1 = sucesso). */
    private fun validarResposta(texto: String, operacao: String) {
        if (texto.isBlank()) return
        try {
            val json = JSONObject(texto)
            if (json.has("statusCode")) {
                val statusCode = json.optInt("statusCode", -1)
                if (statusCode != 1) {
                    val statusString = json.optString("statusString", "")
                    val subStatus = json.optString("subStatusCode", "")
                    val errorMsg = json.optString("errorMsg", "")
                    val detalhes = listOf(statusString, subStatus, errorMsg).filter { it.isNotBlank() }.joinToString(" · ")
                    throw IsapiException("$operacao: $detalhes".trim())
                }
            }
        } catch (e: JSONException) {
            // corpo não veio em JSON reconhecível; como o HTTP já foi bem-sucedido, seguimos
        }
    }

    /** Aciona a abertura da porta remotamente (equivalente ao "acionamento" do Condfy).
     *  doorNo geralmente é 1 nesses terminais de porta única. */
    fun abrirPorta(doorNo: Int = 1) {
        val json = """{"RemoteControlDoor":{"cmd":"open"}}"""
        val body = json.toRequestBody("application/json".toMediaType())
        val resposta = executarComDigest("/ISAPI/AccessControl/RemoteControl/door/$doorNo?format=json", "PUT", body)
        validarResposta(resposta, "Abrir porta")
    }

    /** Consulta os usuários já cadastrados nesse equipamento e filtra pelos que começam
     *  com o número do apartamento, ou cujo nome contenha o texto buscado. */
    fun buscarPorApartamento(query: String): List<Pair<String, String>> {
        val queryLimpo = query.trim()
        val aptoLimpo = queryLimpo.filter { it.isDigit() }
        val encontrados = mutableListOf<Pair<String, String>>()
        var posicao = 0
        val tamanhoPagina = 30
        var totalMatches = Int.MAX_VALUE
        while (posicao < totalMatches && posicao < 500) { // limite de segurança
            val corpo = """
                {"UserInfoSearchCond":{"searchID":"1","searchResultPosition":$posicao,"maxResults":$tamanhoPagina}}
            """.trimIndent().toRequestBody("application/json".toMediaType())

            val resposta = executarComDigest("/ISAPI/AccessControl/UserInfo/Search?format=json", "POST", corpo)
            val json = try { JSONObject(resposta) } catch (e: JSONException) { break }
            val busca = json.optJSONObject("UserInfoSearch") ?: break
            totalMatches = busca.optInt("totalMatches", 0)
            val lista = busca.optJSONArray("UserInfo")
            if (lista == null || lista.length() == 0) break

            for (i in 0 until lista.length()) {
                val item = lista.getJSONObject(i)
                val employeeNo = item.optString("employeeNo", "")
                val nome = item.optString("name", "")
                val bateApto = aptoLimpo.isNotBlank() && employeeNo.startsWith(aptoLimpo)
                val bateNome = queryLimpo.isNotBlank() && nome.contains(queryLimpo, ignoreCase = true)
                if (queryLimpo.isBlank() || bateApto || bateNome) {
                    encontrados.add(employeeNo to nome)
                }
            }
            posicao += tamanhoPagina
        }
        return encontrados
    }

    fun atualizarUsuario(u: UsuarioCadastro) {
        val json = """
            {"UserInfo":{
              "employeeNo":"${u.employeeNo}",
              "name":"${u.nome.replace("\"", "")}",
              "userType":"normal",
              "Valid":{"enable":true,"beginTime":"${u.validadeInicio}","endTime":"${u.validadeFim}"},
              "doorRight":"1",
              "RightPlan":[{"doorNo":1,"planTemplateNo":"1"}]
            }}
        """.trimIndent()
        val body = json.toRequestBody("application/json".toMediaType())
        val resposta = executarComDigest("/ISAPI/AccessControl/UserInfo/Modify?format=json", "PUT", body)
        validarResposta(resposta, "Atualizar usuário")
    }

    fun excluirUsuario(employeeNo: String) {
        val json = """
            {"UserInfoDetail":{"mode":"byEmployeeNo","EmployeeNoList":[{"employeeNo":"$employeeNo"}]}}
        """.trimIndent()
        val body = json.toRequestBody("application/json".toMediaType())
        val resposta = executarComDigest("/ISAPI/AccessControl/UserInfo/Delete?format=json", "PUT", body)
        validarResposta(resposta, "Excluir usuário")
    }

    fun criarUsuario(u: UsuarioCadastro) {
        val json = """
            {"UserInfo":{
              "employeeNo":"${u.employeeNo}",
              "name":"${u.nome.replace("\"", "")}",
              "userType":"normal",
              "Valid":{"enable":true,"beginTime":"${u.validadeInicio}","endTime":"${u.validadeFim}"},
              "doorRight":"1",
              "RightPlan":[{"doorNo":1,"planTemplateNo":"1"}]
            }}
        """.trimIndent()
        val body = json.toRequestBody("application/json".toMediaType())
        val resposta = executarComDigest("/ISAPI/AccessControl/UserInfo/Record?format=json", "POST", body)
        validarResposta(resposta, "Criar usuário")
    }

    fun cadastrarCartao(u: UsuarioCadastro) {
        for (cartao in u.cartoes.filter { it.isNotBlank() }) {
            val json = """
                {"CardInfo":{"employeeNo":"${u.employeeNo}","cardNo":"$cartao","cardType":"normalCard"}}
            """.trimIndent()
            val body = json.toRequestBody("application/json".toMediaType())
            val resposta = executarComDigest("/ISAPI/AccessControl/CardInfo/Record?format=json", "POST", body)
            validarResposta(resposta, "Cadastrar cartão")
        }
    }

    fun enviarFoto(u: UsuarioCadastro) {
        if (u.fotoCaminho.isBlank()) return
        val arquivoFoto = File(u.fotoCaminho)
        val metadados = """{"faceLibType":"blackFD","FDID":"1","FPID":"${u.employeeNo}"}"""

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("FaceDataRecord", null, metadados.toRequestBody("application/json".toMediaType()))
            .addFormDataPart("img", "face.jpg", arquivoFoto.asRequestBody("image/jpeg".toMediaType()))
            .build()

        executarComDigest("/ISAPI/Intelligent/FDLib/FaceDataRecord?format=json", "POST", multipart)
            .let { validarResposta(it, "Enviar foto") }
    }
}