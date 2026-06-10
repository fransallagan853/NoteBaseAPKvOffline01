package com.notebaseapk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVehicleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteVehicle)

    @Query("""
        DELETE FROM favorite_vehicles
        WHERE nopol = :nopol
        AND leasing = :leasing
        AND cabang = :cabang
    """)
    suspend fun deleteFavoriteByKey(
        nopol: String,
        leasing: String,
        cabang: String
    )

    @Query("""
        SELECT COUNT(*) FROM favorite_vehicles
        WHERE nopol = :nopol
        AND leasing = :leasing
        AND cabang = :cabang
    """)
    suspend fun isFavorite(
        nopol: String,
        leasing: String,
        cabang: String
    ): Int

    @Query("SELECT * FROM favorite_vehicles ORDER BY createdAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteVehicle>>

    @Query("DELETE FROM favorite_vehicles")
    suspend fun deleteAllFavorites()
}