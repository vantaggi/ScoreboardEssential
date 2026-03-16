package it.vantaggi.scoreboardessential.utils

import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import org.junit.Test
import kotlin.system.measureTimeMillis

class MatchReportUtilsBenchmark {

    @Test
    fun benchmarkScorersProcessing() {
        // Setup: Create 10000 match events, with many goals
        val matchEvents = (1..10000).map { i ->
            MatchEvent(
                timestamp = "10:00",
                event = if (i % 2 == 0) "Goal" else "Foul",
                player = "Player ${i % 20}",
                team = if (i % 2 == 0) 1 else 2
            )
        }

        val iterations = 100
        var totalTimeCurrent = 0L

        repeat(iterations) {
            val time = measureTimeMillis {
                // CURRENT IMPLEMENTATION LOGIC
                val scorers = matchEvents
                    .filter { it.event == "Goal" && it.player != null }
                    .map { it.player!! }
                    .groupingBy { it }
                    .eachCount()

                if (scorers.isNotEmpty()) {
                    scorers.forEach { (playerName, goalCount) ->
                        // Simulating text = "$playerName ($goalCount)"
                        val text = "$playerName ($goalCount)"
                        if (text.isEmpty()) println("Empty") // consume
                    }
                }
            }
            totalTimeCurrent += time
        }

        val avgTimeCurrent = totalTimeCurrent / iterations.toDouble()
        println("Average Execution Time (Current): $avgTimeCurrent ms")

        // OPTIMIZED IMPLEMENTATION LOGIC (PREVIEW)
        var totalTimeOptimized = 0L
        repeat(iterations) {
            val time = measureTimeMillis {
                val scorers = mutableMapOf<String, Int>()
                for (event in matchEvents) {
                    if (event.event == "Goal") {
                        val playerName = event.player
                        if (playerName != null) {
                            scorers[playerName] = (scorers[playerName] ?: 0) + 1
                        }
                    }
                }

                if (scorers.isNotEmpty()) {
                    val sb = StringBuilder()
                    scorers.forEach { (playerName, goalCount) ->
                        sb.setLength(0)
                        sb.append(playerName).append(" (").append(goalCount).append(")")
                        val text = sb.toString()
                        if (text.isEmpty()) println("Empty") // consume
                    }
                }
            }
            totalTimeOptimized += time
        }

        val avgTimeOptimized = totalTimeOptimized / iterations.toDouble()
        println("Average Execution Time (Optimized): $avgTimeOptimized ms")

        println("Improvement: ${avgTimeCurrent / avgTimeOptimized}x")
    }
}
