package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Team
import org.junit.Test
import kotlin.system.measureTimeMillis
import java.io.File

class StringConcatenationBenchmarkTest {

    @Test
    fun benchmarkStringConcatenationVsPreCalculated() {
        // Setup: Create 1000 matches, each with 10 players
        val matches = (1..1000).map { matchId ->
            val players = (1..10).map { playerId ->
                Player(
                    playerId = playerId,
                    playerName = "Player Name $playerId",
                    appearances = 10,
                    goals = 5
                )
            }
            MatchWithTeams(
                match = Match(
                    matchId = matchId,
                    team1Id = 1,
                    team2Id = 2,
                    team1Score = 2,
                    team2Score = 1,
                    timestamp = System.currentTimeMillis()
                ),
                team1 = Team(1, "Team 1", 0xFFFF0000.toInt(), null),
                team2 = Team(2, "Team 2", 0xFF0000FF.toInt(), null),
                players = players
            )
        }

        // --- Benchmark 1: Concatenation on the fly (Current Implementation) ---
        // Simulating 10 passes (e.g. scrolling up and down) over the list
        val passes = 10
        var totalConcatenationTime = 0L

        repeat(passes) {
            val time = measureTimeMillis {
                matches.forEach { match ->
                    // The logic from MatchHistoryAdapter.kt
                    if (match.players.isNotEmpty()) {
                        val text = "Players: ${match.players.joinToString(", ") { it.playerName }}"
                        // Consume the result to avoid JIT optimization
                        if (text.length < 0) println("Impossible")
                    }
                }
            }
            totalConcatenationTime += time
        }

        val avgConcatenationTime = totalConcatenationTime / passes.toDouble()
        println("Average Concatenation Time (per pass of 1000 items): $avgConcatenationTime ms")


        // --- Benchmark 2: Pre-calculated access (Optimized Implementation) ---
        // Pre-calculate once (simulating background thread work)
        val preCalculatedMatches = matches.map { match ->
            val formatted = if (match.players.isNotEmpty()) {
                 "Players: ${match.players.joinToString(", ") { it.playerName }}"
            } else ""
            MatchWithPreCalculatedString(match, formatted)
        }

        var totalAccessTime = 0L
        repeat(passes) {
            val time = measureTimeMillis {
                preCalculatedMatches.forEach { item ->
                    // Simulating accessing the property in onBindViewHolder
                    val text = item.formattedPlayers
                    // Consume
                    if (text.length < 0) println("Impossible")
                }
            }
            totalAccessTime += time
        }

        val avgAccessTime = totalAccessTime / passes.toDouble()
        println("Average Access Time (per pass of 1000 items): $avgAccessTime ms")

        val result = """
            Benchmark Results (1000 items, avg of $passes passes):
            Concatenation (Main Thread Cost): $avgConcatenationTime ms
            Pre-calculated Access (Main Thread Cost): $avgAccessTime ms
            Improvement: ${if (avgConcatenationTime > 0) String.format("%.2fx", avgConcatenationTime / avgAccessTime.coerceAtLeast(0.001)) else "N/A"}
        """.trimIndent()

        println(result)
        File("/tmp/string_benchmark_result.txt").writeText(result)
    }

    data class MatchWithPreCalculatedString(
        val match: MatchWithTeams,
        val formattedPlayers: String
    )
}
