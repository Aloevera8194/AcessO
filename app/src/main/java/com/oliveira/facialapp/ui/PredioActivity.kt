package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliveira.facialapp.R
import com.oliveira.facialapp.adapter.EquipamentoViewAdapter
import com.oliveira.facialapp.data.ConfigStorage
import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.net.IsapiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PredioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREDIO_ID = "extra_predio_id"
    }

    private lateinit var storage: ConfigStorage
    private lateinit var predio: Predio
    private lateinit var adapter: EquipamentoViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_predio)
        storage = ConfigStorage(this)

        val predioId = intent.getStringExtra(EXTRA_PREDIO_ID)
        predio = storage.listarPredios().find { it.id == predioId } ?: run {
            Toast.makeText(this, "Prédio não encontrado", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = predio.nome
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recyclerEquipamentos)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = EquipamentoViewAdapter(predio.equipamentos) { eq -> confirmarAbrirPorta(eq) }
        recycler.adapter = adapter

        findViewById<android.view.View>(R.id.btnNovoCadastro).setOnClickListener {
            if (predio.ip.isBlank()) {
                Toast.makeText(this, "Configure o IP do prédio primeiro (⚙️)", Toast.LENGTH_LONG).show()
            } else if (predio.equipamentos.isEmpty()) {
                Toast.makeText(this, "Configure ao menos um equipamento primeiro (⚙️)", Toast.LENGTH_LONG).show()
            } else {
                val i = Intent(this, CadastroActivity::class.java)
                i.putExtra(CadastroActivity.EXTRA_PREDIO_ID, predio.id)
                startActivity(i)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // relê caso algo tenha sido alterado na tela de configurações
        predio = storage.listarPredios().find { it.id == predio.id } ?: predio
        supportActionBar?.title = predio.nome
        adapter.atualizar(predio.equipamentos)
        findViewById<android.widget.TextView>(R.id.textSemEquipamentos).visibility =
            if (predio.equipamentos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmarAbrirPorta(eq: Equipamento) {
        AlertDialog.Builder(this)
            .setTitle("Abrir porta?")
            .setMessage("Vai acionar a abertura em \"${eq.nome}\" agora.")
            .setPositiveButton("Abrir") { _, _ ->
                lifecycleScope.launch {
                    val erro = withContext(Dispatchers.IO) {
                        try {
                            IsapiClient(predio, eq).abrirPorta()
                            null
                        } catch (e: Exception) {
                            e.message ?: "Falha desconhecida"
                        }
                    }
                    if (erro == null) {
                        Toast.makeText(this@PredioActivity, "Porta aberta", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this@PredioActivity)
                            .setTitle("Falha ao abrir")
                            .setMessage(erro)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_predio, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_buscar) {
            val i = Intent(this, BuscaActivity::class.java)
            i.putExtra(BuscaActivity.EXTRA_PREDIO_ID, predio.id)
            startActivity(i)
            return true
        }
        if (item.itemId == R.id.action_configurar) {
            pedirSenhaEExecutar {
                val i = Intent(this, ConfigPredioActivity::class.java)
                i.putExtra(ConfigPredioActivity.EXTRA_PREDIO_ID, predio.id)
                startActivity(i)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
