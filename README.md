# Analytics Event Validator

A Java tool that checks analytics tracking against a **tracking plan** and catches the bugs that break
dashboards, funnels and privacy compliance: **PII leaking into events, renamed or missing events,
wrong attribute types and values, naming drift and double-fired events**.

It grew out of real tracking audits I ran on an e-commerce checkout revamp (MoEngage), where the same
issues kept coming back: events shipped under different names than the spec, customer name / phone / email
inside event attributes, casing inconsistencies, and test traffic landing in the wrong workspace.
This is a clean-room, vendor-neutral version of that audit, built to run automatically.

## Two ways to use it

**1. Offline, on a HAR capture (any app, any platform)**

Record the journey in Chrome DevTools (Network > Save all as HAR), Charles, Proxyman or a Playwright
`recordHar`, then:

```bash
java -jar analytics-event-validator.jar --spec tracking-spec.json --har capture.har --out reports
```

Exit code `1` when findings reach `--fail-on` (default `ERROR`), so it works as a CI gate.

**2. Live, inside Playwright E2E tests**

```java
EventRecorder recorder = EventRecorder.attach(context, spec, true); // true = stub collectors locally
// ... drive the UI journey ...
ValidationReport report = recorder.awaitAtLeast(4, Duration.ofSeconds(5)).validate();
assertFalse(report.hasFindingsAtLeast(Severity.ERROR), ReportWriter.console(report));
```

Stubbing the collectors means test runs never pollute the real analytics project.

## What it catches

| Finding | Severity | Example |
|---|---|---|
| `PII_LEAK` | CRITICAL | email, Indian / international phone, card number (Luhn-checked), PAN, or any raw value in a forbidden key such as `name` / `address`; nested objects and GA4 user properties included; hashed values allowed |
| `MISSING_EVENT` | ERROR | `order_placed` never fired, with a hint when a similarly named event fired instead |
| `MISSING_ATTRIBUTE` | ERROR | `checkout_started` without `cart_value` |
| `TYPE_MISMATCH` | ERROR | `price` sent as `"1499"` instead of a number |
| `VALUE_NOT_ALLOWED` | ERROR | `payment_mode` = `UPI` when the plan says `upi` |
| `UNKNOWN_EVENT` | WARNING | `Product Viewed` fired: *did you mean `product_viewed`?* |
| `UNEXPECTED_ATTRIBUTE` | WARNING | attributes not in the plan |
| `NAMING_CONVENTION` | WARNING | `cartValue` in a snake_case plan |
| `DUPLICATE_EVENT` | WARNING | `add_to_cart` sent twice with identical data 300 ms apart |

Reports (console, JSON, HTML) **mask every PII value and list only attribute keys**, so the audit report
cannot leak the data it found.

## Sample output

From `samples/fixtures/buggy-journey.har`:

```
Events captured: 5 | CRITICAL: 3 | ERROR: 5 | WARNING: 7
[CRITICAL] PII_LEAK          page_view         user.mobile   Contains phone number (value 98******10).
[CRITICAL] PII_LEAK          add_to_cart       email         Contains email address (value pr********om).
[CRITICAL] PII_LEAK          add_to_cart       phone         Contains phone number (value +9********10).
[ERROR   ] MISSING_EVENT     product_viewed    -             Expected event was never fired. A similar event 'Product Viewed' was fired instead.
[ERROR   ] MISSING_EVENT     order_placed      -             Expected event was never fired.
[ERROR   ] MISSING_ATTRIBUTE checkout_started  cart_value    Required attribute is missing.
[ERROR   ] TYPE_MISMATCH     add_to_cart       price         Expected number but got string (1499).
[ERROR   ] VALUE_NOT_ALLOWED checkout_started  payment_mode  Value 'UPI' is not one of [upi, card, cod].
[WARNING ] UNKNOWN_EVENT     Product Viewed    -             Event is not in the tracking spec (fired 1x). Did you mean 'product_viewed'?
...
RESULT: FAILED (findings at or above ERROR)
```

The clean capture of the same journey returns `No findings` and exit code `0`.

## Tracking plan format

`samples/tracking-spec.json`:

```json
{
  "name": "Demo Shop - checkout funnel tracking plan",
  "namingConvention": "snake_case",
  "duplicateWindowMs": 1000,
  "collectors": [
    { "name": "demo-analytics", "urlContains": "analytics.example.test/collect", "format": "json",
      "batchField": "events", "eventField": "event", "attributesField": "properties" },
    { "name": "ga4", "urlContains": "google-analytics.com/g/collect", "format": "ga4" }
  ],
  "pii": { "allowHashed": true, "forbiddenKeys": ["customer_name"], "ignoreKeys": [] },
  "events": [
    { "name": "add_to_cart", "expected": true, "attributes": {
        "product_id": { "type": "string", "required": true },
        "price":      { "type": "number", "required": true },
        "quantity":   { "type": "integer", "required": true } } }
  ]
}
```

- **Collectors** say which requests are analytics and how to read them. `json` covers batch SDK payloads
  (MoEngage-style, Segment, custom): point `batchField` / `eventField` / `attributesField` at your SDK's shape.
  `ga4` reads Google Analytics 4 hits (`en`, `ep.*`, `epn.*`, `up.*`, batched lines).
- **expected: true** means the journey under test must fire the event.
- Types: `string`, `number`, `integer`, `boolean`, `object`, `array`, `any`.

## Design

- **Zero-dependency core.** `validator-core` uses only the JDK (including a small JSON parser), so the CLI jar
  runs anywhere with Java 17 and can be dropped into any pipeline.
- **Rules are independent** classes behind one `Rule` interface; adding a check is one class.
- **Same parser for offline and live capture**, so a HAR audit and an E2E test give identical results.
- **Findings are de-duplicated** (one finding per problem, not per firing) and sorted by severity.
- **Proven in CI:** unit tests over clean and buggy fixtures, a real-browser test against a demo shop with a
  `?buggy=1` mode, and a CLI run that must fail on the buggy capture.

## Project structure

```
validator-core/                 CLI + engine (JDK only)
  json/       Json              minimal parser / writer
  model/      events, spec, findings, report
  spec/       SpecLoader
  capture/    HarReader, PayloadParser (json + ga4)
  validate/   PiiRule, EventCoverageRule, AttributeRule, NamingConventionRule, DuplicateEventRule
  report/     console, JSON, HTML
  cli/        Main
validator-playwright/           live capture in Playwright tests
  EventRecorder
  test: demo-shop.html + DemoJourneyTrackingTest
samples/                        tracking plan + clean and buggy HAR captures (fake data)
```

## Build and run

```bash
mvn -B verify                                   # unit tests + browser tests (installs nothing else)
java -jar validator-core/target/analytics-event-validator.jar \
     --spec samples/tracking-spec.json --har samples/fixtures/buggy-journey.har --out reports
open reports/tracking-audit.html
```

First run of the browser tests: install Chromium once with
`mvn -pl validator-playwright exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`.

## Author

Mohammed Mudassir Maniyar, SDET
[LinkedIn](https://linkedin.com/in/mohammed-mudassir03)
