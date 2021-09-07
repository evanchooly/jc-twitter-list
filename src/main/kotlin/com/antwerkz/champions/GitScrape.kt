package com.antwerkz.champions

import org.eclipse.jgit.api.Git
import java.io.File

class GitScrape {
    private val jcGitRepo = File("target/jcs")

    fun loadJCs(): Map<String, String> {
        return extract {
            it.claim(3)
            val handle = it.removeAt(0).extract().substringAfter("@")
            val pair = handle to it.claim(2)
                .map { loc -> loc.substring(1).trim() }
                .filter(String::isNotBlank)
                .joinToString()
            it.claim(2)

            pair
        }
    }

    private fun extract(extractor: (MutableList<String>) -> Pair<String, String>): Map<String, String> {
        if (!jcGitRepo.exists()) {
            Git.cloneRepository()
                .setURI("https://github.com/aalmiray/java-champions")
                .setDirectory(jcGitRepo)
                .call()
                .close()
        } else {
            val open = Git.open(jcGitRepo)
            val pullResult = open
                .pull()
                .call()
            open.close()
        }

        val jcs = mutableMapOf<String, String>()
        var doc = File(jcGitRepo, "README.adoc").readLines().toMutableList()
        while (doc.isNotEmpty()) {
            doc = doc.dropWhile { it != "|{counter:idx}" }.toMutableList()
            jcs += extractor(doc)
        }


        return jcs
            .filter { it.key != "" }
            .toSortedMap()
    }

    private fun String.extract(): String {
        return substring(1).substringAfter("[").substringBefore("]")
    }
}
