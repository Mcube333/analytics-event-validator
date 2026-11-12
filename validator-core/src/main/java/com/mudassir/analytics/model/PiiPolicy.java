package com.mudassir.analytics.model;

import java.util.Set;

/**
 * forbiddenKeys: attribute names that must never carry a raw value (email, phone, name ...).
 * ignoreKeys:    attribute names excluded from value scanning (e.g. a numeric order_id that looks like a phone).
 * allowHashed:   SHA-256 / MD5 hex values are treated as safe.
 */
public record PiiPolicy(boolean allowHashed, Set<String> forbiddenKeys, Set<String> ignoreKeys) {
}
