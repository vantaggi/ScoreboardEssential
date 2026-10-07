package it.vantaggi.scoreboardessential.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.R

/**
 * Lo stato vuoto della UI Constitution (EmptyState): dice che cosa e' vuoto (titolo), perche' e cosa
 * fare (motivo) e, se c'e' una strada, offre il comando. Vive al centro della regione che sarebbe
 * stata piena. L'icona e' a icon-prominent e non e' letta da TalkBack: e' un aiuto, non il messaggio.
 */
class EmptyStateView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val titleView: TextView
        private val reasonView: TextView
        val actionButton: MaterialButton

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val gutter = resources.getDimensionPixelSize(R.dimen.space_32)
            setPadding(gutter, gutter, gutter, gutter)
            LayoutInflater.from(context).inflate(R.layout.view_empty_state, this, true)
            titleView = findViewById(R.id.empty_title)
            reasonView = findViewById(R.id.empty_reason)
            actionButton = findViewById(R.id.empty_action)
            val icona = findViewById<ImageView>(R.id.empty_icon)

            val letti = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView)
            try {
                val iconaId = letti.getResourceId(R.styleable.EmptyStateView_emptyIcon, 0)
                if (iconaId != 0) icona.setImageResource(iconaId) else icona.visibility = View.GONE
                titleView.text = letti.getText(R.styleable.EmptyStateView_emptyTitle)
                reasonView.text = letti.getText(R.styleable.EmptyStateView_emptyReason)
                val azione = letti.getText(R.styleable.EmptyStateView_emptyAction)
                actionButton.text = azione
                actionButton.visibility = if (azione.isNullOrEmpty()) View.GONE else View.VISIBLE
            } finally {
                letti.recycle()
            }
        }

        /** Cambia titolo e motivo: la ricerca senza risultati dice altro dall'elenco vuoto. */
        fun setMessage(
            title: CharSequence,
            reason: CharSequence,
        ) {
            titleView.text = title
            reasonView.text = reason
        }
    }
