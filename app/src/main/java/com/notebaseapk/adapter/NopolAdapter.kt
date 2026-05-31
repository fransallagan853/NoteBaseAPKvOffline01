package com.notebaseapk.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ItemNopolCompactBinding

class NopolAdapter(
    private var list: List<Kendaraan>,
    private val onItemClick: (Kendaraan) -> Unit
) : RecyclerView.Adapter<NopolAdapter.NopolViewHolder>() {

    fun updateData(newList: List<Kendaraan>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NopolViewHolder {
        val binding = ItemNopolCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NopolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NopolViewHolder, position: Int) {
        val item = list[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = list.size

    inner class NopolViewHolder(private val binding: ItemNopolCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Kendaraan) {
            binding.tvNopol.text = item.nopol
            
            val details = mutableListOf<String>()
            if (!item.namaKendaraan.isNullOrEmpty()) details.add(item.namaKendaraan)
            if (!item.tahun.isNullOrEmpty()) details.add(item.tahun)
            if (!item.warna.isNullOrEmpty()) details.add(item.warna)
            if (!item.leasing.isNullOrEmpty()) details.add(item.leasing)
            
            binding.tvDetail.text = details.joinToString(" • ")
            
            if (!item.saldo.isNullOrEmpty() || !item.overdue.isNullOrEmpty()) {
                binding.tvExtra.visibility = View.VISIBLE
                val saldoStr = if (item.saldo.isNullOrEmpty()) "-" else item.saldo
                val ovdStr = if (item.overdue.isNullOrEmpty()) "-" else item.overdue
                binding.tvExtra.text = "Saldo: $saldoStr • OVD: $ovdStr"
            } else {
                binding.tvExtra.visibility = View.GONE
            }
            
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
