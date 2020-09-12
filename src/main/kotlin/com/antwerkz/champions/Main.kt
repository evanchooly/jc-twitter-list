package com.antwerkz.champions

import java.io.FileInputStream
import java.util.Properties

fun main() {
    Main().report()

}

class Main {
    private val properties = Properties()
    private val twitter: TwitterScrape
    private val git = GitScrape()

    init {
        loadProperties()
        twitter = TwitterScrape(properties)
    }

    fun report() {
        val jcs = git.loadJCs()
        val notFollowed = twitter.updateList(jcs)
        val list = twitter.loadJCList()

        nonJCAccounts()

        println("\nAccounts Not Followed:")
        println("----------------------")
        println(notFollowed.joinToString("\n"))

        println("\nCurrent Membership")
        println("------------------")
        list.forEach {
            println("${it.key}:  ${it.value}")
        }


//    downloadMap(jcs)
    }

    private fun loadProperties() {
        properties.load(FileInputStream("twitter.properties"))
    }

    private fun nonJCAccounts() {
        val twitterMembers = twitter.loadJCList()
        val gitMembers = git.loadJCs()
        val erroneous = twitterMembers.values.filterNot { it in gitMembers.keys }
        println("Accounts on twitter but not in git:")
        println("-----------------------------------")

        println(erroneous.joinToString("\n"))
    }

/*
    fun downloadMap(jcs: Map<String, String>) {
        val client = OkHttpClient()

        val baseUrl =
            "https://maps.googleapis.com/maps/api/staticmap?key=${properties.getProperty("maps-api-key")}&size=1024x1024"
        val locations = jcs.values
            .filter { it.contains(",") }
            .map {
                it.replace(", ", ",")
                    .replace(' ', '+')
            }
            .distinct()
//        .chunked(15)
//        .map { .it
            .joinToString("|")
//            }

//    locations.forEachIndexed { i, location ->
        val request = Builder()
            .method("POST", ("markers=color:red|$locations").toRequestBody("application/text".toMediaType()))
            .url(baseUrl)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body
            val content = body!!.bytes()
            File("target/champions.png").writeBytes(content)
        }
//    }

//        val mapsDir = File(jcGitRepo, "maps")
//        println("mapsDir = ${mapsDir}")

    }
*/

}

fun MutableList<String>.claim(count: Int): List<String> {
    val items = mutableListOf<String>()
    repeat(count) { items += this.removeAt(0) }

    return items
}
