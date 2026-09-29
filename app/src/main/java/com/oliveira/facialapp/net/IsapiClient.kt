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