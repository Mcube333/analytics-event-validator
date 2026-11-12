package com.mudassir.analytics.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.mudassir.analytics.capture.PayloadParser;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Collector;
import com.mudassir.analytics.model.TrackingSpec;
import com.mudassir.analytics.model.ValidationReport;
import com.mudassir.analytics.validate.EventValidator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Records analytics calls made by the app while an E2E test runs, so tracking can be validated
 * in the same test that drives the UI.
 *
 * <pre>
 * EventRecorder recorder = EventRecorder.attach(context, spec, true);
 * ... drive the journey ...
 * ValidationReport report = recorder.validate();
 * </pre>
 */
public final class EventRecorder {

    private final TrackingSpec spec;
    private final List<AnalyticsEvent> events = Collections.synchronizedList(new ArrayList<>());

    private EventRecorder(TrackingSpec spec) {
        this.spec = spec;
    }

    /**
     * @param stubCollectors true = answer analytics calls locally with 204 so test traffic never reaches
     *                       the real analytics project (keeps production dashboards clean).
     */
    public static EventRecorder attach(BrowserContext context, TrackingSpec spec, boolean stubCollectors) {
        EventRecorder recorder = new EventRecorder(spec);
        context.onRequest(recorder::onRequest);
        if (stubCollectors) {
            Consumer<Route> stub = route -> route.fulfill(new Route.FulfillOptions()
                    .setStatus(204)
                    .setHeaders(Map.of("Access-Control-Allow-Origin", "*")));
            context.route(url -> spec.collectorFor(url).isPresent(), stub);
        }
        return recorder;
    }

    private void onRequest(Request request) {
        Optional<Collector> collector = spec.collectorFor(request.url());
        if (collector.isEmpty()) {
            return;
        }
        String body = request.postData();
        events.addAll(PayloadParser.parse(collector.get(), request.url(), body, System.currentTimeMillis()));
    }

    public List<AnalyticsEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public void clear() {
        events.clear();
    }

    /** Waits until at least {@code count} events have been captured (analytics calls are often async / batched). */
    public EventRecorder awaitAtLeast(int count, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (events.size() < count) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Expected at least " + count + " analytics events within " + timeout
                        + " but captured " + events.size() + ": " + events().stream().map(AnalyticsEvent::name).toList());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return this;
    }

    public ValidationReport validate() {
        return new EventValidator().validate(events(), spec);
    }
}
