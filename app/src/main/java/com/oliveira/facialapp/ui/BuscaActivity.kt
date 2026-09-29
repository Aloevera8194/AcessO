package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.oliveira.facialapp.R
import com.oliveira.facialapp.data.ConfigStorage
import com.oliveira.facialapp.data.HistoricoStorage
import com.oliveira.facialapp.data.ModoResidenteConfig
import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.net.IsapiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuscaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREDIO_ID = "extra_predio_id"
    }

    private data class ResultadoBusca(val lista: List<Pair<String, String>>?, val erro: String?)

    private lateinit var storage: ConfigStorage
    private lateinit var historico: HistoricoStorage
    private lateinit var predio: Predio
    private lateinit var editApartamento: TextInputEditText
    private lateinit var containerChecklist: LinearLayout

    private val equipamentosBuscaveis: List<Equipamento>
        get() = predio.equipamentos.filter { it.fabricante != "Control iD" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_busca)
        storage = ConfigStorage(this)
        historico = HistoricoStorage(this)

        val predioId = intent.getStringExtra(EXTRA_PREDIO_ID)
        predio = storage.listarPredios().find { it.id == predioId } ?: run {
            Toast.makeText(this, "Prédio não encontrado", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        editApartamento = findViewById(R.id.editApartamentoBusca)
        containerChecklist = findViewById(R.id.containerChecklistBusca)

        montarChecklistEquipamentos()

        findViewById<android.view.View>(R.id.btnMarcarTodos).setOnClickListener {
            marcarTodos(true)
        }
        findViewById<android.view.View>(R.id.btnDesmarcarTodos).setOnClickListener {
            marcarTodos(false)
        }

        val modoResidente = ModoResidenteConfig(this)
        if (modoResidente.ativo) {
            editApartamento.setText(modoResidente.apartamentoFixo)
            editApartamento.isEnabled = false
            buscar()
        }

        findViewById<android.view.View>(R.id.btnBuscar).setOnClickListener { buscar() }
    }

    override fun onResume() {
        super.onResume()
        // ao voltar de uma edição/exclusão, refaz a busca pra refletir o estado atual
        if (editApartamento.text.toString().isNotBlank()) buscar()
    }

    private fun montarChecklistEquipamentos() {
        containerChecklist.removeAllViews()
        val lista = equipamentosBuscaveis
        if (lista.isEmpty()) {
            containerChecklist.addView(linhaTexto("Nenhum equipamento com busca disponível neste prédio"))
            return
        }
        lista.groupBy { it.fabricante }.forEach { (fabricante, itens) ->
            val titulo = TextView(this)
            titulo.text = fabricante
            titulo.setTypeface(null, android.graphics.Typeface.BOLD)
            titulo.setPadding(0, 12, 0, 4)
            containerChecklist.addView(titulo)
            itens.forEach { eq ->
                val cb = CheckBox(this)
                cb.text = "${eq.nome}  (porta ${eq.porta})"
                cb.isChecked = true
                cb.tag = eq.id
                containerChecklist.addView(cb)
            }
        }
    }

    private fun marcarTodos(marcar: Boolean) {
        for (i in 0 until containerChecklist.childCount) {
            val view = containerChecklist.getChildAt(i)
            if (view is CheckBox) view.isChecked = marcar
        }
    }

    private fun equipamentosMarcados(): List<Equipamento> {
        val idsMarcados = mutableSetOf<String>()
        for (i in 0 until containerChecklist.childCount) {
            val view = containerChecklist.getChildAt(i)
            if (view is CheckBox && view.isChecked) idsMarcados.add(view.tag as String)
        }
        return predio.equipamentos.filter { it.id in idsMarcados }
    }

    private fun buscar() {
        val apto = editApartamento.text.toString().trim()
        if (apto.isBlank()) {
            Toast.makeText(this, "Informe o apartamento", Toast.LENGTH_SHORT).show()
            return
        }

        val containerHistorico = findViewById<LinearLayout>(R.id.containerHistorico)
        val containerEquip = findViewById<LinearLayout>(R.id.containerEquipamentos)
        containerHistorico.removeAllViews()
        containerEquip.removeAllViews()

        val registros = historico.buscarPorApartamento(predio.id, apto)
        if (registros.isEmpty()) {
            containerHistorico.addView(linhaTexto("Nada encontrado no histórico deste celular"))
        } else {
            registros.forEach { registro ->
                val equipamento = predio.equipamentos.find { it.id == registro.equipamentoId }
                containerHistorico.addView(
                    linhaClicavel(
                        "${registro.nome}  ·  código ${registro.employeeNo}  ·  ${registro.equipamentoNome}  ·  ${registro.dataHora}"
                    ) {
                        if (equipamento == null) {
                            Toast.makeText(this, "Esse equipamento não existe mais neste prédio", Toast.LENGTH_SHORT).show()
                        } else {
                            mostrarAcoes(equipamento, registro.employeeNo, registro.nome, registro.apartamento)
                        }
                    }
                )
            }
        }

        if (predio.ip.isBlank() || predio.equipamentos.isEmpty()) {
            containerEquip.addView(linhaTexto("Prédio sem IP/equipamentos configurados"))
            return
        }

        val selecionados = equipamentosMarcados()
        if (selecionados.isEmpty()) {
            containerEquip.addView(linhaTexto("Nenhum equipamento selecionado para busca"))
            return
        }

        selecionados.forEach { eq ->
            val linhaCarregando = linhaTexto("${eq.nome}: buscando...")
            containerEquip.addView(linhaCarregando)
            lifecycleScope.launch {
                val resultado = withContext(Dispatchers.IO) {
                    try {
                        ResultadoBusca(IsapiClient(predio, eq).buscarPorApartamento(apto), null)
                    } catch (e: Exception) {
                        ResultadoBusca(null, e.message)
                    }
                }
                containerEquip.removeView(linhaCarregando)
                when {
                    resultado.erro != null -> containerEquip.addView(linhaTexto("${eq.nome}: erro ao consultar (${resultado.erro})"))
                    resultado.lista.isNullOrEmpty() -> containerEquip.addView(linhaTexto("${eq.nome}: nenhum cadastro encontrado"))
                    else -> {
                        containerEquip.addView(linhaTexto(eq.nome, negrito = true))
                        resultado.lista.forEach { (codigo, nome) ->
                            containerEquip.addView(
                                linhaClicavel("  • $nome — código $codigo") {
                                    mostrarAcoes(eq, codigo, nome, apto)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun mostrarAcoes(equipamento: Equipamento, employeeNo: String, nome: String, apartamento: String) {
        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(arrayOf("Editar", "Excluir", "Cancelar")) { _, which ->
                when (which) {
                    0 -> abrirEdicao(equipamento, employeeNo, nome, apartamento)
                    1 -> confirmarExclusao(equipamento, employeeNo, nome)
                }
            }
            .show()
    }

    private fun abrirEdicao(equipamento: Equipamento, employeeNo: String, nome: String, apartamento: String) {
        val i = Intent(this, CadastroActivity::class.java)
        i.putExtra(CadastroActivity.EXTRA_PREDIO_ID, predio.id)
        i.putExtra(CadastroActivity.EXTRA_MODO_EDICAO, true)
        i.putExtra(CadastroActivity.EXTRA_EQUIPAMENTO_ID_ALVO, equipamento.id)
        i.putExtra(CadastroActivity.EXTRA_EMPLOYEE_NO, employeeNo)
        i.putExtra(CadastroActivity.EXTRA_APARTAMENTO, apartamento)
        i.putExtra(CadastroActivity.EXTRA_NOME, nome)
        startActivity(i)
    }

    private fun confirmarExclusao(equipamento: Equipamento, employeeNo: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir cadastro?")
            .setMessage("$nome (código $employeeNo) será removido de ${equipamento.nome}. Isso não pode ser desfeito.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    val erro = withContext(Dispatchers.IO) {
                        try {
                            IsapiClient(predio, equipamento).excluirUsuario(employeeNo)
                            null
                        } catch (e: Exception) {
                            e.message ?: "Falha desconhecida"
                        }
                    }
                    if (erro == null) {
                        Toast.makeText(this@BuscaActivity, "Excluído com sucesso", Toast.LENGTH_SHORT).show()
                        buscar()
                    } else {
                        AlertDialog.Builder(this@BuscaActivity)
                            .setTitle("Falha ao excluir")
                            .setMessage(erro)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun linhaTexto(texto: String, negrito: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = texto
        tv.setPadding(0, 8, 0, 8)
        if (negrito) tv.setTypeface(null, android.graphics.Typeface.BOLD)
        return tv
    }

    private fun linhaClicavel(texto: String, aoClicar: () -> Unit): TextView {
        val tv = linhaTexto(texto)
        tv.setTextColor(getColor(R.color.primary))
        tv.setOnClickListener { aoClicar() }
        return tv
    }
}