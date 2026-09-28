package com.oliveira.facialapp.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.oliveira.facialapp.R
import com.oliveira.facialapp.data.ConfigStorage
import com.oliveira.facialapp.data.ContadorUsuario
import com.oliveira.facialapp.data.FotoUtils
import com.oliveira.facialapp.data.HistoricoStorage
import com.oliveira.facialapp.data.ModoResidenteConfig
import com.oliveira.facialapp.model.Equipamento
import com.oliveira.facialapp.model.Predio
import com.oliveira.facialapp.model.ResultadoEnvio
import com.oliveira.facialapp.model.ResultadoStatus
import com.oliveira.facialapp.model.UsuarioCadastro
import com.oliveira.facialapp.net.IsapiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CadastroActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREDIO_ID = "extra_predio_id"
        const val EXTRA_MODO_EDICAO = "extra_modo_edicao"
        const val EXTRA_EQUIPAMENTO_ID_ALVO = "extra_equipamento_id_alvo"
        const val EXTRA_EMPLOYEE_NO = "extra_employee_no"
        const val EXTRA_APARTAMENTO = "extra_apartamento"
        const val EXTRA_NOME = "extra_nome"
    }

    private lateinit var storage: ConfigStorage
    private lateinit var predio: Predio
    private lateinit var contador: ContadorUsuario
    private lateinit var historico: HistoricoStorage

    private lateinit var imgFoto: ImageView
    private lateinit var editNome: EditText
    private lateinit var editApartamento: EditText
    private lateinit var editCodigo: EditText
    private lateinit var editCartao: EditText
    private lateinit var btnValidadeInicio: com.google.android.material.button.MaterialButton
    private lateinit var btnValidadeFim: com.google.android.material.button.MaterialButton
    private lateinit var containerEquipamentos: LinearLayout
    private lateinit var containerResultados: LinearLayout

    private var caminhoFotoPreparada: String = ""
    private var dataInicio = ""
    private var dataFim = ""
    private var uriCameraTemp: Uri? = null
    private var nfcAdapter: NfcAdapter? = null
    private var modoEdicao = false
    private var equipamentoIdAlvo: String? = null

    private val formatoIsapi = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val formatoExibicao = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    private val tirarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        if (sucesso && uriCameraTemp != null) processarFoto(uriCameraTemp!!)
    }
    private val escolherDaGaleria = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processarFoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)
        storage = ConfigStorage(this)
        contador = ContadorUsuario(this)
        historico = HistoricoStorage(this)

        val predioId = intent.getStringExtra(EXTRA_PREDIO_ID)
        predio = storage.listarPredios().find { it.id == predioId } ?: run {
            Toast.makeText(this, "Prédio não encontrado", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        modoEdicao = intent.getBooleanExtra(EXTRA_MODO_EDICAO, false)
        equipamentoIdAlvo = intent.getStringExtra(EXTRA_EQUIPAMENTO_ID_ALVO)
        if (modoEdicao) toolbar.title = "Editar cadastro"

        imgFoto = findViewById(R.id.imgFoto)
        editNome = findViewById(R.id.editNome)
        editApartamento = findViewById(R.id.editApartamento)
        editCodigo = findViewById(R.id.editCodigo)
        editCartao = findViewById(R.id.editCartao)
        btnValidadeInicio = findViewById(R.id.btnValidadeInicio)
        btnValidadeFim = findViewById(R.id.btnValidadeFim)
        containerEquipamentos = findViewById(R.id.containerEquipamentos)
        containerResultados = findViewById(R.id.containerResultados)

        if (modoEdicao) {
            editApartamento.setText(intent.getStringExtra(EXTRA_APARTAMENTO) ?: "")
            editCodigo.setText(intent.getStringExtra(EXTRA_EMPLOYEE_NO) ?: "")
            editNome.setText(intent.getStringExtra(EXTRA_NOME) ?: "")
            editApartamento.isEnabled = false
            editCodigo.isEnabled = false
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEnviar).text = "Atualizar cadastro"
        } else {
            editApartamento.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    editCodigo.setText(contador.proximoCodigo(s?.toString() ?: ""))
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            val modoResidente = ModoResidenteConfig(this)
            if (modoResidente.ativo) {
                editApartamento.setText(modoResidente.apartamentoFixo)
                editApartamento.isEnabled = false
            }
        }

        // Validade padrão: hoje até +5 anos, editável
        val cal = Calendar.getInstance()
        dataInicio = formatoIsapi.format(cal.time)
        btnValidadeInicio.text = "Início: ${formatoExibicao.format(cal.time)}"
        cal.add(Calendar.YEAR, 5)
        dataFim = formatoIsapi.format(cal.time)
        btnValidadeFim.text = "Fim: ${formatoExibicao.format(cal.time)}"

        btnValidadeInicio.setOnClickListener { abrirSeletorData(true) }
        btnValidadeFim.setOnClickListener { abrirSeletorData(false) }

        findViewById<android.view.View>(R.id.btnTirarFoto).setOnClickListener {
            val arquivo = FotoUtils.novoArquivoCamera(this)
            val uri = FileProvider.getUriForFile(this, "com.oliveira.facialapp.fileprovider", arquivo)
            uriCameraTemp = uri
            tirarFoto.launch(uri)
        }
        findViewById<android.view.View>(R.id.btnGaleria).setOnClickListener {
            escolherDaGaleria.launch("image/*")
        }

        montarChecklistEquipamentos()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        findViewById<android.view.View>(R.id.btnLerNfc).setOnClickListener {
            if (nfcAdapter == null) {
                Toast.makeText(this, "Este celular não tem NFC", Toast.LENGTH_SHORT).show()
            } else if (nfcAdapter?.isEnabled == false) {
                Toast.makeText(this, "Ative o NFC nas configurações do celular", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Aproxime a TAG do celular", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.view.View>(R.id.btnGerarQr).setOnClickListener {
            val cartao = editCartao.text.toString().trim()
            if (cartao.isBlank()) {
                Toast.makeText(this, "Preencha o número do cartão primeiro (o QR usa esse número)", Toast.LENGTH_LONG).show()
            } else {
                mostrarQr(cartao)
            }
        }

        findViewById<android.view.View>(R.id.btnEnviar).setOnClickListener {
            enviarCadastro()
        }
    }

    private fun mostrarQr(codigo: String) {
        val bitmap = com.oliveira.facialapp.data.QrUtils.gerarQr(codigo)
        val imageView = android.widget.ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(8, 8, 8, 8)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        AlertDialog.Builder(this)
            .setTitle("QR do cartão $codigo")
            .setMessage("Aproxime a tela do celular da câmera do Controle de acesso.")
            .setView(imageView)
            .setPositiveButton("Compartilhar") { _, _ -> compartilharQr(bitmap, codigo) }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun compartilharQr(bitmap: android.graphics.Bitmap, codigo: String) {
        val pasta = java.io.File(cacheDir, "qr").apply { mkdirs() }
        val arquivo = java.io.File(pasta, "qr_$codigo.png")
        java.io.FileOutputStream(arquivo).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "com.oliveira.facialapp.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar QR"))
    }

    override fun onResume() {
        super.onResume()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) processarTagNfc(tag)
    }

    private fun processarTagNfc(tag: Tag) {
        val bytes = tag.id
        if (bytes.isEmpty()) {
            Toast.makeText(this, "Não consegui ler o ID dessa TAG", Toast.LENGTH_SHORT).show()
            return
        }
        val invertidoDecimal = bytes.reversedArray().fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        editCartao.setText(invertidoDecimal.toString())
        Toast.makeText(this, "Cartão lido: $invertidoDecimal", Toast.LENGTH_SHORT).show()
    }

    private fun abrirSeletorData(inicio: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, ano, mes, dia ->
            cal.set(ano, mes, dia, if (inicio) 0 else 23, if (inicio) 0 else 59, if (inicio) 0 else 59)
            val iso = formatoIsapi.format(cal.time)
            val exibicao = formatoExibicao.format(cal.time)
            if (inicio) {
                dataInicio = iso
                btnValidadeInicio.text = "Início: $exibicao"
            } else {
                dataFim = iso
                btnValidadeFim.text = "Fim: $exibicao"
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun processarFoto(uri: Uri) {
        val arquivo = FotoUtils.prepararFoto(this, uri)
        caminhoFotoPreparada = arquivo.absolutePath
        imgFoto.setImageURI(Uri.fromFile(arquivo))
    }

    private fun montarChecklistEquipamentos() {
        containerEquipamentos.removeAllViews()
        predio.equipamentos.forEach { eq ->
            val cb = CheckBox(this)
            cb.text = "${eq.nome}  (porta ${eq.porta})"
            cb.isChecked = if (modoEdicao) eq.id == equipamentoIdAlvo else true
            cb.tag = eq.id
            containerEquipamentos.addView(cb)
        }
    }

    private fun equipamentosSelecionados(): List<Equipamento> {
        val idsMarcados = mutableSetOf<String>()
        for (i in 0 until containerEquipamentos.childCount) {
            val cb = containerEquipamentos.getChildAt(i) as CheckBox
            if (cb.isChecked) idsMarcados.add(cb.tag as String)
        }
        return predio.equipamentos.filter { it.id in idsMarcados }
    }

    private fun montarUsuarioCadastro(): UsuarioCadastro = UsuarioCadastro(
        employeeNo = editCodigo.text.toString().trim(),
        apartamento = editApartamento.text.toString().trim(),
        nome = editNome.text.toString().trim(),
        cartao = editCartao.text.toString().trim(),
        validadeInicio = dataInicio,
        validadeFim = dataFim,
        fotoCaminho = caminhoFotoPreparada
    )

    private fun enviarCadastro() {
        val selecionados = equipamentosSelecionados()
        if (selecionados.isEmpty()) {
            Toast.makeText(this, "Selecione ao menos um equipamento", Toast.LENGTH_SHORT).show()
            return
        }
        val usuario = montarUsuarioCadastro()
        if (usuario.nome.isBlank()) {
            Toast.makeText(this, "Informe o nome", Toast.LENGTH_SHORT).show()
            return
        }
        if (usuario.employeeNo.isBlank()) {
            Toast.makeText(this, "Informe o código do usuário", Toast.LENGTH_SHORT).show()
            return
        }
        if (!modoEdicao) contador.confirmarUso()
        containerResultados.removeAllViews()

        selecionados.forEach { eq ->
            val linha = criarLinhaResultado(eq, "Enviando...", null)
            containerResultados.addView(linha)
            lifecycleScope.launch {
                val resultado = withContext(Dispatchers.IO) { enviarParaUmEquipamento(eq, usuario) }
                atualizarLinhaResultado(linha, resultado, usuario)
            }
        }
    }

    private fun enviarParaUmEquipamento(eq: Equipamento, usuario: UsuarioCadastro): ResultadoEnvio {
        return try {
            val cliente = IsapiClient(predio, eq)
            if (modoEdicao) cliente.atualizarUsuario(usuario) else cliente.criarUsuario(usuario)
            cliente.cadastrarCartao(usuario)
            cliente.enviarFoto(usuario)
            ResultadoEnvio(eq, ResultadoStatus.SUCESSO, if (modoEdicao) "Atualizado com sucesso" else "Cadastrado com sucesso")
        } catch (e: Exception) {
            ResultadoEnvio(eq, ResultadoStatus.FALHA, e.message ?: "Falha desconhecida")
        }
    }

    private fun criarLinhaResultado(eq: Equipamento, statusTexto: String, corHex: Int?): LinearLayout {
        val linha = LinearLayout(this)
        linha.orientation = LinearLayout.HORIZONTAL
        linha.setPadding(0, 8, 0, 8)

        val textoEquip = TextView(this)
        textoEquip.text = eq.nome
        textoEquip.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val textoStatus = TextView(this)
        textoStatus.text = statusTexto
        textoStatus.tag = "status"
        if (corHex != null) textoStatus.setTextColor(corHex)

        linha.addView(textoEquip)
        linha.addView(textoStatus)
        linha.tag = eq
        return linha
    }

    private fun atualizarLinhaResultado(linha: LinearLayout, resultado: ResultadoEnvio, usuario: UsuarioCadastro) {
        val textoStatus = linha.findViewWithTag<TextView>("status")
        val cor = if (resultado.status == ResultadoStatus.SUCESSO)
            getColor(R.color.sucesso) else getColor(R.color.falha)
        textoStatus.setTextColor(cor)

        if (resultado.status == ResultadoStatus.SUCESSO) {
            textoStatus.text = "✓ OK"
            historico.registrar(
                com.oliveira.facialapp.model.RegistroCadastro(
                    predioId = predio.id,
                    equipamentoId = resultado.equipamento.id,
                    equipamentoNome = resultado.equipamento.nome,
                    apartamento = usuario.apartamento,
                    employeeNo = usuario.employeeNo,
                    nome = usuario.nome,
                    dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date())
                )
            )
        } else {
            textoStatus.text = "✗ Falhou"

            val btnDetalhes = com.google.android.material.button.MaterialButton(this)
            btnDetalhes.text = "Ver / tentar de novo"
            btnDetalhes.setOnClickListener {
                mostrarDialogoRetentativa(resultado.equipamento, usuario, resultado.mensagem, linha)
            }
            linha.addView(btnDetalhes)
        }
    }

    private fun mostrarDialogoRetentativa(eq: Equipamento, usuarioBase: UsuarioCadastro, motivo: String, linha: LinearLayout) {
        val input = EditText(this)
        input.setText(usuarioBase.employeeNo)
        input.hint = "Código do usuário"

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 0)
        val textoMotivo = TextView(this)
        textoMotivo.text = "Motivo da falha:\n$motivo"
        layout.addView(textoMotivo)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Tentar novamente em ${eq.nome}")
            .setView(layout)
            .setPositiveButton("Enviar de novo") { _, _ ->
                val usuarioNovo = usuarioBase.copy(employeeNo = input.text.toString().trim())
                val textoStatus = linha.findViewWithTag<TextView>("status")
                textoStatus.text = "Enviando..."
                textoStatus.setTextColor(getColor(android.R.color.darker_gray))
                lifecycleScope.launch {
                    val resultado = withContext(Dispatchers.IO) { enviarParaUmEquipamento(eq, usuarioNovo) }
                    if (linha.childCount > 2) linha.removeViewAt(2)
                    atualizarLinhaResultado(linha, resultado, usuarioNovo)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
