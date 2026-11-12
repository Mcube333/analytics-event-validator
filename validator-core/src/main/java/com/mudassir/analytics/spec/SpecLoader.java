package com.mudassir.analytics.spec;

import com.mudassir.analytics.json.Json;
import com.mudassir.analytics.model.AttributeSpec;
import com.mudassir.analytics.model.Collector;
import com.mudassir.analytics.model.EventSpec;
import com.mudassir.analytics.model.PiiPolicy;
import com.mudassir.analytics.model.TrackingSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads a tracking plan from JSON (see samples/tracking-spec.json). */
public final class SpecLoader {

    private static final Set<String> DEFAULT_FORBIDDEN_KEYS = Set.of(
            "email", "email_id", "phone", "phone_number", "mobile", "mobile_number",
            "name", "full_name", "first_name", "last_name", "address", "dob", "date_of_birth", "pan");

    private SpecLoader() {
    }

    public static TrackingSpec load(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    public static TrackingSpec parse(String json) {
        Map<String, Object> root = Json.asMap(Json.parse(json));

        List<Collector> collectors = Json.asList(root.get("collectors")).stream()
                .map(Json::asMap)
                .map(c -> new Collector(
                        Json.asString(c.get("name"), "collector"),
                        required(c, "urlContains"),
                        Json.asString(c.get("format"), "json").toLowerCase(Locale.ROOT),
                        Json.asString(c.get("batchField"), ""),
                        Json.asString(c.get("eventField"), "event"),
                        Json.asString(c.get("attributesField"), "")))
                .toList();
        if (collectors.isEmpty()) {
            throw new IllegalArgumentException("Spec must define at least one collector");
        }

        Map<String, Object> piiNode = Json.asMap(root.get("pii"));
        Set<String> forbidden = new HashSet<>(DEFAULT_FORBIDDEN_KEYS);
        Json.asList(piiNode.get("forbiddenKeys")).forEach(k -> forbidden.add(lower(k)));
        Set<String> ignore = new HashSet<>();
        Json.asList(piiNode.get("ignoreKeys")).forEach(k -> ignore.add(lower(k)));
        PiiPolicy pii = new PiiPolicy(bool(piiNode.get("allowHashed"), true), forbidden, ignore);

        Map<String, EventSpec> events = new LinkedHashMap<>();
        for (Object e : Json.asList(root.get("events"))) {
            Map<String, Object> ev = Json.asMap(e);
            String name = required(ev, "name");
            Map<String, AttributeSpec> attributes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> a : Json.asMap(ev.get("attributes")).entrySet()) {
                Map<String, Object> attr = Json.asMap(a.getValue());
                List<String> allowed = Json.asList(attr.get("allowed")).stream().map(String::valueOf).toList();
                attributes.put(a.getKey(), new AttributeSpec(
                        Json.asString(attr.get("type"), "any").toLowerCase(Locale.ROOT),
                        bool(attr.get("required"), false),
                        allowed));
            }
            events.put(name, new EventSpec(name, bool(ev.get("expected"), false),
                    bool(ev.get("allowExtraAttributes"), false), attributes));
        }

        return new TrackingSpec(
                Json.asString(root.get("name"), "Tracking spec"),
                Json.asString(root.get("namingConvention"), "snake_case"),
                root.get("duplicateWindowMs") instanceof Number n ? n.longValue() : 1000L,
                collectors, pii, events);
    }

    private static String required(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Spec is missing required field '" + key + "' in " + node);
        }
        return String.valueOf(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static String lower(Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
