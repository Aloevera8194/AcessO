package com.oliveira.facialapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliveira.facialapp.R
import com.oliveira.facialapp.model.Equipamento

class EquipamentoManageAdapter(
    private var itens: List<Equipamento>,
    private val onEditar: (Equipamento) -> Unit,
    private val onExcluir: (Equipamento) -> Unit
) : RecyclerView.Adapter<EquipamentoManageAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val nome: android.widget.TextView = view.findViewById(R.id.textNomeEquip)
        val ip: android.widget.TextView = view.findViewById(R.id.textIpEquip)
        val editar: android.view.View = view.findViewById(R.id.btnEditarEquip)
        val excluir: android.view.View = view.findViewById(R.id.btnExcluirEquip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_equipamento_manage, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val eq = itens[position]
        holder.nome.text = eq.nome.ifBlank { "(sem nome)" }
        val loginProprio = if (eq.usuario.isNotBlank()) " · login próprio" else ""
        holder.ip.text = "Porta ${eq.porta}$loginProprio"
        holder.editar.setOnClickListener { onEditar(eq) }
        holder.excluir.setOnClickListener { onExcluir(eq) }
    }

    override fun getItemCount() = itens.size

    fun atualizar(novos: List<Equipamento>) {
        itens = novos
        notifyDataSetChanged()
    }
}
