package com.notebaseapk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVehicleDao {

    @Query("""
        SELECT DISTINCT k.* FROM kendaraan k
        INNER JOIN favorite_vehicles f
        ON (
            (f.nopolKey != '' AND k.nopolKey = f.nopolKey)
            OR
            (f.nopolKey = '' 
                AND k.nopol = f.nopol
                AND k.leasing = f.leasing
                AND k.cabang = f.cabang
            )
        )
        ORDER BY f.createdAt DESC
    """)
    fun getFavoriteKendaraanList(): Flow<List<Kendaraan>>

    @Query("""
        SELECT * FROM kendaraan
        WHERE TRIM(catatan) != ''
        ORDER BY CAST(groupNumber AS INTEGER) ASC, nopol ASC
    """)
    fun getCatatanKendaraanList(): Flow<List<Kendaraan>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteVehicle)

    @Query("""
        DELETE FROM favorite_vehicles
        WHERE 
            (nopolKey = :nopolKey AND :nopolKey != '')
            OR
            (
                nopol = :nopol
                AND leasing = :leasing
                AND cabang = :cabang
            )
    """)
    suspend fun deleteFavoriteByKey(
        nopolKey: String,
        nopol: String,
        leasing: String,
        cabang: String
    )

    @Query("""
        SELECT COUNT(*) FROM favorite_vehicles
        WHERE 
            (nopolKey = :nopolKey AND :nopolKey != '')
            OR
            (
                nopol = :nopol
                AND leasing = :leasing
                AND cabang = :cabang
            )
    """)
    suspend fun isFavorite(
        nopolKey: String,
        nopol: String,
        leasing: String,
        cabang: String
    ): Int

    @Query("SELECT * FROM favorite_vehicles ORDER BY createdAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteVehicle>>

    @Query("DELETE FROM favorite_vehicles")
    suspend fun deleteAllFavorites()
}