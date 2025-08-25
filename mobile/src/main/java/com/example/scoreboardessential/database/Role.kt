package com.example.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roles")
data class Role(
    @PrimaryKey(autoGenerate = true)
    val roleId: Int = 0,
    val name: String,
    val category: String
) {
    override fun toString(): String {
        return name
    }
}
