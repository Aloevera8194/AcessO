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
        val destruir = JSONObject()
            .put("object", "uhf_tags")
            .put("where", JSONObject().put("uhf_tags", JSONObject().put("user_id", userId)))
        post("/destroy_objects.fcgi", destruir, session)

        val tags = u.tagsUhf.map { it.id.trim() }.filter { it.isNotBlank() }
        if (tags.isEmpty()) return

        val valores = JSONArray()
        tags.forEach { tag ->
            valores.put(JSONObject().put("value", tag).put("user_id", userId))
        }
        val corpo = JSONObject().put("object", "uhf_tags").put("values", valores)
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
}