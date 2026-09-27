package com.oliveira.facialapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliveira.facialapp.R
import com.oliveira.facialapp.model.Equipamento

class EquipamentoViewAdapter(
    private var itens: List<Equipamento>,
    private val onAbrirPorta: (Equipamento) -> Unit
) : RecyclerView.Adapter<EquipamentoViewAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val nome: android.widget.TextView = view.findViewById(R.id.textNomeEquip)
        val ip: android.widget.TextView = view.findViewById(R.id.textIpEquip)
        val btnAbrir: android.view.View = view.findViewById(R.id.btnAbrirPorta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_equipamento_view, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val eq = itens[position]
        holder.nome.text = eq.nome.ifBlank { "(sem nome)" }
        holder.ip.text = "Porta ${eq.porta}"
        holder.btnAbrir.setOnClickListener { onAbrirPorta(eq) }
    }

    override fun getItemCount() = itens.size

    fun atualizar(novos: List<Equipamento>) {
        itens = novos
        notifyDataSetChanged()
    }
}
