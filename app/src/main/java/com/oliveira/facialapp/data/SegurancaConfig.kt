package com.oliveira.facialapp.data

/** Senha fixa pra abrir as Configurações do prédio (IP, senha do equipamento, portas).
 *  Embutida no app — não aparece em tela nenhuma, só quem souber digita.
 *  Pra trocar, é só mudar esse valor e gerar um novo APK. */
object SegurancaConfig {
    const val SENHA_CONFIGURACOES = "8194"
}
