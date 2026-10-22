package com.oliveira.facialapp.net

import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.model.UsuarioCadastro
import com.oliveira.facialapp.model.senhaEfetiva
import com.oliveira.facialapp.model.usuarioEfetivo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fala com leitores Control iD (ex: iDUHF) via API REST própria (login.fcgi + objetos).
 *  Cuida apenas do necessário para este app: usuário (por matrícula) e TAGs UHF. */
class IduhfClient(private val predio: Predio, private val equipamento: Equipamento) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://${predio.ip}:${equipamento.porta}"
    private val usuario = equipamento.usuarioEfetivo(predio)
    private val senha = equipamento.senhaEfetiva(predio)
    private val formatoIsapi = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    class IduhfException(message: String) : IOException(message)

    private fun post(path: String, corpo: JSONObject, session: String? = null): JSONObject {
        val url = if (session != null) "$baseUrl$path?session=$session" else "$baseUrl$path"
        val body = corpo.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        val resposta = client.newCall(request).execute()
        val texto = resposta.body?.string() ?: ""
        resposta.close()
        if (!resposta.isSuccessful) throw IduhfException("HTTP ${resposta.code}: $texto")
        val json = try { JSONObject(texto) } catch (e: Exception) { JSONObject() }
        if (json.has("error")) throw IduhfException(json.optString("error"))
        return json
    }

    fun abrirPorta() {
        val session = login()
        val corpo = JSONObject().put(
            "actions",
            JSONArray().put(
                JSONObject()
                    .put("action", "door")
                    .put("parameters", "door=1")
            )
        )
        post("/execute_actions.fcgi", corpo, session)
    }

    private fun login(): String {
        val corpo = JSONObject()
            .put("login", usuario.ifBlank { "admin" })
            .put("password", senha.ifBlank { "admin" })
        val resposta = post("/login.fcgi", corpo)
        val session = resposta.optString("session")
        if (session.isBlank()) throw IduhfException("Login falhou: sessão vazia (confira usuário/senha)")
        return session
    }

    private fun buscarIdPorMatricula(session: String, matricula: String): Long? {
        val corpo = JSONObject()
            .put("object", "users")
            .put("fields", JSONArray().put("id").put("registration"))
            .put("where", JSONObject().put("users", JSONObject().put("registration", matricula)))
        val resposta = post("/load_objects.fcgi", corpo, session)
        val lista = resposta.optJSONArray("users") ?: return null
        if (lista.length() == 0) return null
        return lista.getJSONObject(0).optLong("id")
    }

    private fun criarUsuarioObjeto(session: String, u: UsuarioCadastro): Long {
        val valores = JSONObject()
            .put("registration", u.employeeNo)
            .put("name", u.nome)
            .put("begin_time", timestamp(u.validadeInicio))
            .put("end_time", timestamp(u.validadeFim))
        val corpo = JSONObject()
            .put("object", "users")
            .put("values", JSONArray().put(valores))
        val resposta = post("/create_objects.fcgi", corpo, session)
        val ids = resposta.optJSONArray("ids") ?: throw IduhfException("Criar usuário: resposta sem id")
        if (ids.length() == 0) throw IduhfException("Criar usuário: nenhum id retornado")
        return ids.getLong(0)
    }

    private fun atualizarDadosUsuario(session: String, userId: Long, u: UsuarioCadastro) {
        val valores = JSONObject()
            .put("name", u.nome)
            .put("begin_time", timestamp(u.validadeInicio))
            .put("end_time", timestamp(u.validadeFim))
        val corpo = JSONObject()
            .put("object", "users")
            .put("values", valores)
            .put("where", JSONObject().put("users", JSONObject().put("id", userId)))
        post("/modify_objects.fcgi", corpo, session)
    }

    private fun substituirTagsUhf(session: String, userId: Long, u: UsuarioCadastro) {
        // No modo Standard, TAG UHF é armazenada como cartão (cards).
        val valores = u.tagsUhf.mapNotNull { tag ->
            val areaTexto = tag.area.trim()
            val codigoTexto = tag.id.trim()

            if (areaTexto.isBlank() && codigoTexto.isBlank()) return@mapNotNull null
            if (areaTexto.isBlank() || codigoTexto.isBlank()) {
                throw IduhfException("TAG inválida: use o formato área,código. Ex.: 001,16345")
            }

            val area = areaTexto.toLongOrNull()
                ?: throw IduhfException("Área inválida na TAG: $areaTexto")
            val codigo = codigoTexto.toLongOrNull()
                ?: throw IduhfException("Código inválido na TAG: $codigoTexto")

            if (area < 0 || area > 2147483647L ||
                codigo < 0 || codigo > 4294967295L) {
                throw IduhfException("Área ou código fora do limite permitido.")
            }

            area * 4294967296L + codigo
        }.distinct()

        // Verifica conflitos antes de remover cartões já associados ao usuário.
        if (valores.isNotEmpty()) {
            val consulta = JSONObject()
                .put("object", "cards")
                .put("fields", JSONArray().put("value").put("user_id"))
            val resposta = post("/load_objects.fcgi", consulta, session)
            val cards = resposta.optJSONArray("cards") ?: JSONArray()

            for (i in 0 until cards.length()) {
                val card = cards.getJSONObject(i)
                val valor = card.optLong("value")
                if (valor in valores && card.optLong("user_id") != userId) {
                    throw IduhfException("Esta TAG já está associada a outro usuário.")
                }
            }
        }

        val destruir = JSONObject()
            .put("object", "cards")
            .put("where", JSONObject().put("cards", JSONObject().put("user_id", userId)))
        post("/destroy_objects.fcgi", destruir, session)

        if (valores.isEmpty()) return

        val lista = JSONArray()
        valores.forEach { valor ->
            lista.put(JSONObject().put("value", valor).put("user_id", userId))
        }

        val corpo = JSONObject()
            .put("object", "cards")
            .put("values", lista)
        post("/create_objects.fcgi", corpo, session)
    }

    private fun timestamp(dataIsapi: String): Long {
        return try {
            (formatoIsapi.parse(dataIsapi)?.time ?: 0L) / 1000
        } catch (e: Exception) {
            0L
        }
    }

    fun criarUsuario(u: UsuarioCadastro) {
        val session = login()
        val existenteId = buscarIdPorMatricula(session, u.employeeNo)
        val userId = existenteId ?: criarUsuarioObjeto(session, u)
        if (existenteId != null) atualizarDadosUsuario(session, userId, u)
        substituirTagsUhf(session, userId, u)
    }

    fun atualizarUsuario(u: UsuarioCadastro) {
        val session = login()
        val userId = buscarIdPorMatricula(session, u.employeeNo) ?: criarUsuarioObjeto(session, u)
        atualizarDadosUsuario(session, userId, u)
        substituirTagsUhf(session, userId, u)
    }

    /** Busca usuários cujo registration comece com o apartamento OU cujo nome contenha o texto. */
    fun buscarPorApartamento(query: String): List<Pair<String, String>> {
        val session = login()
        val queryLimpo = query.trim()
        val aptoLimpo = queryLimpo.filter { it.isDigit() }

        val corpo = JSONObject()
            .put("object", "users")
            .put("fields", JSONArray().put("id").put("registration").put("name"))
        val resposta = post("/load_objects.fcgi", corpo, session)
        val lista = resposta.optJSONArray("users") ?: return emptyList()

        val encontrados = mutableListOf<Pair<String, String>>()
        for (i in 0 until lista.length()) {
            val item = lista.getJSONObject(i)
            val registration = item.optString("registration", "")
            val nome = item.optString("name", "")
            val bateApto = aptoLimpo.isNotBlank() && registration.startsWith(aptoLimpo)
            val bateNome = queryLimpo.isNotBlank() && nome.contains(queryLimpo, ignoreCase = true)
            if (queryLimpo.isBlank() || bateApto || bateNome) {
                encontrados.add(registration to nome)
            }
        }
        return encontrados
    }

    fun detalhesUsuario(employeeNo: String): String {
        val session = login()

        val consultaUsuarios = JSONObject()
            .put("object", "users")
            .put("fields", JSONArray().put("id").put("registration").put("name"))
            .put("where", JSONObject().put(
                "users", JSONObject().put("registration", employeeNo)
            ))

        val usuarios = post("/load_objects.fcgi", consultaUsuarios, session)
            .optJSONArray("users") ?: JSONArray()

        if (usuarios.length() == 0) return "Usuário não encontrado no equipamento."

        val usuarioEncontrado = usuarios.getJSONObject(0)
        val userId = usuarioEncontrado.optLong("id")
        val nome = usuarioEncontrado.optString("name", "")
        val registro = usuarioEncontrado.optString("registration", "")

        val consultaCartoes = JSONObject()
            .put("object", "cards")
            .put("fields", JSONArray().put("id").put("value").put("user_id"))
            .put("where", JSONObject().put(
                "cards", JSONObject().put("user_id", userId)
            ))

        val cartoes = post("/load_objects.fcgi", consultaCartoes, session)
            .optJSONArray("cards") ?: JSONArray()

        val resultado = StringBuilder()
        resultado.append("Nome: ").append(nome)
        resultado.append("\\nApartamento/matrícula: ").append(registro)
        resultado.append("\\nID interno: ").append(userId)
        resultado.append("\\n\\nTAGs/cartões associados: ")

        if (cartoes.length() == 0) {
            resultado.append("nenhum")
        } else {
            resultado.append("\\n")
            for (i in 0 until cartoes.length()) {
                val cartao = cartoes.getJSONObject(i)
                val valor = cartao.optLong("value")
                val area = valor / 4294967296L
                val codigo = valor % 4294967296L
                resultado.append("Área: ").append(area)
                    .append("  Código: ").append(codigo)
                    .append("\\nValor API: ").append(valor)
                    .append("\\nID do cartão: ").append(cartao.optLong("id"))
                if (i < cartoes.length() - 1) resultado.append("\\n\\n")
            }
        }
        return resultado.toString()
    }

    fun excluirUsuario(employeeNo: String) {
        val session = login()
        val corpo = JSONObject()
            .put("object", "users")
            .put("where", JSONObject().put("users", JSONObject().put("registration", employeeNo)))
        post("/destroy_objects.fcgi", corpo, session)
    }
}