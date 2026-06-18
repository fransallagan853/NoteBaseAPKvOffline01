package com.notebaseapk.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ItemNopolCompactBinding
import com.notebaseapk.util.NopolFormatter

class NopolAdapter(
    private var list: List<Kendaraan>,
    private val onItemClick: (Kendaraan) -> Unit
) : RecyclerView.Adapter<NopolAdapter.NopolViewHolder>() {

    fun updateData(newList: List<Kendaraan>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NopolViewHolder {
        val binding = ItemNopolCompactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NopolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NopolViewHolder, position: Int) {
        val item = list[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = list.size

    inner class NopolViewHolder(
        private val binding: ItemNopolCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Kendaraan) {
            binding.tvNopol.text = NopolFormatter.display(item.nopol)

            val details = mutableListOf<String>()

            if (item.namaKendaraan.isNotEmpty()) {
                details.add(item.namaKendaraan)
            }

            if (item.tahun.isNotEmpty()) {
                details.add(item.tahun)
            }

            if (item.warna.isNotEmpty()) {
                details.add(item.warna)
            }

            if (item.leasing.isNotEmpty()) {
                val leasingText = if (item.periodeData.isNotEmpty()) {
                    "${item.leasing} (${item.periodeData})"
                } else {
                    item.leasing
                }

                details.add(leasingText)
            }

            binding.tvDetail.text = details.joinToString(" • ")

            if (item.saldo.isNotEmpty() || item.overdue.isNotEmpty()) {
                binding.tvExtra.visibility = View.VISIBLE

                val saldoStr = item.saldo.ifEmpty { "-" }
                val ovdStr = item.overdue.ifEmpty { "-" }

                binding.tvExtra.text = "Saldo: $saldoStr • OVD: $ovdStr"
            } else {
                binding.tvExtra.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}