package com.mudassir.analytics.report;

import com.mudassir.analytics.json.Json;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Finding;
import com.mudassir.analytics.model.Severity;
import com.mudassir.analytics.model.ValidationReport;
import com.mudassir.analytics.validate.PiiDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Console, JSON and self-contained HTML outputs. PII values are always masked. */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static String console(ValidationReport report) {
        StringBuilder b = new StringBuilder();
        b.append("\n=== ").append(report.specName()).append(" ===\n");
        b.append(String.format("Events captured: %d | CRITICAL: %d | ERROR: %d | WARNING: %d%n",
                report.events().size(), report.count(Severity.CRITICAL), report.count(Severity.ERROR),
                report.count(Severity.WARNING)));
        if (report.findings().isEmpty()) {
            b.append("No findings. Tracking matches the spec.\n");
        }
        for (Finding f : report.findings()) {
            b.append(String.format("[%-8s] %-20s %-22s %-18s %s%n", f.severity(), f.type(), f.event(),
                    f.attribute() == null ? "-" : f.attribute(), f.message()));
        }
        return b.toString();
    }

    public static void writeJson(ValidationReport report, Path file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("spec", report.specName());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("events", report.events().size());
        for (Severity s : Severity.values()) {
            summary.put(s.name().toLowerCase(), report.count(s));
        }
        root.put("summary", summary);
        List<Object> findings = new ArrayList<>();
        for (Finding f : report.findings()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("severity", f.severity().name());
            m.put("type", f.type().name());
            m.put("event", f.event());
            m.put("attribute", f.attribute());
            m.put("message", f.message());
            m.put("timestamp", f.timestampMs() == 0 ? null : Instant.ofEpochMilli(f.timestampMs()).toString());
            findings.add(m);
        }
        root.put("findings", findings);
        List<Object> events = new ArrayList<>();
        for (AnalyticsEvent e : report.events()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.name());
            m.put("collector", e.collector());
            m.put("timestamp", e.timestampMs() == 0 ? null : Instant.ofEpochMilli(e.timestampMs()).toString());
            m.put("attributeKeys", new ArrayList<>(e.attributes().keySet())); // keys only: never copy raw values
            events.add(m);
        }
        root.put("events", events);
        writeFile(file, Json.write(root));
    }

    public static void writeHtml(ValidationReport report, Path file) throws IOException {
        StringBuilder rows = new StringBuilder();
        for (Finding f : report.findings()) {
            rows.append("<tr class='").append(f.severity().name().toLowerCase()).append("'>")
                    .append(td(f.severity().name())).append(td(f.type().name())).append(td(f.event()))
                    .append(td(f.attribute() == null ? "-" : f.attribute())).append(td(f.message()))
                    .append("</tr>\n");
        }
        StringBuilder timeline = new StringBuilder();
        for (AnalyticsEvent e : report.events()) {
            String keys = e.attributes().keySet().stream().collect(Collectors.joining(", "));
            timeline.append("<tr>")
                    .append(td(e.timestampMs() == 0 ? "-" : Instant.ofEpochMilli(e.timestampMs()).toString()))
                    .append(td(e.name())).append(td(e.collector())).append(td(keys)).append("</tr>\n");
        }
        String verdict = report.hasFindingsAtLeast(Severity.ERROR) ? "FAILED" : "PASSED";

        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Tracking audit - %s</title>
                <style>
                  :root { --bg:#fff; --fg:#1d1d1f; --muted:#6e6e73; --line:#e5e5ea; --crit:#b42318; --err:#c4320a; --warn:#b54708; --ok:#067647; }
                  @media (prefers-color-scheme: dark) { :root { --bg:#161618; --fg:#f2f2f7; --muted:#a1a1a6; --line:#2c2c2e; } }
                  body { font: 14px/1.5 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background:var(--bg); color:var(--fg); margin:0; padding:24px; }
                  h1 { font-size:20px; margin:0 0 4px; } .muted { color:var(--muted); }
                  .cards { display:flex; gap:12px; flex-wrap:wrap; margin:16px 0 24px; }
                  .card { border:1px solid var(--line); border-radius:10px; padding:12px 16px; min-width:110px; }
                  .card b { display:block; font-size:22px; }
                  .verdict { font-weight:700; color:%s; }
                  .wrap { overflow-x:auto; } table { border-collapse:collapse; width:100%%; margin-bottom:28px; }
                  th, td { text-align:left; padding:8px 10px; border-bottom:1px solid var(--line); vertical-align:top; }
                  th { font-size:12px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); }
                  tr.critical td:first-child { color:var(--crit); font-weight:700; }
                  tr.error td:first-child { color:var(--err); font-weight:700; }
                  tr.warning td:first-child { color:var(--warn); font-weight:600; }
                </style></head><body>
                <h1>Tracking audit: %s</h1>
                <div class="muted">Generated %s &middot; verdict <span class="verdict">%s</span></div>
                <div class="cards">
                  <div class="card">Events<b>%d</b></div>
                  <div class="card">Critical<b>%d</b></div>
                  <div class="card">Errors<b>%d</b></div>
                  <div class="card">Warnings<b>%d</b></div>
                </div>
                <h2>Findings</h2>
                <div class="wrap"><table><thead><tr><th>Severity</th><th>Type</th><th>Event</th><th>Attribute</th><th>Detail</th></tr></thead>
                <tbody>%s</tbody></table></div>
                <h2>Captured events</h2>
                <div class="wrap"><table><thead><tr><th>Time</th><th>Event</th><th>Collector</th><th>Attribute keys</th></tr></thead>
                <tbody>%s</tbody></table></div>
                </body></html>
                """.formatted(
                esc(report.specName()), "FAILED".equals(verdict) ? "var(--crit)" : "var(--ok)",
                esc(report.specName()), Instant.now().toString(), verdict,
                report.events().size(), report.count(Severity.CRITICAL), report.count(Severity.ERROR),
                report.count(Severity.WARNING),
                rows.length() == 0 ? "<tr><td colspan='5'>No findings</td></tr>" : rows,
                timeline);
        writeFile(file, html);
    }

    private static String td(String s) {
        return "<td>" + esc(s) + "</td>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void writeFile(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }

    /** Exposed for callers that print individual values. */
    public static String masked(Object value) {
        return PiiDetector.mask(value);
    }
}
