package com.mudassir.analytics.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.Severity;
import com.mudassir.analytics.model.TrackingSpec;
import com.mudassir.analytics.model.ValidationReport;
import com.mudassir.analytics.report.ReportWriter;
import com.mudassir.analytics.spec.SpecLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives a real browser through the demo checkout and validates the tracking it emits. */
class DemoJourneyTrackingTest {

    private static final String SHOP = "https://demo-shop.example.test/";
    private static Playwright playwright;
    private static Browser browser;
    private static TrackingSpec spec;
    private static String shopHtml;

    private BrowserContext context;
    private Page page;
    private EventRecorder recorder;

    @BeforeAll
    static void launch() throws IOException {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        spec = SpecLoader.load(Path.of("../samples/tracking-spec.json"));
        try (InputStream in = Objects.requireNonNull(
                DemoJourneyTrackingTest.class.getResourceAsStream("/demo-shop.html"))) {
            shopHtml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll
    static void close() {
        playwright.close();
    }

    @BeforeEach
    void newContext() {
        context = browser.newContext();
        // Serve the demo page from a fake origin: no web server needed, nothing leaves the machine
        context.route(SHOP + "**", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200).setContentType("text/html").setBody(shopHtml)));
        recorder = EventRecorder.attach(context, spec, true);
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    private void runCheckoutJourney(String query) {
        page.navigate(SHOP + query);
        page.locator("#view").click();
        page.locator("#add").click();
        page.locator("#checkout").click();
        page.locator("#pay").click();
        page.locator("#status").getByText("Order placed").waitFor();
    }

    @Test
    void cleanBuildPassesTrackingAudit() throws IOException {
        runCheckoutJourney("");
        ValidationReport report = recorder.awaitAtLeast(4, Duration.ofSeconds(5)).validate();
        ReportWriter.writeHtml(report, Path.of("target", "tracking-audit-clean.html"));

        assertEquals(4, report.events().size());
        assertTrue(report.findings().isEmpty(), () -> ReportWriter.console(report));
    }

    @Test
    void buggyBuildIsCaught() throws IOException {
        runCheckoutJourney("?buggy=1");
        ValidationReport report = recorder.awaitAtLeast(4, Duration.ofSeconds(5)).validate();
        ReportWriter.writeHtml(report, Path.of("target", "tracking-audit-buggy.html"));
        System.out.println(ReportWriter.console(report));

        assertTrue(report.hasFindingsAtLeast(Severity.ERROR));
        assertEquals(2, report.ofType(FindingType.PII_LEAK).size(), "email + phone in add_to_cart");
        assertFalse(report.ofType(FindingType.UNKNOWN_EVENT).isEmpty(), "'Product Viewed' renamed event");
        assertFalse(report.ofType(FindingType.MISSING_EVENT).isEmpty(), "order_placed never fired");
        assertFalse(report.ofType(FindingType.TYPE_MISMATCH).isEmpty(), "price sent as string");
        assertFalse(report.ofType(FindingType.DUPLICATE_EVENT).isEmpty(), "add_to_cart double fire");
        assertFalse(report.ofType(FindingType.VALUE_NOT_ALLOWED).isEmpty(), "'UPI' vs 'upi'");
    }
}
