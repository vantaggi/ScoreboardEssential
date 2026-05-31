package it.vantaggi.scoreboardessential.shared

/**
 * Lightweight, cross-module representation of a player used for phone↔watch sync.
 *
 * The Wearable Data Layer only carries primitive values, so a roster is serialized
 * into a single string via [encodeList] / [decodeList]. Control characters are used
 * as separators so they never collide with player or role names.
 */
data class PlayerData(
    val id: Int,
    val name: String,
    val roles: List<String>,
    val goals: Int = 0,
    val appearances: Int = 0,
) {
    companion object {
        private const val FIELD_SEP = "" // Unit Separator
        private const val RECORD_SEP = "" // Record Separator
        private const val ROLE_SEP = "" // Group Separator

        /** Serializes a roster into a single transport string. */
        fun encodeList(players: List<PlayerData>): String =
            players.joinToString(RECORD_SEP) { p ->
                listOf(
                    p.id.toString(),
                    p.name,
                    p.roles.joinToString(ROLE_SEP),
                    p.goals.toString(),
                    p.appearances.toString(),
                ).joinToString(FIELD_SEP)
            }

        /** Parses a transport string produced by [encodeList]. Tolerates malformed records. */
        fun decodeList(raw: String?): List<PlayerData> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 3) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1]
                if (name.isBlank()) return@mapNotNull null
                val roles = if (parts[2].isEmpty()) emptyList() else parts[2].split(ROLE_SEP)
                val goals = parts.getOrNull(3)?.toIntOrNull() ?: 0
                val appearances = parts.getOrNull(4)?.toIntOrNull() ?: 0
                PlayerData(id, name, roles, goals, appearances)
            }
        }
    }
}
