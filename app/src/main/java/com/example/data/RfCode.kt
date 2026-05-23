package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rf_codes")
data class RfCode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rawCode: String,
    val protocol: String,
    val timestamp: Long = System.currentTimeMillis(),
    val frequency: Double = 433.92
)
