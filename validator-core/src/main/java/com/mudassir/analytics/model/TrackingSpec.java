package com.mudassir.analytics.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The tracking plan: what should be sent, by whom, and under which rules. */
public record TrackingSpec(String name, String namingConvention, long duplicateWindowMs,
                           List<Collector> collectors, PiiPolicy pii, Map<String, EventSpec> events) {

    public Optional<Collector> collectorFor(String url) {
        return collectors.stream().filter(c -> c.matches(url)).findFirst();
    }
}
