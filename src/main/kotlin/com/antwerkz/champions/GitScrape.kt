package com.antwerkz.champions

import org.eclipse.jgit.api.Git
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileReader

class GitScrape {
    private val jcGitRepo = File("target/jcs")

    fun loadJCs(): Map<String, String> {
        fetchRepo()
        val list: Map<*, *> = Yaml().load(FileReader(File(jcGitRepo, "java-champions.yml")))
        val members: List<Map<String, Map<*, *>>> = list["members"] as List<Map<String, Map<*, *>>>
        return members
            .map { (it["name"] as String) to (it["social"]?.get("twitter") as String?) }
            .filter { it.second != null }
            .associate { it.first to it.second!!.substringAfterLast("/") }
            .toSortedMap()
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
            val pullResult = repo
                .pull()
                .call()
            repo.close()
        }
    }

    private fun String.extract(): String {
        return substring(1).substringAfter("[").substringBefore("]")
    }
}
