package com.notebaseapk.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KendaraanDao {
    @Query("SELECT * FROM kendaraan ORDER BY nopol ASC")
    fun getAllKendaraan(): Flow<List<Kendaraan>>

    @Query("SELECT * FROM kendaraan")
    suspend fun getAllKendaraanList(): List<Kendaraan>

    @Query("""
        SELECT 
            printf('%04d', CAST(groupNumber AS INTEGER)) AS groupNumber,
            COUNT(*) as jumlah
        FROM kendaraan
        WHERE 
            :keyword = ''
            OR printf('%04d', CAST(groupNumber AS INTEGER)) LIKE :keyword || '%'
            OR groupNumber LIKE :keyword || '%'
        GROUP BY CAST(groupNumber AS INTEGER)
        ORDER BY CAST(groupNumber AS INTEGER) ASC
    """)
    fun getGroupNopol(keyword: String): Flow<List<GroupNopol>>

    @Query("""
        SELECT * FROM kendaraan 
        WHERE CAST(groupNumber AS INTEGER) = CAST(:groupNumber AS INTEGER)
        AND searchKey LIKE '%' || :filter || '%'
        ORDER BY nopol ASC
    """)
    fun getKendaraanByGroup(groupNumber: String, filter: String): Flow<List<Kendaraan>>

    @Query("SELECT * FROM kendaraan WHERE searchKey LIKE '%' || :normalizedKeyword || '%' ORDER BY nopol ASC")
    fun searchKendaraanSpesifik(normalizedKeyword: String): Flow<List<Kendaraan>>

    @Query("SELECT * FROM kendaraan WHERE id = :id")
    suspend fun getKendaraanById(id: Int): Kendaraan?

    @Query("SELECT * FROM kendaraan WHERE nopolKey = :nopolKey LIMIT 1")
    suspend fun getKendaraanByNopolKey(nopolKey: String): Kendaraan?

    @Query("SELECT * FROM kendaraan WHERE nopol = :nopol AND leasing = :leasing LIMIT 1")
    suspend fun getKendaraanByNopolAndLeasing(nopol: String, leasing: String): Kendaraan?

    @Query("SELECT * FROM kendaraan WHERE nopol = :nopol AND leasing = :leasing AND cabang = :cabang LIMIT 1")
    suspend fun getKendaraanByNopolLeasingCabang(nopol: String, leasing: String, cabang: String): Kendaraan?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(kendaraan: Kendaraan): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(kendaraanList: List<Kendaraan>): List<Long>

    @Update
    suspend fun update(kendaraan: Kendaraan)

    @Update
    suspend fun updateAll(kendaraanList: List<Kendaraan>)

    @Delete
    suspend fun delete(kendaraan: Kendaraan)

    @Query("DELETE FROM kendaraan")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM kendaraan")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kendaraan")
    suspend fun getCountSync(): Int

    @Query("""
    DELETE FROM kendaraan
    WHERE nopolKey != ''
    AND id NOT IN (
        SELECT id FROM (
            SELECT k.id
            FROM kendaraan k
            WHERE k.nopolKey != ''
            AND k.id = (
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
        )
    )
""")
    suspend fun deleteOldDuplicatesByNopolKey()
    @Transaction
    suspend fun importData(newData: List<Kendaraan>, updateData: List<Kendaraan>) {
        if (newData.isNotEmpty()) insertAll(newData)
        if (updateData.isNotEmpty()) updateAll(updateData)
    }
}