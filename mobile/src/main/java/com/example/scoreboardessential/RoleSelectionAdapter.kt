package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Role
import com.google.android.material.checkbox.MaterialCheckBox

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_ROLE = 1

class RoleSelectionAdapter(
    private val onRoleSelected: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private val selectedRoleIds = mutableSetOf<Int>()

    fun submitList(roles: List<Role>, initiallySelectedIds: List<Int>) {
        selectedRoleIds.clear()
        selectedRoleIds.addAll(initiallySelectedIds)

        items.clear()
        val groupedRoles = roles.groupBy { it.category }
        groupedRoles.forEach { (category, roleList) ->
            items.add(category)
            items.addAll(roleList)
        }
        notifyDataSetChanged()
    }

    fun getSelectedRoleIds(): List<Int> = selectedRoleIds.toList()

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_ROLE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_role_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_role, parent, false)
            RoleViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(items[position] as String)
            is RoleViewHolder -> {
                val role = items[position] as Role
                holder.bind(role, selectedRoleIds.contains(role.roleId)) { roleId, isChecked ->
                    if (isChecked) {
                        selectedRoleIds.add(roleId)
                    } else {
                        selectedRoleIds.remove(roleId)
                    }
                    onRoleSelected(roleId, isChecked)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.header_title)
        fun bind(category: String) {
            title.text = category
        }
    }

    class RoleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.role_checkbox)
        fun bind(role: Role, isSelected: Boolean, onToggle: (Int, Boolean) -> Unit) {
            checkBox.text = role.name
            checkBox.isChecked = isSelected
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(role.roleId, isChecked)
            }
        }
    }
}
