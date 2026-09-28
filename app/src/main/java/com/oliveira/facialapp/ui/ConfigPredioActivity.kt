package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
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
import com.oliveira.facialapp.model.EquipamentoCatalogo
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

        findViewById<View>(R.id.btnSalvarPredio).setOnClickListener {
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

        findViewById<View>(R.id.btnAddEquipamento).setOnClickListener {
            mostrarDialogoEquipamento(null)
        }
    }

    private fun mostrarDialogoEquipamento(existente: Equipamento?) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 0)

        val labelFabricante = TextView(this).apply { text = "Fabricante" }
        val spinnerFabricante = Spinner(this)
        val fabricantes = EquipamentoCatalogo.fabricantes
        spinnerFabricante.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fabricantes)

        val labelModelo = TextView(this).apply { text = "Modelo"; setPadding(0, 16, 0, 0) }
        val spinnerModelo = Spinner(this)

        fun atualizarModelos(fabricante: String, modeloSelecionado: String?) {
            val modelos = EquipamentoCatalogo.modelosPorFabricante[fabricante] ?: emptyList()
            spinnerModelo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelos)
            val idx = modelos.indexOf(modeloSelecionado).let { if (it >= 0) it else 0 }
            if (modelos.isNotEmpty()) spinnerModelo.setSelection(idx)
        }

        val editNomeEq = EditText(this).apply {
            hint = "Nome/apelido (ex: Catraca entrada)"
            setPadding(0, 16, 0, 0)
        }
        val editPorta = EditText(this).apply { hint = "Porta"; setText("80") }
        val editUsuarioEq = EditText(this).apply { hint = "Usuário (deixe em branco pra usar o padrão do prédio)" }
        val editSenhaEq = EditText(this).apply {
            hint = "Senha (deixe em branco pra usar a padrão do prédio)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val fabricanteInicial = existente?.fabricante ?: fabricantes.first()
        spinnerFabricante.setSelection(fabricantes.indexOf(fabricanteInicial).let { if (it >= 0) it else 0 })
        atualizarModelos(fabricanteInicial, existente?.modelo)

        spinnerFabricante.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                atualizarModelos(fabricantes[position], null)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        existente?.let {
            editNomeEq.setText(it.nome)
            editPorta.setText(it.porta)
            editUsuarioEq.setText(it.usuario)
            editSenhaEq.setText(it.senha)
        }

        listOf(labelFabricante, spinnerFabricante, labelModelo, spinnerModelo, editNomeEq, editPorta, editUsuarioEq, editSenhaEq)
            .forEach { layout.addView(it) }

        val scroll = android.widget.ScrollView(this)
        scroll.addView(layout)

        AlertDialog.Builder(this)
            .setTitle(if (existente == null) "Novo equipamento" else "Editar equipamento")
            .setView(scroll)
            .setPositiveButton("Salvar") { _, _ ->
                val porta = editPorta.text.toString().trim().ifBlank { "80" }
                val fabricanteEscolhido = spinnerFabricante.selectedItem as? String ?: fabricantes.first()
                val modeloEscolhido = spinnerModelo.selectedItem as? String ?: ""
                if (existente == null) {
                    predio.equipamentos.add(
                        Equipamento(
                            nome = editNomeEq.text.toString().trim(),
                            porta = porta,
                            usuario = editUsuarioEq.text.toString().trim(),
                            senha = editSenhaEq.text.toString(),
                            fabricante = fabricanteEscolhido,
                            modelo = modeloEscolhido
                        )
                    )
                } else {
                    existente.nome = editNomeEq.text.toString().trim()
                    existente.porta = porta
                    existente.usuario = editUsuarioEq.text.toString().trim()
                    existente.senha = editSenhaEq.text.toString()
                    existente.fabricante = fabricanteEscolhido
                    existente.modelo = modeloEscolhido
                }
                storage.salvarPredio(predio)
                adapter.atualizar(predio.equipamentos)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
