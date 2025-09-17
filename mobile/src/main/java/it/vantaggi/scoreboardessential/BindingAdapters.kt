package it.vantaggi.scoreboardessential

import android.widget.TextView
import androidx.databinding.BindingAdapter
import it.vantaggi.scoreboardessential.database.Role

@BindingAdapter("app:roles")
fun setRoles(textView: TextView, roles: List<Role>?) {
    textView.text = roles?.joinToString(", ") { it.name } ?: ""
}
