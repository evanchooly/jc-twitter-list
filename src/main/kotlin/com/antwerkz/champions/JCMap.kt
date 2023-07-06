package com.antwerkz.champions

import org.eclipse.jgit.api.Git
import java.io.File

object JCMap {
    private lateinit var lines: List<String>
    private var lineNumber = 0
    private val jcGitRepo = File("target/jcs")
    val champions: List<Champion> by lazy {
        loadJCs()
    }

    private fun loadJCs(): List<Champion> {
        fetchRepo()
        val file = File(GitScrape.jcGitRepo, "java-champions.yml")
        lines = file.readLines()
            .filterNot {
                it.trim().startsWith("#")
                        || it.trim().startsWith("members:")
                        || it.trim().isEmpty()
            }
            .toMutableList()
        return read()
    }

    private fun read(): List<Champion> {
        var list = mutableListOf<Champion>()
        try {
            while ( peek("") ) {
                list += readChampion()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse yaml on line ${lineNumber - 1}: ${e.message}", e)
        }
        return list
    }

    private fun nextLine(): String {
        return lines[lineNumber++]
    }
    private fun peek(): String? {
        return if (lineNumber < lines.size) lines[lineNumber] else null
    }

    private fun peek(prefix: String) = peek()?.startsWith(prefix) == true

    private fun readChampion(): Champion {
        if (!peek("  - ")) throw IllegalStateException("Not at a champion definition: lineNumber=$lineNumber, line=${peek()}")
        var line = nextLine()
        var champion = Champion()
        champion.name = line.substringAfter(": ")
        while(peek("    ")) {
            var (key, value) = keyValue()
            when (key) {
                "year" -> champion.year = value.toInt()
                "country" -> champion.country = readCountry()
                "city" -> champion.city = value
                "social" -> champion.social = readSocial()
                "avatar" -> champion.avatar = value
                "status" -> champion.status = readStatus()
                else -> throw IllegalStateException("unknown champion key: $key")
            }
        }

        return champion
    }

    private fun readStatus(): List<Status> {
        val statuses = mutableListOf<Status>()
        while(peek("      - ")) {
            statuses += Status.valueOf(
                nextLine()
                    .substringAfter("- ")
                    .replace("-", "_")
                    .lowercase()
            )
        }

        return statuses
    }

    private fun readSocial(): List<Social> {
        var social = mutableListOf<Social>()
        while(peek("      ")) {
            val (key, value) = keyValue()
            val valueOf = Site.valueOf(key.lowercase())
            social += Social(valueOf, value)
        }

        return social
    }

    private fun readCountry(): Country {
        val country = Country()
        while (peek("      ")) {
            val (key, value) = keyValue()
            when (key) {
                "nomination" -> country.nomination = value
                "residence" -> country.residence = value
                "birth" -> country.birth = value
                "citizenship" -> country.citizenship = value
                else -> throw IllegalStateException("unknown country key: $key")
            }
        }

        return country
    }

    private fun keyValue(): Pair<String, String> {
        val trim = nextLine().trim()
        val key = trim.substringBefore(":")
        val value = trim.substringAfter(":").trim()

        return  key to value
    }


    private fun fetchRepo() {
        if (!jcGitRepo.exists()) {
            Git.cloneRepository()
                .setURI("https://github.com/aalmiray/java-champions")
                .setDirectory(jcGitRepo)
                .call()
                .close()
        } else {
            val repo = Git.open(jcGitRepo)
            repo.pull().call()
            repo.close()
        }
    }

}


