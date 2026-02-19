package it.vantaggi.scoreboardessential.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RoleUtilsTest {
    @ParameterizedTest
    @CsvSource(
        // PORTA
        "Portiere, POR",
        // DIFESA
        "Difensore Centrale, DC",
        "Terzino Sinistro, TS",
        "Terzino Destro, TD",
        "Libero, LIB",
        // CENTROCAMPO
        "Mediano, MED",
        "Centrocampista Centrale, CC",
        "Trequartista, TRQ",
        "Esterno Sinistro, ES",
        "Esterno Destro, ED",
        // ATTACCO
        "Ala Sinistra, AS",
        "Ala Destra, AD",
        "Seconda Punta, SP",
        "Centravanti, ATT",
        // Fallback - Single Word
        "Unknown, UNK",
        "Portierone, POR",
        "A, A",
        // Fallback - Multi Word
        "Attacking Midfielder, AM",
        "Very Long Role Name, VLR",
        "A B, AB",
        // Edge Cases
        "'', ''", // Empty string
        "'   ', ''", // Spaces only
        "'A  B', AB", // Consecutive spaces
        "' A B ', AB", // Leading/Trailing spaces
    )
    fun testGetRoleAbbreviation(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, RoleUtils.getRoleAbbreviation(input))
    }
}
