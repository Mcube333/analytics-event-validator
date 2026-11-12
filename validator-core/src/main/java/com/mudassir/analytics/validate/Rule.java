package com.mudassir.analytics.validate;

import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.TrackingSpec;

import java.util.List;

/** One independent check over the captured events. */
public interface Rule {
    List<Finding> check(List<AnalyticsEvent> events, TrackingSpec spec);
}
