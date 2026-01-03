package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Role
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlayersManagementAdapterTest {

    private lateinit var adapter: PlayersManagementAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_ScoreboardEssential)
        adapter = PlayersManagementAdapter(onPlayerClick = {}, onStatsClick = {})
    }

    @Test
    fun bind_setsAvatarTextAndBackgroundColorCorrectly() {
        val player = Player(playerId = 1, playerName = "Vandal", goals = 10, appearances = 5)
        val role = Role(1, "Striker", "ATTACCO")
        val playerWithRoles = PlayerWithRoles(player, listOf(role))

        adapter.submitList(listOf(playerWithRoles))

        // Create a dummy ViewHolder
        val parent = android.widget.FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        // Bind data
        adapter.onBindViewHolder(viewHolder, 0)

        val avatarTextView = viewHolder.itemView.findViewById<TextView>(R.id.player_avatar)
        val avatarCardView = viewHolder.itemView.findViewById<MaterialCardView>(R.id.player_avatar_card)

        // Verify text
        assertEquals("V", avatarTextView.text.toString())

        // Verify background color logic
        val colors = context.resources.getIntArray(R.array.avatar_colors)
        val expectedColorIndex = Math.abs(player.playerName.hashCode()) % colors.size
        val expectedColor = colors[expectedColorIndex]

        val background = avatarCardView.cardBackgroundColor
        assertEquals(expectedColor, background.defaultColor)
    }
}
