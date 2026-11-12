package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** PII_LEAK: personal data inside event attributes, including nested objects and arrays. Values are masked in the report. */
public class PiiRule implements Rule {

    @Override
    public List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec) {
        List<Finding> findings = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (AnalyticsEvent event : events) {
            scan(event, "", event.attributes(), spec, findings, reported);
        }
        return findings;
    }

    private void scan(AnalyticsEvent event, String path, Object node, TrackingSpec spec,
                      List<Finding> findings, Set<String> reported) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = path.isEmpty() ? String.valueOf(e.getKey()) : path + "." + e.getKey();
                scan(event, key, e.getValue(), spec, findings, reported);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                scan(event, path + "[" + i + "]", list.get(i), spec, findings, reported);
            }
        } else if (!path.isEmpty()) {
            Optional<String> kind = PiiDetector.detect(path.replaceAll("\\[\\d+]", ""), node, spec.pii());
            if (kind.isPresent() && reported.add(event.name() + "|" + path)) {
                findings.add(Finding.of(FindingType.PII_LEAK, event.name(), path,
                        "Contains " + kind.get() + " (value " + PiiDetector.mask(node) + ").", event.timestampMs()));
            }
        }
    }
}
