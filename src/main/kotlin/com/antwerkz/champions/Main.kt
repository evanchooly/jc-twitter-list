package com.antwerkz.champions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.eclipse.jgit.api.Git
import twitter4j.PagableResponseList
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.User
import twitter4j.conf.ConfigurationBuilder
import java.io.File
import java.io.FileInputStream
import java.time.Duration.between
import java.time.Instant
import java.time.Instant.ofEpochMilli
import java.util.Properties
import java.util.SortedSet


private val list_members = File("java-champions.list")

class TwitterScrape {
    val twitter: Twitter

    init {
        val properties = Properties()
        properties.load(FileInputStream("twitter.properties"))
        val cb = ConfigurationBuilder()
        cb.setDebugEnabled(true)
            .setOAuthConsumerKey(properties.getProperty("api_key"))
            .setOAuthConsumerSecret(properties.getProperty("api_key_secret"))
            .setOAuthAccessToken(properties.getProperty("access_token"))
            .setOAuthAccessTokenSecret(properties.getProperty("access_token_secret"))
        val tf = TwitterFactory(cb.build())
        twitter = tf.instance
    }

    fun jcFollows(): List<String> {
        return loadList(File("twitter.list")) { cursor: Long ->
            twitter.getFriendsList("Java_Champions", cursor)
        }.sorted()
    }

    internal fun loadList(cacheFile: File, loader: Function1<Long, PagableResponseList<User>>): List<String> {
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val between = between(ofEpochMilli(cacheFile.lastModified()), Instant.now())
        val list = mutableListOf<String>()
        if (!cacheFile.exists() || between.toMinutes() >= 15) {
            var cursor: Long = -1
            while (cursor != 0L) {
                val followers = loader(cursor)
                for (follower in followers) {
                    list += follower.screenName
                }
                cursor = followers.nextCursor
            }

            mapper.writer().writeValues(cacheFile).writeAll(list)
        } else {
            list += mapper.readerFor(String::class.java)
                .readValues<String>(cacheFile.toURI().toURL())
                .readAll()
        }

        return list
            .sorted()
    }

    fun updateList(jcs: Set<String>): List<String> {
        val list = loadList(list_members) { cursor: Long ->
            twitter.getUserListMembers("evanchooly", "java-champions", 500, cursor)
        }.toSortedSet()

        val newFollows = jcs.map { it.toLowerCase() }
            .subtract(list.map { it.toLowerCase() })
            .chunked(100)

        if(newFollows.isNotEmpty()) {
            list_members.delete()
        }

        newFollows.forEach {
            twitter.createUserListMembers("evanchooly", "java-champions", *it.toTypedArray())
        }

        return newFollows.flatten()
    }
}

data class Account(val name: String, val twitter: String) {}

class GitScrape {
    fun list(): SortedSet<String> {
        val target = File("target/jcs")
        if (!target.exists()) {
            Git.cloneRepository()
                .setURI("https://github.com/aalmiray/java-champions")
                .setDirectory(target)
                .call()
        } else {
            Git.open(target).pull()
        }

        val jcs = mutableListOf<String>()
        val doc = File(target, "README.adoc").readLines().toMutableList()
        while(doc.isNotEmpty()) {
            if(doc[0] == "|{counter:idx}") {
                jcs += readAccount(doc)
            } else {
                doc.delete(1)
            }
        }


        return jcs
            .filter { it != "" }
            .toSortedSet()
    }

    private fun readAccount(doc: MutableList<String>): String {
        doc.delete(3)

        return doc.removeAt(0).extract().substringAfter("@")
    }
}

fun String.extract(): String {
    return substring(1).substringAfter("[").substringBefore("]")
}

fun MutableList<String>.delete(count: Int) = (1..count).forEach{ this.removeAt(0) }

fun main() {
    val twitter = TwitterScrape()

    val jcs = GitScrape().list()

    val notFollowed =twitter.updateList(jcs)

    println("not followed = ${notFollowed}")
}

