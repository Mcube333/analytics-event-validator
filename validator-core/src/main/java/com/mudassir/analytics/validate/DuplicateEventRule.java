package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** DUPLICATE_EVENT: the same event with identical attributes sent twice within the spec's window (double-fire bug). */
public class DuplicateEventRule implements Rule {

    @Override
    public List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec) {
        List<AnalyticsEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(AnalyticsEvent::timestampMs));

        List<Finding> findings = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            AnalyticsEvent current = sorted.get(i);
            for (int j = i - 1; j >= 0; j--) {
                AnalyticsEvent previous = sorted.get(j);
                long gap = current.timestampMs() - previous.timestampMs();
                if (gap > spec.duplicateWindowMs()) {
                    break;
                }
                if (previous.name().equals(current.name())
                        && Objects.equals(previous.attributes(), current.attributes())) {
                    findings.add(Finding.of(FindingType.DUPLICATE_EVENT, current.name(), null,
                            "Fired twice with identical attributes " + gap + " ms apart.", current.timestampMs()));
                    break;
                }
            }
        }
        return findings;
    }
}
