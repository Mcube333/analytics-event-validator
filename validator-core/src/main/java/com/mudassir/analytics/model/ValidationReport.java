package com.mudassir.analytics.model;

import java.util.List;

public record ValidationReport(String specName, List<AnalyticsEvent> events, List<Finding> findings) {

    public long count(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    public boolean hasFindingsAtLeast(Severity threshold) {
        return findings.stream().anyMatch(f -> f.severity().isAtLeast(threshold));
    }

    public List<Finding> ofType(FindingType type) {
        return findings.stream().filter(f -> f.type() == type).toList();
    }
}
