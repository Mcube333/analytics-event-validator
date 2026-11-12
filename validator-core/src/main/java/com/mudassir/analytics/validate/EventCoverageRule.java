package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.EventSpec;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MISSING_EVENT: an event the journey must fire never arrived.
 * UNKNOWN_EVENT: an event arrived that is not in the spec, with the most likely intended spec name
 *                ("Product Viewed" -> did you mean "product_viewed"?). This is the classic
 *                "event shipped under a different name than spec'd" bug.
 */
public class EventCoverageRule implements Rule {

    @Override
    public List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec) {
        List<Finding> findings = new ArrayList<>();
        Set<String> fired = events.stream().map(AnalyticsEvent::name).collect(Collectors.toSet());

        Map<String, AnalyticsEvent> firstUnknown = new LinkedHashMap<>();
        for (AnalyticsEvent e : events) {
            if (!spec.events().containsKey(e.name())) {
                firstUnknown.putIfAbsent(e.name(), e);
            }
        }
        for (AnalyticsEvent e : firstUnknown.values()) {
            String suggestion = closestSpecName(e.name(), spec.events().keySet());
            String hint = suggestion == null ? "" : " Did you mean '" + suggestion + "'?";
            long times = events.stream().filter(x -> x.name().equals(e.name())).count();
            findings.add(Finding.of(FindingType.UNKNOWN_EVENT, e.name(), null,
                    "Event is not in the tracking spec (fired " + times + "x)." + hint, e.timestampMs()));
        }

        for (EventSpec expected : spec.events().values()) {
            if (expected.expected() && !fired.contains(expected.name())) {
                String alias = firstUnknown.keySet().stream()
                        .filter(u -> expected.name().equals(closestSpecName(u, spec.events().keySet())))
                        .findFirst().orElse(null);
                String hint = alias == null ? "" : " A similar event '" + alias + "' was fired instead.";
                findings.add(Finding.of(FindingType.MISSING_EVENT, expected.name(), null,
                        "Expected event was never fired." + hint, 0L));
            }
        }
        return findings;
    }

    static String closestSpecName(String actual, Set<String> specNames) {
        String normalized = normalize(actual);
        for (String name : specNames) {
            if (normalize(name).equals(normalized)) {
                return name; // same words, different casing / separators
            }
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String name : specNames) {
            int d = levenshtein(normalized, normalize(name));
            if (d < bestDistance) {
                bestDistance = d;
                best = name;
            }
        }
        return bestDistance <= Math.max(2, normalized.length() / 4) ? best : null;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
