package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oliveira.facialapp.data.SegurancaConfig

fun AppCompatActivity.pedirSenhaEExecutar(onSucesso: () -> Unit) {
    val input = EditText(this)
    input.hint = "Senha"
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    AlertDialog.Builder(this)
        .setTitle("Senha de configurações")
        .setView(input)
        .setPositiveButton("Entrar") { _, _ ->
            if (input.text.toString() == SegurancaConfig.SENHA_CONFIGURACOES) {
                onSucesso()
            } else {
                Toast.makeText(this, "Senha incorreta", Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}
