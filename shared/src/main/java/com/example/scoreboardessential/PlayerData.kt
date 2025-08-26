package com.example.scoreboardessential

data class PlayerData(
    val id: Int,
    val name: String,
    val roles: List<String>,
    val goals: Int = 0,
    val appearances: Int = 0
)
