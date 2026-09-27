package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliveira.facialapp.R
import com.oliveira.facialapp.adapter.PredioAdapter
import com.oliveira.facialapp.data.ConfigStorage
import com.oliveira.facialapp.data.ModoResidenteConfig
import com.oliveira.facialapp.model.Predio

class MainActivity : AppCompatActivity() {

    private lateinit var storage: ConfigStorage
    private lateinit var adapter: PredioAdapter
    private lateinit var textVazio: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        storage = ConfigStorage(this)
        textVazio = findViewById(R.id.textVazio)
        val recycler = findViewById<RecyclerView>(R.id.recyclerPredios)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = PredioAdapter(
            emptyList(),
            onAbrir = { predio ->
                val i = Intent(this, PredioActivity::class.java)
                i.putExtra(PredioActivity.EXTRA_PREDIO_ID, predio.id)
                startActivity(i)
            },
            onConfigurar = { predio ->
                pedirSenhaEExecutar {
                    val i = Intent(this, ConfigPredioActivity::class.java)
                    i.putExtra(ConfigPredioActivity.EXTRA_PREDIO_ID, predio.id)
                    startActivity(i)
                }
            }
        )
        recycler.adapter = adapter

        findViewById<android.view.View>(R.id.fabNovoPredio).setOnClickListener {
            val input = EditText(this)
            input.hint = "Nome do prédio"
            AlertDialog.Builder(this)
                .setTitle("Novo prédio")
                .setView(input)
                .setPositiveButton("Criar") { _, _ ->
                    val nome = input.text.toString().trim()
                    if (nome.isNotBlank()) {
                        val predio = Predio(nome = nome)
                        storage.salvarPredio(predio)
                        pedirSenhaEExecutar {
                            val i = Intent(this, ConfigPredioActivity::class.java)
                            i.putExtra(ConfigPredioActivity.EXTRA_PREDIO_ID, predio.id)
                            startActivity(i)
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        carregarLista()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_modo_morador) {
            pedirSenhaEExecutar { mostrarDialogoModoMorador() }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun mostrarDialogoModoMorador() {
        val config = ModoResidenteConfig(this)
        val input = EditText(this)
        input.hint = "Número do apartamento (em branco = modo normal)"
        input.setText(config.apartamentoFixo)

        AlertDialog.Builder(this)
            .setTitle("Modo morador")
            .setMessage("Se definir um apartamento aqui, este celular fica travado nesse apartamento: o cadastro e a busca sempre usam esse número, sem poder trocar.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                config.apartamentoFixo = input.text.toString().trim()
                Toast.makeText(
                    this,
                    if (config.ativo) "Travado no apartamento ${config.apartamentoFixo}" else "Modo normal (sem travar apartamento)",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun carregarLista() {
        val predios = storage.listarPredios()
        adapter.atualizar(predios)
        textVazio.visibility = if (predios.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }
}
