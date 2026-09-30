package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.oliveira.facialapp.R
import com.oliveira.facialapp.adapter.EquipamentoManageAdapter
import com.oliveira.facialapp.data.ConfigStorage
import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio

class ConfigPredioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREDIO_ID = "extra_predio_id"
    }

    private lateinit var storage: ConfigStorage
    private lateinit var predio: Predio
    private lateinit var adapter: EquipamentoManageAdapter

    private lateinit var editNome: TextInputEditText
    private lateinit var editIp: TextInputEditText
    private lateinit var editUsuario: TextInputEditText
    private lateinit var editSenha: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_predio)
        storage = ConfigStorage(this)

        val predioId = intent.getStringExtra(EXTRA_PREDIO_ID)
        predio = storage.listarPredios().find { it.id == predioId } ?: run {
            Toast.makeText(this, "Prédio não encontrado", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = "Configurar ${predio.nome}"
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        editNome = findViewById(R.id.editNomePredio)
        editIp = findViewById(R.id.editIpPredio)
        editUsuario = findViewById(R.id.editUsuarioPredio)
        editSenha = findViewById(R.id.editSenhaPredio)

        editNome.setText(predio.nome)
        editIp.setText(predio.ip)
        editUsuario.setText(predio.usuarioPadrao)
        editSenha.setText(predio.senhaPadrao)

        findViewById<android.view.View>(R.id.btnSalvarPredio).setOnClickListener {
            predio.nome = editNome.text.toString().trim()
            predio.ip = editIp.text.toString().trim()
            predio.usuarioPadrao = editUsuario.text.toString().trim()
            predio.senhaPadrao = editSenha.text.toString()
            storage.salvarPredio(predio)
            toolbar.title = "Configurar ${predio.nome}"
            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerEquipamentos)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = EquipamentoManageAdapter(
            predio.equipamentos,
            onEditar = { eq -> mostrarDialogoEquipamento(eq) },
            onExcluir = { eq ->
                AlertDialog.Builder(this)
                    .setTitle("Excluir equipamento?")
                    .setMessage(eq.nome)
                    .setPositiveButton("Excluir") { _, _ ->
                        predio.equipamentos.removeAll { it.id == eq.id }
                        storage.salvarPredio(predio)
                        adapter.atualizar(predio.equipamentos)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
        recycler.adapter = adapter

        findViewById<android.view.View>(R.id.btnAddEquipamento).setOnClickListener {
            mostrarDialogoEquipamento(null)
        }
    }

    private fun mostrarDialogoEquipamento(existente: Equipamento?) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 0)

        val editNomeEq = EditText(this).apply { hint = "Nome/apelido (ex: Catraca entrada)" }
        val editPorta = EditText(this).apply { hint = "Porta"; setText("80") }
        val editUsuarioEq = EditText(this).apply { hint = "Usuário (deixe em branco pra usar o padrão do prédio)" }
        val editSenhaEq = EditText(this).apply {
            hint = "Senha (deixe em branco pra usar a padrão do prédio)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        existente?.let {
            editNomeEq.setText(it.nome)
            editPorta.setText(it.porta)
            editUsuarioEq.setText(it.usuario)
            editSenhaEq.setText(it.senha)
        }

        listOf(editNomeEq, editPorta, editUsuarioEq, editSenhaEq).forEach { layout.addView(it) }

        AlertDialog.Builder(this)
            .setTitle(if (existente == null) "Novo equipamento" else "Editar equipamento")
            .setView(layout)
            .setPositiveButton("Salvar") { _, _ ->
                val porta = editPorta.text.toString().trim().ifBlank { "80" }
                if (existente == null) {
                    predio.equipamentos.add(
                        Equipamento(
                            nome = editNomeEq.text.toString().trim(),
                            porta = porta,
                            usuario = editUsuarioEq.text.toString().trim(),
                            senha = editSenhaEq.text.toString()
                        )
                    )
                } else {
                    existente.nome = editNomeEq.text.toString().trim()
                    existente.porta = porta
                    existente.usuario = editUsuarioEq.text.toString().trim()
                    existente.senha = editSenhaEq.text.toString()
                }
                storage.salvarPredio(predio)
                adapter.atualizar(predio.equipamentos)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
