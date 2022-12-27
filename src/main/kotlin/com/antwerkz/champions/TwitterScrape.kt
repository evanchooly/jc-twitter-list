package com.antwerkz.champions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.v1.PagableResponseList
import twitter4j.v1.User
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.Properties

class TwitterScrape(properties: Properties) {
    companion object {
        val LOG = LoggerFactory.getLogger(TwitterScrape::class.java)
    }
    private val twitter: Twitter
    private val listMembers = File("java-champions.list")

    init {
        val cb = Twitter.newBuilder()
            .prettyDebugEnabled(true)
            .oAuthConsumer(properties.getProperty("api_key"),properties.getProperty("api_key_secret"))
            .oAuthAccessToken(properties.getProperty("access_token"), properties.getProperty("access_token_secret"))
        twitter =  cb.build()
    }

/*
    fun jcFollows(): List<String> {
        return loadList(File("twitter.list")) { cursor: Long ->
            twitter.getFriendsList("Java_Champions", cursor)
        }.sorted()
    }
*/

    private fun loadList(cacheFile: File, loader: (Long) -> PagableResponseList<User>): Map<String, String> {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val between = Duration.between(Instant.ofEpochMilli(cacheFile.lastModified()), Instant.now())
        val list = sortedMapOf<String, String>()
        if (!cacheFile.exists() || between.toMinutes() >= 15) {
            var cursor: Long = -1
            while (cursor != 0L) {
                val followers = loader(cursor)
                for (follower in followers) {
                    list += follower.name to follower.screenName
                }
                cursor = followers.nextCursor
            }

            mapper.writer().writeValues(cacheFile).write(list)
        } else {
            list.putAll(
                mapper.readerFor(Map::class.java)
                    .readValues<Map<String, String>>(cacheFile.toURI().toURL())
                    .nextValue()
            )
        }

        return list
    }

    fun updateList(jcs: Map<String, String>): List<String> {
        val list = loadJCList()

        val newFollows =
            jcs.values.map { it.lowercase(Locale.getDefault()) }
            .subtract(list.map { it.value.lowercase(Locale.getDefault()) }.toSet())
            .chunked(100)

        if (newFollows.isNotEmpty()) {
            listMembers.delete()
        }

        newFollows.forEach {
            println("adding members to list:  ${it}")
            it.forEach {
                try {
                    twitter.v1().list().createUserListMembers("evanchooly", "java-champions", it)
                } catch(e: TwitterException) {
                    LOG.error("Error adding user '$it'")
                }
            }
        }

        loadJCList()
        return newFollows.flatten()
    }

    fun loadJCList(): Map<String, String> {
        return loadList(listMembers) { cursor: Long ->
            twitter.v1().list().getUserListMembers("evanchooly", "java-champions", 500, cursor)
        }
    }
}
