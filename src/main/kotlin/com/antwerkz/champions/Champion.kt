package com.antwerkz.champions

import com.antwerkz.champions.GithubPages.client
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class Champion {
    companion object {
        val mapper = ObjectMapper()
    }

    var name: String = ""
    var year: Int = 0
    var country: Country = Country()
    var city: String = ""
    var social: List<Social> = listOf()
    var avatar: String = ""
    val coordinates: Coordinates by lazy {
        loadCoordinates()
    }

    private fun loadCoordinates(): Coordinates {
        var coordinates = Coordinates(0.0, 0.0, "")
        val file = listOf(city, country.location())
            .filter { it.isNotBlank() }
            .joinToString("_")
            .replace(" ", "_")
            .replace(",", "")
            .replace("/", "_")

        var cached = File("etc/${file}.coords")
        if (!cached.exists()) {
            val location = listOf(city, country.location())
                .filter { it.isNotBlank() }
                .joinToString(", ")
            try {
                coordinates = findLocation(location).get()
            } catch (e: Exception) {
                println("**************** Could not find location data for ${location}: ${e.message}")
            }
            if (coordinates.lat == 0.0) {
                println("**************** ${location} => coordinates = ${coordinates}")
            }
            cached.parentFile.mkdirs()
            mapper.writeValue(FileWriter(cached), coordinates)
        } else {
            coordinates = mapper.readValue(FileReader(cached), Coordinates::class.java)
        }

        return coordinates
    }

    private fun findLocation(name: String): CompletableFuture<Coordinates> {
        // try to lookup the location if not present
        // Note: "https://nominatim.openstreetmap.org" was down when writing the script
        val uri = URI.create(
            "https://nominatim.terrestris.de" +
                    "/search.php?" +
                    "q=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&" +
                    "polygon_geojson=1&" +
                    "format=jsonv2&" +
                    "limit=1"
        )
        val send: CompletableFuture<Coordinates> = client.sendAsync(
            HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .timeout(Duration.ofMinutes(5))
                .header("accept-language", "en-EN,en")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
            .thenApply(Function<HttpResponse<String>, String> { response ->
                require(response.statusCode() == 200) { "Invalid response: $response" }
                val body = response.body()
                require(!body.contains("\"error\":{")) { "Invalid response: $body for '$uri'" }
                body
            })
            .thenApply(Function<String, Coordinates> { json: String ->
                try {
                    val location: Map<String, Any> = mapper.readValue(json, List::class.java).first() as Map<String, Any>
                    val lon = (location["lon"] as String).toDouble()
                    val lat = (location["lat"] as String).toDouble()
                    Coordinates(lat, lon, name.trim())
                } catch (e: Exception) {
                    println("**************** json = ${json}")
                    throw e
                }
            })
        return send
    }

    var status: List<Status> = listOf()
    override fun toString(): String {
        return "Champion(name='$name', year=$year, country=$country, city='$city', social=$social, avatar='$avatar', " +
                "status=$status)"
    }

}

/*
- name: Andres Almiray
  year: 2010
  country:
    nomination: Switzerland
    citizenship: Mexico
    birth: Colombia
  city: Basel
  social:
    twitter: https://twitter.com/aalmiray
    mastodon: https://mastodon.social/@aalmiray
    linkedin: https://www.linkedin.com/in/aalmiray
    github: https://github.com/aalmiray
    website: https://andresalmiray.com
  avatar: img/avatars/aalmiray.png
  status:
    - alumni
 */