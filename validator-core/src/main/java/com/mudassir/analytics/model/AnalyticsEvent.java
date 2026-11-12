package com.mudassir.analytics.model;

import java.util.Map;

/** One tracked event as it actually left the app. */
public record AnalyticsEvent(String name, Map<String, Object> attributes, String collector,
                             long timestampMs, String sourceUrl) {
}
