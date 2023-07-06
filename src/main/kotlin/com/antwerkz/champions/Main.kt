package com.antwerkz.champions

import java.io.FileInputStream
import java.util.Properties

fun main() {
    Main().report()

}

class Main {
    private val properties = Properties()
    private val twitter: TwitterScrape
    private val git = GitScrape

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
        val erroneous = twitterMembers.values.filterNot { it in gitMembers.values }
        println("Accounts on twitter but not in git:")
        println("-----------------------------------")

        println(erroneous.joinToString("\n"))
    }
}

fun MutableList<String>.claim(count: Int): List<String> {
    val items = mutableListOf<String>()
    repeat(count) { items += this.removeAt(0) }

    return items
}
