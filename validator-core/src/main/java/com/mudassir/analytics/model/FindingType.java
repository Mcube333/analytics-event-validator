package com.mudassir.analytics.model;

public enum FindingType {
    PII_LEAK(Severity.CRITICAL),
    MISSING_EVENT(Severity.ERROR),
    MISSING_ATTRIBUTE(Severity.ERROR),
    TYPE_MISMATCH(Severity.ERROR),
    VALUE_NOT_ALLOWED(Severity.ERROR),
    UNKNOWN_EVENT(Severity.WARNING),
    UNEXPECTED_ATTRIBUTE(Severity.WARNING),
    NAMING_CONVENTION(Severity.WARNING),
    DUPLICATE_EVENT(Severity.WARNING);

    private final Severity severity;

    FindingType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }
}
