package com.mudassir.analytics.model;

public record Finding(FindingType type, String event, String attribute, String message, long timestampMs) {

    public Severity severity() {
        return type.severity();
    }

    public static Finding of(FindingType type, String event, String attribute, String message, long timestampMs) {
        return new Finding(type, event, attribute, message, timestampMs);
    }
}
