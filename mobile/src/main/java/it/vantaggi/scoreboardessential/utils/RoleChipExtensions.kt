package it.vantaggi.scoreboardessential.utils

import android.content.res.ColorStateList
import android.view.LayoutInflater
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.Role

fun ChipGroup.setRoles(roles: List<Role>) {
    removeAllViews()

    val inflater = LayoutInflater.from(context)

    if (roles.isEmpty()) {
        val chip = inflater.inflate(R.layout.view_role_chip, this, false) as Chip
        chip.text = "N/A"
        // Use colorSurface as a default for empty state, similar to how RoleBadgeGroup did it
        val surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
        chip.chipBackgroundColor = ColorStateList.valueOf(surfaceColor)
        addView(chip)
        return
    }

    roles.forEach { role ->
        val chip = inflater.inflate(R.layout.view_role_chip, this, false) as Chip
        chip.text = RoleUtils.getRoleAbbreviation(role.name)
        val color = RoleUtils.getCategoryColor(context, role.category)
        chip.chipBackgroundColor = ColorStateList.valueOf(color)
        addView(chip)
    }
}
