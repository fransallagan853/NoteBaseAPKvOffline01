package com.notebaseapk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

@Database(
    entities = [
        Kendaraan::class,
        FavoriteVehicle::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun kendaraanDao(): KendaraanDao
    abstract fun favoriteVehicleDao(): FavoriteVehicleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kendaraan ADD COLUMN nopolKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE kendaraan ADD COLUMN periodeData TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_vehicles ADD COLUMN nopolKey TEXT NOT NULL DEFAULT ''")

                val kendaraanCursor = db.query("SELECT id, nopol FROM kendaraan")
                try {
                    while (kendaraanCursor.moveToNext()) {
                        val id = kendaraanCursor.getInt(0)
                        val nopol = kendaraanCursor.getString(1) ?: ""
                        val nopolKey = generateNopolKeyForMigration(nopol)

                        db.execSQL(
                            "UPDATE kendaraan SET nopolKey = ? WHERE id = ?",
                            arrayOf<Any>(nopolKey, id)
                        )
                    }
                } finally {
                    kendaraanCursor.close()
                }

                val favoriteCursor = db.query("SELECT id, nopol FROM favorite_vehicles")
                try {
                    while (favoriteCursor.moveToNext()) {
                        val id = favoriteCursor.getInt(0)
                        val nopol = favoriteCursor.getString(1) ?: ""
                        val nopolKey = generateNopolKeyForMigration(nopol)

                        db.execSQL(
                            "UPDATE favorite_vehicles SET nopolKey = ? WHERE id = ?",
                            arrayOf<Any>(nopolKey, id)
                        )
                    }
                } finally {
                    favoriteCursor.close()
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_vehicles_nopolKey ON favorite_vehicles(nopolKey)")
            }
        }

        private fun generateNopolKeyForMigration(raw: String): String {
            val clean = raw.uppercase(Locale.getDefault())
                .replace("[^A-Z0-9]".toRegex(), "")
                .trim()

            val match = Regex("^([A-Z]{1,2})(\\d{1,4})([A-Z]{0,4})$")
                .matchEntire(clean)

            return if (match != null) {
                val wilayah = match.groupValues[1]
                val angka = match.groupValues[2].padStart(4, '0')
                val seri = match.groupValues[3]
                "$wilayah$angka$seri"
            } else {
                clean
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notebase_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}