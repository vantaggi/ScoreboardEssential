package it.vantaggi.scoreboardessential.utils

import android.view.LayoutInflater
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.Role

/**
 * I chip dei ruoli di un giocatore: la sigla (POR, DC...) con il nome intero per TalkBack, solo bordo
 * e testo come ogni badge. Un giocatore senza ruoli non ha chip: e' il nome e basta.
 */
fun ChipGroup.setRoles(roles: List<Role>) {
    removeAllViews()

    val inflater = LayoutInflater.from(context)
    roles.forEach { role ->
        val chip = inflater.inflate(R.layout.view_role_chip, this, false) as Chip
        chip.text = RoleUtils.getRoleAbbreviation(role.name)
        chip.contentDescription = role.name
        addView(chip)
    }
}
