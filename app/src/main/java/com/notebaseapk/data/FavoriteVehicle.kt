package com.notebaseapk.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_vehicles",
    indices = [
        Index(value = ["nopol", "leasing", "cabang"], unique = true)
    ]
)
data class FavoriteVehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val nopol: String,
    val leasing: String,
    val cabang: String,

    val createdAt: Long = System.currentTimeMillis()
)