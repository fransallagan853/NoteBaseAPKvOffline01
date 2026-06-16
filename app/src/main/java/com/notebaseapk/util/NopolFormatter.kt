package com.notebaseapk.util

import java.util.Locale

object NopolFormatter {

    fun display(raw: String): String {
        val upper = raw.trim()
            .uppercase(Locale.getDefault())
            .replace("\\s+".toRegex(), " ")

        if (upper.isBlank()) return "-"

        val clean = upper.replace("[^A-Z0-9]".toRegex(), "")

        val match = Regex("^([A-Z]{1,2})(\\d{1,4})([A-Z]{0,4})$")
            .matchEntire(clean)

        return if (match != null) {
            val kodeWilayah = match.groupValues[1]
            val angka = match.groupValues[2]
            val seri = match.groupValues[3]

            if (seri.isNotBlank()) {
                "$kodeWilayah $angka $seri"
            } else {
                "$kodeWilayah $angka"
            }
        } else {
            upper
        }
    }
}