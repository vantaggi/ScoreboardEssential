package it.vantaggi.scoreboardessential.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation

/**
 * Custom View che disegna una formazione tattica in stile "campo da calcio"
 * Refactored to use FrameLayout and View markers instead of Canvas drawing.
 */
class FormationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var formation: Formation? = null
    private var onPlayerClickListener: ((PlayerWithRoles) -> Unit)? = null

    init {
        // Imposta il background del campo
        background = ContextCompat.getDrawable(context, R.drawable.bg_football_field)
    }

    fun setFormation(formation: Formation) {
        this.formation = formation
        updateViews()
    }

    fun setOnPlayerClickListener(listener: (PlayerWithRoles) -> Unit) {
        this.onPlayerClickListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Aggiorna le posizioni quando la dimensione cambia
        if (formation != null) {
            updateViews()
        }
    }

    private fun updateViews() {
        // Se la view non ha ancora dimensioni, aspetta
        if (width == 0 || height == 0) return

        val currentFormation = formation ?: return

        removeAllViews()

        // Definisci le linee (percentuali dall'alto)
        // GK: 90%, DEF: 75%, MID: 50%, FWD: 25%
        // Nota: Il campo è disegnato dall'alto (0) al basso (height).
        // Normalmente il portiere è in basso in una visualizzazione tattica verticale.
        val gkY = 0.90f
        val defY = 0.75f
        val midY = 0.50f
        val fwdY = 0.25f

        addPlayersRow(currentFormation.goalkeeper, gkY)
        addPlayersRow(currentFormation.defenders, defY)
        addPlayersRow(currentFormation.midfielders, midY)
        addPlayersRow(currentFormation.forwards, fwdY)
    }

    private fun addPlayersRow(players: List<PlayerWithRoles>, yPercent: Float) {
        if (players.isEmpty()) return

        val count = players.size
        // Spaziatura orizzontale
        val spacing = width.toFloat() / (count + 1)

        players.forEachIndexed { index, player ->
            val playerView = LayoutInflater.from(context).inflate(R.layout.view_formation_player_marker, this, false)

            // Setup dati giocatore
            val cardView = playerView.findViewById<MaterialCardView>(R.id.card_avatar)
            val initialText = playerView.findViewById<TextView>(R.id.text_initial)
            val nameText = playerView.findViewById<TextView>(R.id.text_name)

            val initial = player.player.playerName.firstOrNull()?.uppercase() ?: "?"
            initialText.text = initial
            nameText.text = player.player.playerName

            // Setup click listener
            playerView.setOnClickListener {
                onPlayerClickListener?.invoke(player)
            }

            // Misura la view per centrarla correttamente
            playerView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )

            val viewWidth = playerView.measuredWidth
            val viewHeight = playerView.measuredHeight

            // Calcola posizione
            val cx = spacing * (index + 1)
            val cy = height * yPercent

            val left = (cx - viewWidth / 2).toInt()
            val top = (cy - viewHeight / 2).toInt()

            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            params.leftMargin = left
            params.topMargin = top

            playerView.layoutParams = params
            addView(playerView)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Mantieni aspect ratio 2:3
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val desiredWidth = widthSize
        // Se width è UNSPECIFIED (es. ScrollView), potremmo avere problemi, ma assumiamo MATCH_PARENT o fisso
        val desiredHeight = (desiredWidth * 1.5f).toInt()

        val newWidthSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY)
        val newHeightSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)

        super.onMeasure(newWidthSpec, newHeightSpec)
    }
}
