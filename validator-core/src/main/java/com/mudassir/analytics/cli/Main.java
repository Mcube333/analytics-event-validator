package com.mudassir.analytics.cli;

import com.mudassir.analytics.capture.HarReader;
import com.mudassir.analytics.model.AnalyticsEvent;
import com.mudassir.analytics.model.Severity;
import com.mudassir.analytics.model.TrackingSpec;
import com.mudassir.analytics.model.ValidationReport;
import com.mudassir.analytics.report.ReportWriter;
import com.mudassir.analytics.spec.SpecLoader;
import com.mudassir.analytics.validate.EventValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Usage:
 *   java -jar analytics-event-validator.jar --spec tracking-spec.json --har capture.har [--out reports] [--fail-on ERROR]
 * Exit codes: 0 = passed, 1 = findings at or above --fail-on, 2 = usage / input error.
 */
public final class Main {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String spec = null;
        String har = null;
        String out = "reports";
        Severity failOn = Severity.ERROR;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--spec" -> spec = args[++i];
                    case "--har" -> har = args[++i];
                    case "--out" -> out = args[++i];
                    case "--fail-on" -> failOn = Severity.valueOf(args[++i].toUpperCase(Locale.ROOT));
                    case "-h", "--help" -> {
                        usage();
                        return 0;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            if (spec == null || har == null) {
                usage();
                return 2;
            }

            TrackingSpec trackingSpec = SpecLoader.load(Path.of(spec));
            List<AnalyticsEvent> events = HarReader.read(Path.of(har), trackingSpec);
            ValidationReport report = new EventValidator().validate(events, trackingSpec);

            System.out.println(ReportWriter.console(report));
            Path outDir = Path.of(out);
            ReportWriter.writeJson(report, outDir.resolve("tracking-audit.json"));
            ReportWriter.writeHtml(report, outDir.resolve("tracking-audit.html"));
            System.out.println("Reports: " + outDir.resolve("tracking-audit.html").toAbsolutePath());

            boolean failed = report.hasFindingsAtLeast(failOn);
            System.out.println(failed ? "RESULT: FAILED (findings at or above " + failOn + ")" : "RESULT: PASSED");
            return failed ? 1 : 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static void usage() {
        System.out.println("""
                Analytics event validator
                  --spec <file>      tracking plan JSON (required)
                  --har <file>       HAR capture from DevTools / Charles / Playwright (required)
                  --out <dir>        report folder (default: reports)
                  --fail-on <level>  CRITICAL | ERROR | WARNING (default: ERROR)
                """);
    }
}
