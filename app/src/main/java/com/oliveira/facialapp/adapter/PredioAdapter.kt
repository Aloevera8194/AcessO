package com.oliveira.facialapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliveira.facialapp.R
import com.oliveira.facialapp.model.Predio

class PredioAdapter(
    private var itens: List<Predio>,
    private val onAbrir: (Predio) -> Unit,
    private val onConfigurar: (Predio) -> Unit
) : RecyclerView.Adapter<PredioAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val nome: android.widget.TextView = view.findViewById(R.id.textNomePredio)
        val qtd: android.widget.TextView = view.findViewById(R.id.textQtdEquipamentos)
        val areaAbrir: android.view.View = view.findViewById(R.id.areaAbrirPredio)
        val btnConfig: android.view.View = view.findViewById(R.id.btnConfigPredio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_predio, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val predio = itens[position]
        holder.nome.text = predio.nome
        val n = predio.equipamentos.size
        holder.qtd.text = if (n == 1) "1 equipamento" else "$n equipamentos"
        holder.areaAbrir.setOnClickListener { onAbrir(predio) }
        holder.btnConfig.setOnClickListener { onConfigurar(predio) }
    }

    override fun getItemCount() = itens.size

    fun atualizar(novos: List<Predio>) {
        itens = novos
        notifyDataSetChanged()
    }
}
