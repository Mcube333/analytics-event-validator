package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.AttributeSpec;
import com.mudassir.analytics.model.EventSpec;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MISSING_ATTRIBUTE, TYPE_MISMATCH, VALUE_NOT_ALLOWED and UNEXPECTED_ATTRIBUTE for every spec'd event. */
public class AttributeRule implements Rule {

    @Override
    public List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec) {
        List<Finding> findings = new ArrayList<>();
        for (AnalyticsEvent event : events) {
            EventSpec eventSpec = spec.events().get(event.name());
            if (eventSpec == null) {
                continue; // reported by EventCoverageRule
            }
            Map<String, Object> actual = event.attributes();

            for (Map.Entry<String, AttributeSpec> entry : eventSpec.attributes().entrySet()) {
                String key = entry.getKey();
                AttributeSpec attr = entry.getValue();
                Object value = actual.get(key);

                if (value == null || (value instanceof String s && s.isBlank())) {
                    if (attr.required()) {
                        findings.add(Finding.of(FindingType.MISSING_ATTRIBUTE, event.name(), key,
                                "Required attribute is " + (actual.containsKey(key) ? "empty" : "missing") + ".",
                                event.timestampMs()));
                    }
                    continue;
                }
                if (!typeMatches(attr.type(), value)) {
                    findings.add(Finding.of(FindingType.TYPE_MISMATCH, event.name(), key,
                            "Expected " + attr.type() + " but got " + describe(value) + ".", event.timestampMs()));
                    continue;
                }
                if (!attr.allowed().isEmpty() && !attr.allowed().contains(String.valueOf(value))) {
                    findings.add(Finding.of(FindingType.VALUE_NOT_ALLOWED, event.name(), key,
                            "Value '" + value + "' is not one of " + attr.allowed() + ".", event.timestampMs()));
                }
            }

            if (!eventSpec.allowExtraAttributes()) {
                for (String key : actual.keySet()) {
                    if (!eventSpec.attributes().containsKey(key)) {
                        findings.add(Finding.of(FindingType.UNEXPECTED_ATTRIBUTE, event.name(), key,
                                "Attribute is not in the spec for this event.", event.timestampMs()));
                    }
                }
            }
        }
        return findings;
    }

    static boolean typeMatches(String type, Object value) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Long || value instanceof Integer
                    || (value instanceof Double d && d == Math.rint(d) && !d.isInfinite());
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
    }

    private static String describe(Object value) {
        String kind = value instanceof String ? "string" : value instanceof Number ? "number"
                : value instanceof Boolean ? "boolean" : value instanceof Map ? "object"
                : value instanceof List ? "array" : value.getClass().getSimpleName();
        return kind + " (" + value + ")";
    }
}
