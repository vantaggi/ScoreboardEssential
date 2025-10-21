package it.vantaggi.scoreboardessential.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.Role
import it.vantaggi.scoreboardessential.utils.RoleUtils

class RoleBadgeGroup
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
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
            val badge =
                LayoutInflater
                    .from(context)
                    .inflate(R.layout.view_role_badge, this, false) as MaterialCardView

            val textView = badge.findViewById<TextView>(R.id.role_badge_text)
            textView.text = RoleUtils.getRoleAbbreviation(role.name)

            val color = RoleUtils.getCategoryColor(context, role.category)
            badge.setCardBackgroundColor(color)

            return badge
        }

        private fun createEmptyBadge(): MaterialCardView {
            val badge =
                LayoutInflater
                    .from(context)
                    .inflate(R.layout.view_role_badge, this, false) as MaterialCardView

            val textView = badge.findViewById<TextView>(R.id.role_badge_text)
            textView.text = "N/A"

            badge.setCardBackgroundColor(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, "Error"),
            )

            return badge
        }
    }
