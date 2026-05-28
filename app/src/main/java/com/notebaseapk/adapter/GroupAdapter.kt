package com.notebaseapk.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notebaseapk.data.GroupNopol
import com.notebaseapk.databinding.ItemNopolGroupBinding

class GroupAdapter(
    private var groups: List<GroupNopol>,
    private val onItemClick: (GroupNopol) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    fun updateData(newGroups: List<GroupNopol>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemNopolGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.bind(group)
    }

    override fun getItemCount(): Int = groups.size

    inner class GroupViewHolder(private val binding: ItemNopolGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: GroupNopol) {
            binding.tvGroupNumber.text = group.groupNumber
            binding.tvJumlah.text = "${group.jumlah} data"
            binding.root.setOnClickListener { onItemClick(group) }
        }
    }
}
