package com.antwerkz.champions

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.Properties

object Countries {
    private val countryIsoCodes = Properties()

    init {
        try {
            Files.newBufferedReader(Path.of("countries.properties")).use { reader -> countryIsoCodes.load(reader) }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }

    fun find(name: String): String? {
        val property: String? = countryIsoCodes.getProperty(
            name.lowercase(Locale.ROOT)
                .replace(" ", "")
                .replace(",", "")
                .replace("'", "")
                .replace("ç", "c")
                .replace("ô", "o")
                .replace("é", "e")
                .replace("å", "a")
        )

        return property
    }
}
