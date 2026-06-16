package com.notebaseapk.util

import com.notebaseapk.data.Kendaraan
import java.util.Locale

object NopolFormatter {

    data class NopolPart(
        val wilayah: String,
        val angkaText: String,
        val angkaSort: Int,
        val seri: String,
        val fallback: String
    )

    fun display(raw: String): String {
        val part = parse(raw)

        return if (part != null) {
            if (part.seri.isNotBlank()) {
                "${part.wilayah} ${part.angkaText} ${part.seri}"
            } else {
                "${part.wilayah} ${part.angkaText}"
            }
        } else {
            raw.trim()
                .uppercase(Locale.getDefault())
                .replace("\\s+".toRegex(), " ")
        }
    }

    fun sortList(list: List<Kendaraan>): List<Kendaraan> {
        return list
            .map { kendaraan ->
                val part = parse(kendaraan.nopol)
                    ?: NopolPart(
                        wilayah = normalize(kendaraan.nopol),
                        angkaText = "",
                        angkaSort = Int.MAX_VALUE,
                        seri = "",
                        fallback = normalize(kendaraan.nopol)
                    )

                kendaraan to part
            }
            .sortedWith(
                compareBy<Pair<Kendaraan, NopolPart>> { it.second.wilayah }
                    .thenBy { it.second.angkaSort }
                    .thenBy { it.second.angkaText }
                    .thenBy { it.second.seri }
                    .thenBy { it.second.fallback }
            )
            .map { it.first }
    }

    private fun parse(raw: String): NopolPart? {
        val clean = normalize(raw)

        val match = Regex("^([A-Z]{1,2})(\\d{1,4})([A-Z]{0,4})$")
            .matchEntire(clean)
            ?: return null

        val angkaText = match.groupValues[2]

        return NopolPart(
            wilayah = match.groupValues[1],
            angkaText = angkaText,
            angkaSort = angkaText.toIntOrNull() ?: Int.MAX_VALUE,
            seri = match.groupValues[3],
            fallback = clean
        )
    }

    private fun normalize(raw: String): String {
        return raw.uppercase(Locale.getDefault())
            .replace("[^A-Z0-9]".toRegex(), "")
            .trim()
    }
}