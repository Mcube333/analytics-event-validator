package com.mudassir.analytics.capture;

import com.mudassir.analytics.json.Json;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Collector;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns one captured analytics request (URL + body) into events. Shared by the HAR reader and live capture. */
public final class PayloadParser {

    private PayloadParser() {
    }

    public static List<AnalyticsEvent> parse(Collector collector, String url, String body, long timestampMs) {
        return switch (collector.format()) {
            case "ga4" -> parseGa4(collector, url, body, timestampMs);
            case "json" -> parseJson(collector, url, body, timestampMs);
            default -> throw new IllegalArgumentException("Unsupported collector format: " + collector.format());
        };
    }

    // ---------------------------------------------------------------- generic JSON (MoEngage-style batches, Segment, custom)

    private static List<AnalyticsEvent> parseJson(Collector c, String url, String body, long ts) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (IllegalArgumentException e) {
            return List.of(); // not JSON: not an analytics payload for this collector
        }

        List<Object> items = new ArrayList<>();
        if (root instanceof List<?> list) {
            items.addAll(list);
        } else if (!c.batchField().isEmpty() && Json.asMap(root).get(c.batchField()) instanceof List<?> batch) {
            items.addAll(batch);
        } else {
            items.add(root);
        }

        List<AnalyticsEvent> events = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> node = Json.asMap(item);
            Object name = node.get(c.eventField());
            if (name == null) {
                continue;
            }
            Map<String, Object> attributes;
            if (!c.attributesField().isEmpty()) {
                attributes = new LinkedHashMap<>(Json.asMap(node.get(c.attributesField())));
            } else {
                attributes = new LinkedHashMap<>(node);
                attributes.remove(c.eventField());
            }
            events.add(new AnalyticsEvent(String.valueOf(name), attributes, c.name(), ts, url));
        }
        return events;
    }

    // ---------------------------------------------------------------- GA4 collect hits

    private static List<AnalyticsEvent> parseGa4(Collector c, String url, String body, long ts) {
        Map<String, String> shared = queryParams(URI.create(url).getRawQuery());
        List<Map<String, String>> hits = new ArrayList<>();
        if (body == null || body.isBlank()) {
            hits.add(shared);
        } else {
            for (String line : body.split("\\r?\\n")) { // batched hits: one query string per line
                if (line.isBlank()) {
                    continue;
                }
                Map<String, String> merged = new LinkedHashMap<>(shared);
                merged.putAll(queryParams(line));
                hits.add(merged);
            }
        }

        List<AnalyticsEvent> events = new ArrayList<>();
        for (Map<String, String> hit : hits) {
            String name = hit.get("en");
            if (name == null) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (Map.Entry<String, String> p : hit.entrySet()) {
                String key = p.getKey();
                if (key.startsWith("ep.")) {
                    attributes.put(key.substring(3), p.getValue());
                } else if (key.startsWith("epn.")) {
                    attributes.put(key.substring(4), toNumber(p.getValue()));
                } else if (key.startsWith("up.")) {
                    attributes.put("user." + key.substring(3), p.getValue());
                } else if (key.startsWith("upn.")) {
                    attributes.put("user." + key.substring(4), toNumber(p.getValue()));
                }
            }
            events.add(new AnalyticsEvent(name, attributes, c.name(), ts, url));
        }
        return events;
    }

    private static Object toNumber(String value) {
        try {
            return value.contains(".") ? Double.parseDouble(value) : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value; // will surface as a TYPE_MISMATCH
        }
    }

    static Map<String, String> queryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
