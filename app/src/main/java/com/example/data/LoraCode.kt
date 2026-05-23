package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lora_codes")
data class LoraCode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val payload: String,
    val frequency: Double,
    val sf: Int, // Spreading Factor (e.g., 7, 8)
    val bw: Double, // Bandwidth in kHz (e.g., 125.0, 250.0)
    val crc: Boolean,
    val rssi: Int = -55,
    val snr: Double = 8.5,
    val timestamp: Long = System.currentTimeMillis()
)
