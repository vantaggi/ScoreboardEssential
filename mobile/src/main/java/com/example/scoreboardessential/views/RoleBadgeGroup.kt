package com.example.scoreboardessential.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.example.scoreboardessential.R
import com.example.scoreboardessential.database.Role
import com.example.scoreboardessential.utils.RoleUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup

class RoleBadgeGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ChipGroup(context, attrs, defStyleAttr) {

    init {
        chipSpacingHorizontal = 4
        isSingleLine = true
    }

    fun setRoles(roles: List<Role>) {
        removeAllViews()

        if (roles.isEmpty()) {
            addView(createEmptyBadge())
            return
        }

        roles.forEach { role ->
            addView(createRoleBadge(role))
        }
    }

    private fun createRoleBadge(role: Role): MaterialCardView {
        val badge = LayoutInflater.from(context)
            .inflate(R.layout.view_role_badge, this, false) as MaterialCardView

        val textView = badge.findViewById<TextView>(R.id.role_badge_text)
        textView.text = RoleUtils.getRoleAbbreviation(role.name)

        val color = RoleUtils.getCategoryColor(context, role.category)
        badge.setCardBackgroundColor(color)

        return badge
    }

    private fun createEmptyBadge(): MaterialCardView {
        val badge = LayoutInflater.from(context)
            .inflate(R.layout.view_role_badge, this, false) as MaterialCardView

        val textView = badge.findViewById<TextView>(R.id.role_badge_text)
        textView.text = "N/A"

        badge.setCardBackgroundColor(
            context.getColor(R.color.surface_variant_dark)
        )

        return badge
    }
}
