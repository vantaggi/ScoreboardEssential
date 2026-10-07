package it.vantaggi.scoreboardessential

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun bind_setsNameAndRoleAbbreviation() {
        val player = Player(playerId = 1, playerName = "Vandal", goals = 10, appearances = 5)
        val role = Role(1, "Portiere", "PORTA")
        adapter.submitList(listOf(PlayerWithRoles(player, listOf(role))))

        val parent = android.widget.FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        assertEquals(
            "Vandal",
            viewHolder.itemView
                .findViewById<TextView>(R.id.player_name)
                .text
                .toString(),
        )
        val roles = viewHolder.itemView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.player_roles_group)
        assertEquals("POR", (roles.getChildAt(0) as com.google.android.material.chip.Chip).text.toString())
    }
}
