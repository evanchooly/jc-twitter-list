package com.antwerkz.champions

import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import java.util.logging.Logger


object GithubPages {
    val logger = Logger.getLogger(javaClass.simpleName)

    val client = HttpClient.newBuilder()
        .executor(ForkJoinPool.commonPool())
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    fun generate() {
        val champions = JCMap.champions
        logger.info { "Found #${champions.size} champions." }
        val target = Path.of(".")
        Files.writeString(target.resolve("map.html"), map(champions))
        logger.info { "Generation successful." }
    }

    private fun map(champions: List<Champion>): String {
        return """<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta http-equiv="x-ua-compatible" content="ie=edge" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />

    <link href="https://javachampions.org/css/fontawesome-all.min.css" rel="stylesheet">
    <title>Java Champions</title>
${leafletCss()}    <style>
      body { margin: 0; padding: 0; }
      #map { height: 100vh; }
    </style>
  </head>

  <body>
${mapContent(champions)}  </body>
</html>"""
    }

    private fun mapContent(champions: List<Champion>): String {
        return """    <div id="map"></div>    <script src="https://unpkg.com/leaflet@1.8.0/dist/leaflet.js"
      integrity="sha512-BB3hKbKWOc9Ez/TAwyWxNXeoV9c1v6FIeYiBieIWkpLjauysF18NzgR1MBNBXf8/KABdlkX68nAhlwcDFLGPCQ=="
      crossorigin=""></script>
    <script>
      (function () {
          var map = L.map('map', {drawControl: true}).setView([51.505, -0.09], 13);
          L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
              attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a>'
          }).addTo(map);
""" +
                champions
                    .groupBy { it.coordinates.name }
                    .map { (location: String, list: List<Champion>) ->
                        var tooltips = location + list.tooltips()
                        var coordinates = list.first().coordinates
                        "            { " +
                                "tooltip: '$tooltips', " +
                                "marker: L.marker([${coordinates.lat},${coordinates.lon}], {alt:'${location}'}) }"
                    }
                    .joinToString(",\n", "          var markers = [\n", "\n          ];\n") +
                "          markers.forEach(function (marker) {\n" +
                "            marker.marker.on('mouseover', function() {\n" +  // workaround to make it a bit persistent and enable to click on links
                "              marker.marker.bindTooltip(marker.tooltip, {permanent: true, interactive:true});\n" +
                "              if (window.unbindTooltip && window.unbindTooltip.marker != marker) { window.unbindTooltip.unbind(); }\n" +
                "              unbindTooltip = {" +
                "                version: window.unbindTooltip && window.unbindTooltip.marker == marker ? window.unbindTooltip.version + 1 : 1," +
                "                marker: marker, " +
                "                unbind: function () { marker.marker.unbindTooltip(); window.unbindTooltip = undefined; }" +
                "              };\n" +
                "            });\n" +
                "            marker.marker.on('mouseout', function() {\n" +
                "              var version = window.unbindTooltip ? window.unbindTooltip.version : -1;\n" +
                "              window.unbindTooltip && setTimeout(function () {\n" +
                "                window.unbindTooltip && version == window.unbindTooltip.version && window.unbindTooltip.unbind();\n" +
                "                window.unbindTooltip = undefined;\n" +
                "              }, 3000);\n" +
                "            });\n" +
                "          });\n" +
                "          var group = L.featureGroup(markers.map(function (it) { return it.marker; })).addTo(map);\n" +
                "          map.fitBounds(group.getBounds());\n" +
                "          L.Control.textbox = L.Control.extend({\n" +
                "            onAdd: function(map) {\n" +
                "              var title = L.DomUtil.create('div');\n" +
                "              title.id = 'map-title';\n" +
                "              title.innerHTML = '<h1>Java Champions</h1>'\n" +
                "              return title;\n" +
                "            },\n" +
                "            onRemove: function(map) {}\n" +
                "          });\n" +
                "          L.control.textbox = function(opts) { return new L.Control.textbox(opts); }\n" +
                "          L.control.textbox({position: 'topleft'}).addTo(map);" +
                "      })();\n" +
                "    </script>\n"
    }

    private fun List<Champion>.tooltips() =
        joinToString("<hr>", prefix = "<hr>", transform = {
            "${it.name} (${it.year})<br>" +
                    it.social.joinToString(" ") { social ->
                        """<a href="${social.url}"
                            onmousedown="if (window.unbindTooltip) {
                              window.unbindTooltip.unbind(); 
                              window.unbindTooltip = undefined; 
                            }
                            setTimeout(function () {
                              window.open(\'${social.url}\', \'_blank\').focus();
                            }, 100)">${social.site.icon()}</a>""".trimMargin()
                    }.replace("\n", "")
        })


    private fun leafletCss() =
        """    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.8.0/dist/leaflet.css"
      integrity="sha512-hoalWLoI8r4UszCkZ5kL8vayOGVae1oxXe/2A4AO6J9+580uKHDO3JdHb7NzwwzK5xr/Fs0W40kiNHxM9vyTtQ=="
      crossorigin="anonymous" referrerpolicy="no-referrer"/>
"""

}

private fun Social.linkText(): String {
    return ""
}
