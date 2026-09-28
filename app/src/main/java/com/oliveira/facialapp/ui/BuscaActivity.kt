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
    private lateinit var pr