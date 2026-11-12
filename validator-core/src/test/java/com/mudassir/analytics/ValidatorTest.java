package com.mudassir.analytics;

import com.mudassir.analytics.capture.HarReader;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.FindingType;
import com.mudassir.analytics.model.Severity;
import com.mudassir.analytics.model.TrackingSpec;
import com.mudassir.analytics.model.ValidationReport;
import com.mudassir.analytics.spec.SpecLoader;
import com.mudassir.analytics.validate.EventValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest {

    private static TrackingSpec spec;

    @BeforeAll
    static void loadSpec() throws Exception {
        spec = SpecLoader.load(fixture("tracking-spec.json"));
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(ValidatorTest.class.getResource("/fixtures/" + name)).toURI());
    }

    private static ValidationReport validate(String har) throws Exception {
        List<AnalyticsEvent> events = HarReader.read(fixture(har), spec);
        return new EventValidator().validate(events, spec);
    }

    private static boolean has(ValidationReport r, FindingType type, String event, String attribute) {
        return r.findings().stream().anyMatch(f -> f.type() == type && event.equals(f.event())
                && Objects.equals(attribute, f.attribute()));
    }

    @Test
    void cleanJourneyHasNoFindings() throws Exception {
        ValidationReport report = validate("clean-journey.har");
        assertEquals(5, report.events().size());
        assertTrue(report.findings().isEmpty(), () -> "Unexpected findings: " + report.findings());
    }

    @Test
    void hashedUserIdIsNotReportedAsPii() throws Exception {
        assertTrue(validate("clean-journey.har").ofType(FindingType.PII_LEAK).isEmpty());
    }

    @Test
    void piiLeaksAreCriticalAndMasked() throws Exception {
        ValidationReport report = validate("buggy-journey.har");
        assertTrue(has(report, FindingType.PII_LEAK, "add_to_cart", "email"));
        assertTrue(has(report, FindingType.PII_LEAK, "add_to_cart", "phone"));
        assertTrue(has(report, FindingType.PII_LEAK, "page_view", "user.mobile"));
        for (Finding f : report.ofType(FindingType.PII_LEAK)) {
            assertEquals(Severity.CRITICAL, f.severity());
            assertFalse(f.message().contains("priya.sharma@gmail.com"), "report must not repeat raw PII"); // pii-allow
        }
    }

    @Test
    void renamedEventIsLinkedToSpecName() throws Exception {
        ValidationReport report = validate("buggy-journey.har");
        Finding unknown = report.ofType(FindingType.UNKNOWN_EVENT).get(0);
        assertEquals("Product Viewed", unknown.event());
        assertTrue(unknown.message().contains("Did you mean 'product_viewed'"));
        assertTrue(has(report, FindingType.MISSING_EVENT, "product_viewed", null));
        assertTrue(has(report, FindingType.MISSING_EVENT, "order_placed", null));
    }

    @Test
    void attributeProblemsAreReported() throws Exception {
        ValidationReport report = validate("buggy-journey.har");
        assertTrue(has(report, FindingType.TYPE_MISMATCH, "add_to_cart", "price"));
        assertTrue(has(report, FindingType.MISSING_ATTRIBUTE, "checkout_started", "cart_value"));
        assertTrue(has(report, FindingType.VALUE_NOT_ALLOWED, "checkout_started", "payment_mode"));
        assertTrue(has(report, FindingType.NAMING_CONVENTION, "checkout_started", "cartValue"));
        assertTrue(has(report, FindingType.DUPLICATE_EVENT, "add_to_cart", null));
    }

    @Test
    void repeatedProblemsAreReportedOnce() throws Exception {
        long priceMismatches = validate("buggy-journey.har").ofType(FindingType.TYPE_MISMATCH).stream()
                .filter(f -> "price".equals(f.attribute())).count();
        assertEquals(1, priceMismatches);
    }

    @Test
    void buggyJourneyFailsTheGate() throws Exception {
        ValidationReport report = validate("buggy-journey.har");
        assertTrue(report.hasFindingsAtLeast(Severity.ERROR));
        assertFalse(validate("clean-journey.har").hasFindingsAtLeast(Severity.WARNING));
    }

    @Test
    void cardNumbersNeedValidLuhnChecksum() {
        TrackingSpec s = spec;
        AnalyticsEvent validCard = new AnalyticsEvent("page_view",
                Map.of("page_location", "x", "ref", "4111111111111111"), "t", 1, "u"); // pii-allow: public test card
        AnalyticsEvent randomDigits = new AnalyticsEvent("page_view",
                Map.of("page_location", "x", "ref", "4111111111111112"), "t", 1, "u"); // pii-allow: fails Luhn on purpose
        assertEquals(1, new EventValidator().validate(List.of(validCard), s).ofType(FindingType.PII_LEAK).size());
        assertEquals(0, new EventValidator().validate(List.of(randomDigits), s).ofType(FindingType.PII_LEAK).size());
    }

    @Test
    void reportsAreWrittenWithoutRawPii(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        ValidationReport report = validate("buggy-journey.har");
        com.mudassir.analytics.report.ReportWriter.writeJson(report, dir.resolve("r.json"));
        com.mudassir.analytics.report.ReportWriter.writeHtml(report, dir.resolve("r.html"));
        for (String file : List.of("r.json", "r.html")) {
            String content = readString(dir.resolve(file));
            assertFalse(content.contains("priya.sharma"), file + " leaks PII"); // pii-allow
            assertFalse(content.contains("9876543210"), file + " leaks PII"); // pii-allow
        }
    }

    private static String readString(Path p) throws IOException {
        return Files.readString(p);
    }
}
