package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Event names and attribute keys must follow one convention (casing drift breaks dashboards and funnels). */
public class NamingConventionRule implements Rule {

    private static final Pattern SNAKE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");
    private static final Pattern CAMEL = Pattern.compile("^[a-z][a-zA-Z0-9]*$");

    @Override
    public List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec) {
        Pattern pattern = "camelCase".equalsIgnoreCase(spec.namingConvention()) ? CAMEL : SNAKE;
        List<Finding> findings = new ArrayList<>();
        Set<String> reported = new HashSet<>();

        for (AnalyticsEvent e : events) {
            if (!pattern.matcher(e.name()).matches() && reported.add("event:" + e.name())) {
                findings.add(Finding.of(FindingType.NAMING_CONVENTION, e.name(), null,
                        "Event name is not " + spec.namingConvention() + ".", e.timestampMs()));
            }
            for (String key : e.attributes().keySet()) {
                String bare = key.startsWith("user.") ? key.substring(5) : key;
                if (!pattern.matcher(bare).matches() && reported.add("attr:" + e.name() + ":" + key)) {
                    findings.add(Finding.of(FindingType.NAMING_CONVENTION, e.name(), key,
                            "Attribute key is not " + spec.namingConvention() + ".", e.timestampMs()));
                }
            }
        }
        return findings;
    }
}
