package it.vantaggi.scoreboardessential.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation

/**
* Custom View che disegna una formazione tattica in stile "campo da calcio"
*/
class FormationView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var formation: Formation? = null

        private val fieldPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.team_electric_green)
                style = Paint.Style.FILL
                alpha = 50 // Semi-trasparente
            }

        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.stencil_white)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }

        private val playerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.graffiti_pink)
                style = Paint.Style.FILL
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.stencil_white)
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }

        private val playerRadius = 40f
        private val fieldRect = RectF()

        fun setFormation(formation: Formation) {
            this.formation = formation
            invalidate() // Ridisegna la view
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Lascia margini per i giocatori
            fieldRect.set(
                playerRadius * 2,
                playerRadius * 2,
                w.toFloat() - playerRadius * 2,
                h.toFloat() - playerRadius * 2,
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Disegna il campo
            canvas.drawRoundRect(fieldRect, 20f, 20f, fieldPaint)
            canvas.drawRoundRect(fieldRect, 20f, 20f, linePaint)

            // Linea di metà campo
            val midYLine = fieldRect.centerY()
            canvas.drawLine(
                fieldRect.left,
                midYLine,
                fieldRect.right,
                midYLine,
                linePaint,
            )

            val form = formation ?: return

            // Calcola le posizioni Y per ogni linea
            val fieldHeight = fieldRect.height()
            val sectionHeight = fieldHeight / 5 // 5 sezioni: GK, DEF, MID, ATT, top margin

            val gkY = fieldRect.bottom - sectionHeight * 0.5f
            val defY = fieldRect.bottom - sectionHeight * 1.5f
            val midY = fieldRect.bottom - sectionHeight * 2.5f
            val attY = fieldRect.bottom - sectionHeight * 3.5f

            // Disegna i giocatori
            drawPlayersInLine(canvas, form.goalkeeper, gkY)
            drawPlayersInLine(canvas, form.defenders, defY)
            drawPlayersInLine(canvas, form.midfielders, midY)
            drawPlayersInLine(canvas, form.forwards, attY)
        }

        private fun drawPlayersInLine(
            canvas: Canvas,
            players: List<PlayerWithRoles>,
            yPosition: Float,
        ) {
            if (players.isEmpty()) return

            val fieldWidth = fieldRect.width()
            val spacing = fieldWidth / (players.size + 1)

            players.forEachIndexed { index, player ->
                val xPosition = fieldRect.left + spacing * (index + 1)

                // Disegna cerchio giocatore
                canvas.drawCircle(xPosition, yPosition, playerRadius, playerPaint)

                // Disegna iniziale del nome (se è PlayerWithRoles)
                val initial =
                    player.player.playerName
                        .firstOrNull()
                        ?.uppercase() ?: "?"
                canvas.drawText(
                    initial,
                    xPosition,
                    yPosition + 12f, // Offset per centrare verticalmente
                    textPaint,
                )
            }
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            // Mantieni aspect ratio 2:3 (tipico di un campo da calcio visto dall'alto)
            val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
            val desiredHeight = (desiredWidth * 1.5f).toInt()

            setMeasuredDimension(
                desiredWidth,
                resolveSize(desiredHeight, heightMeasureSpec),
            )
        }
    }
