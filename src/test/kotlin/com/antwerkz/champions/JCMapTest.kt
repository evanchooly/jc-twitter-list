package com.antwerkz.champions

import org.testng.Assert.*
import org.testng.annotations.Test
import java.nio.file.Path

class JCMapTest {
    @Test
    fun buildMap() {
        assertEquals(JCMap.champions.first { c -> c.name.equals("Dion Almaer") }
            .city, "San Francisco, California")
        assertEquals(JCMap.champions.first { c -> c.name.equals("Paul Bakker") }
            .social.first { s -> s.site == Site.mastodon }.url, "https://mastodon.online/@paulbakker")

        GithubPages.generate()
    }
}