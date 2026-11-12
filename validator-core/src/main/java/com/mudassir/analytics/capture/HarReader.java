package com.mudassir.analytics.capture;

import com.mudassir.analytics.json.Json;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Collector;
import com.mudassir.analytics.model.TrackingSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts analytics events from a HAR file (Chrome DevTools > Network > Save all as HAR,
 * Charles, Proxyman, or a Playwright recordHar). Only requests matching a spec collector are read.
 */
public final class HarReader {

    private HarReader() {
    }

    public static List<AnalyticsEvent> read(Path harFile, TrackingSpec spec) throws IOException {
        return parse(Files.readString(harFile), spec);
    }

    public static List<AnalyticsEvent> parse(String harJson, TrackingSpec spec) {
        Map<String, Object> log = Json.asMap(Json.asMap(Json.parse(harJson)).get("log"));
        List<AnalyticsEvent> events = new ArrayList<>();
        for (Object e : Json.asList(log.get("entries"))) {
            Map<String, Object> entry = Json.asMap(e);
            Map<String, Object> request = Json.asMap(entry.get("request"));
            String url = Json.asString(request.get("url"), "");
            Optional<Collector> collector = spec.collectorFor(url);
            if (collector.isEmpty()) {
                continue;
            }
            String body = Json.asString(Json.asMap(request.get("postData")).get("text"), "");
            events.addAll(PayloadParser.parse(collector.get(), url, body, timestamp(entry)));
        }
        return events;
    }

    private static long timestamp(Map<String, Object> entry) {
        try {
            return OffsetDateTime.parse(Json.asString(entry.get("startedDateTime"), "")).toInstant().toEpochMilli();
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
