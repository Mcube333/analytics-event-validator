package com.mudassir.analytics.model;

/**
 * Where events are sent and how to read them.
 * format "json": body is JSON; events in batchField (optional), name in eventField, attributes in attributesField
 *                (when attributesField is empty, every other top-level field is an attribute).
 * format "ga4":  Google Analytics 4 collect hits (en=event name, ep.*=text params, epn.*=number params, up.*=user props).
 */
public record Collector(String name, String urlContains, String format,
                        String batchField, String eventField, String attributesField) {

    public boolean matches(String url) {
        return url != null && url.contains(urlContains);
    }
}
