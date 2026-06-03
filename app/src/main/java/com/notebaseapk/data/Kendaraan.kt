package com.notebaseapk.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kendaraan",
    indices = [Index(value = ["nopol", "leasing"], unique = true)]
)
data class Kendaraan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nopol: String,
    val groupNumber: String,
    val namaKendaraan: String,
    val tahun: String = "",
    val warna: String = "",
    val noRangka: String = "",
    val noMesin: String = "",
    val leasing: String,
    val saldo: String = "",
    val overdue: String = "",
    var catatan: String,
    val searchKey: String = "",
    var editorName: String? = null,
    var editorPhone: String? = null
)
