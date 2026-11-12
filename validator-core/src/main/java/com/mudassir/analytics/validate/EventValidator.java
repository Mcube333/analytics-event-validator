package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.TrackingSpec;
import com.mudassir.analytics.model.ValidationReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs every rule and returns one report, most severe findings first. */
public class EventValidator {

    private final List<Rule> rules;

    public EventValidator() {
        this(List.of(new PiiRule(), new EventCoverageRule(), new AttributeRule(),
                new NamingConventionRule(), new DuplicateEventRule()));
    }

    public EventValidator(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public ValidationReport validate(List<AnalyticsEvent> events, TrackingSpec spec) {
        // Same problem on repeated firings of one event is reported once
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (Rule rule : rules) {
            for (Finding f : rule.check(events, spec)) {
                unique.putIfAbsent(f.type() + "|" + f.event() + "|" + f.attribute() + "|" + f.message(), f);
            }
        }
        List<Finding> findings = new ArrayList<>(unique.values());
        findings.sort(Comparator
                .comparing((Finding f) -> f.severity().ordinal())
                .thenComparing(f -> f.type().ordinal())
                .thenComparingLong(Finding::timestampMs));
        return new ValidationReport(spec.name(), List.copyOf(events), List.copyOf(findings));
    }
}
