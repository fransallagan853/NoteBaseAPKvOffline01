package com.notebaseapk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVehicleDao {

    @Query("""
    SELECT k.* FROM kendaraan k
    INNER JOIN favorite_vehicles f
    ON k.nopolKey = f.nopolKey
    WHERE k.id = (
        SELECT k2.id
        FROM kendaraan k2
        WHERE k2.nopolKey = k.nopolKey
        ORDER BY 
            CASE 
                WHEN LENGTH(k2.periodeData) = 5 
                THEN CAST(SUBSTR(k2.periodeData, 4, 2) AS INTEGER) * 100 
                     + CAST(SUBSTR(k2.periodeData, 1, 2) AS INTEGER)
                ELSE 0
            END DESC,
            k2.id DESC
        LIMIT 1
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
