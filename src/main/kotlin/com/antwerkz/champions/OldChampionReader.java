package com.antwerkz.champions;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

class OldChampionReader implements AutoCloseable {
    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final BufferedReader reader;
    private final String start;
    private final String end;
    private final HttpClient client;

    public OldChampionReader(final BufferedReader reader,
                             final String start, final String end) {
        this.reader = reader;
        this.start = start;
        this.end = end;
        this.client = HttpClient.newBuilder()
                .executor(ForkJoinPool.commonPool())
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private Map<String, Coordinates> findLocations(final List<String[]> lines) throws InterruptedException, ExecutionException {
        final var locationLookups = lines.stream()
                .map(it -> it[1])
                .filter(it -> !it.isBlank())
                .distinct()
                .collect(toMap(identity(), this::findLocation));
        allOf(locationLookups.values().toArray(new CompletableFuture[0])).get();
        return locationLookups.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, it -> it.getValue().getNow(null)));
    }

    private CompletableFuture<Coordinates> findLocation(final String name) {
        // first check it is not in the column already: "city, country (lat, lon)", use dots, not commas for decimals.
        final int coordinateStart = name.indexOf('(');
        if (coordinateStart > 0) {
            final int coordinateEnd = name.indexOf(')', coordinateStart + 1);
            if (coordinateEnd < 0) {
                throw new IllegalArgumentException("Missing ')' in '" + name + "' (coordinates)");
            }
            final int sep = name.indexOf(',', coordinateStart);
            if (sep < 0) {
                throw new IllegalArgumentException("Missing ',' in '" + name + "' (coordinates)");
            }
            final var citySep = name.indexOf(',');
            if (citySep < 0) {
                throw new IllegalArgumentException("Missing city in '" + name + "'");
            }
            final var endCity = name.lastIndexOf(' ', coordinateStart);
            if (endCity < 0) {
                throw new IllegalArgumentException("Missing city in '" + name + "'");
            }
            return completedFuture(new Coordinates(
                    Double.parseDouble(name.substring(1, sep).strip()),
                    Double.parseDouble(name.substring(sep + 1, coordinateEnd).strip()),
                    name.substring(citySep, endCity).strip()));
        }
        if ("online".equalsIgnoreCase(name)) { // TODO: refine
            return completedFuture(new Coordinates(0., 0., "Online"));
        }

        // try to lookup the location if not present
        // Note: "https://nominatim.openstreetmap.org" was down when writing the script
        final var uri = URI.create("https://nominatim.terrestris.de" +
                "/search.php?" +
                "q=" + URLEncoder.encode(name, UTF_8) + "&" +
                "polygon_geojson=1&" +
                "format=jsonv2&" +
                "limit=1");
        logger.info(() -> "Calling '" + uri + "'");
        return client.sendAsync(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(uri)
                                .timeout(Duration.ofMinutes(5))
                                .header("accept-language", "en-EN,en")
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalArgumentException("Invalid response: " + response);
                    }
                    final var body = response.body();
                    if (body.contains("\"error\":{")) {
                        throw new IllegalArgumentException("Invalid response: " + body + " for '" + uri + "'");
                    }
                    return body;
                })
                .thenApply(json -> {
                    final int latStart = json.indexOf("\"lat\":\"");
                    if (latStart < 0) {
                        throw new IllegalArgumentException("Invalid lat start:" + latStart + "(" + json + ")");
                    }
                    final int latEnd = json.indexOf("\"", latStart + "\"lat\":\"".length() + 1);
                    if (latEnd < 0) {
                        throw new IllegalArgumentException("Invalid lat end:" + latEnd + "(" + json + ")");
                    }
                    final int lonStart = json.indexOf("\"lon\":\"");
                    if (lonStart < 0) {
                        throw new IllegalArgumentException("Invalid lon start:" + lonStart + "(" + json + ")");
                    }
                    final int lonEnd = json.indexOf("\"", lonStart + "\"lon\":\"".length() + 1);
                    if (lonEnd < 0) {
                        throw new IllegalArgumentException("Invalid lon end:" + lonEnd + "(" + json + ")");
                    }
                    final int startName = json.indexOf("\"display_name\":\"");
                    if (startName < 0) {
                        throw new IllegalArgumentException("No display name for '" + name + "'");
                    }
                    final int endName = json.indexOf("\"", startName + "\"display_name\":\"".length() + 1);
                    if (endName < 0) {
                        throw new IllegalArgumentException("No display name for '" + name + "'");
                    }
                    final var displayNameSegments = json.substring(startName + "\"display_name\":\"".length(), endName).split(",");
                    return new Coordinates(
                            Double.parseDouble(json.substring(latStart + "\"lat\":\"".length(), latEnd)),
                            Double.parseDouble(json.substring(lonStart + "\"lon\":\"".length(), lonEnd)),
                            displayNameSegments[displayNameSegments.length - 1].strip());
                });
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
