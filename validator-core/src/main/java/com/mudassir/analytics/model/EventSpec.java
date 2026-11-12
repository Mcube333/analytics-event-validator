package com.mudassir.analytics.model;

import java.util.Map;

/**
 * expected = the journey under test must fire this event at least once.
 * allowExtraAttributes = attributes not in the spec are reported as warnings unless true.
 */
public record EventSpec(String name, boolean expected, boolean allowExtraAttributes,
                        Map<String, AttributeSpec> attributes) {
}
