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

    fun jcFollows(): List<Account> {
        return loadList(File("twitter.list")) { cursor: Long ->
            twitter.getFriendsList("Java_Champions", cursor)
        }
    }

    private fun loadList(cacheFile: File, loader: Function1<Long, PagableResponseList<User>>): List<Account> {
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val between = between(ofEpochMilli(cacheFile.lastModified()), Instant.now())
        val list = mutableListOf<Account>()
        if (!cacheFile.exists() || between.toDays() > 3) {
            var cursor: Long = -1
            while (cursor != 0L) {
                val followers = loader(cursor)
                for (follower in followers) {
                    list += Account(follower.name, follower.screenName)
                }
                cursor = followers.nextCursor
            }

            mapper.writer().writeValues(cacheFile).writeAll(list)
        } else {
            list += mapper.readerFor(Account::class.java)
                .readValues<Account>(cacheFile.toURI().toURL())
                .readAll()
        }

        return list
    }

    fun updateList(jcs: MutableList<Account>) {
        val list = loadList(list_members) { cursor: Long ->
            twitter.getUserListMembers("evanchooly", "java-champions", cursor)
        }
        val intersect = list.intersect(jcs)

        jcs.removeAll(list)
        var newFollows = jcs.map { it.twitter }
            .filter { it != "" }
            .chunked(100)

        if(newFollows.isNotEmpty()) {
            list_members.delete()
        }
        println("newFollows = ${newFollows}")
        newFollows.forEach {
            twitter.createUserListMembers("evanchooly", "java-champions", *it.toTypedArray())
        }
    }
}

data class Account(val name: String, val twitter: String) {}

class GitScrape {
    fun list(): MutableList<Account> {
        val target = File("target/jcs")
        if (!target.exists()) {
            Git.cloneRepository()
                .setURI("https://github.com/aalmiray/java-champions")
                .setDirectory(target)
                .call()
        }

        val jcs = mutableListOf<Account>()
        val doc = File(target, "README.adoc").readLines().toMutableList()
        while(doc.isNotEmpty()) {
            if(doc[0] == "|{counter:idx}") {
                jcs += readAccount(doc)
            } else {
                doc.delete(1)
            }
        }

        return jcs
    }

    private fun readAccount(doc: MutableList<String>): Account {
        doc.delete(2)
        val name = extract(doc.removeAt(0))
        val twitter = extract(doc.removeAt(0)).substringAfter("@")

        return Account(name, twitter)
    }
}

fun extract(entry: String): String {
    return entry.substring(1).substringAfter("[").substringBefore("]")
}

fun MutableList<String>.delete(count: Int) = (1..count).forEach{ this.removeAt(0) }

fun main() {
    val twitter = TwitterScrape()
    val follows = twitter.jcFollows()
        .map { it.twitter to it.name }
        .toMap()

    val jcs = GitScrape().list()
    val notFollowed = jcs.filter { it.twitter != "" }
        .filter { it.twitter !in follows }
        .sortedBy { it.twitter }

    println("not followed = ${notFollowed.map { it.twitter }}")

    twitter.updateList(jcs)
}

