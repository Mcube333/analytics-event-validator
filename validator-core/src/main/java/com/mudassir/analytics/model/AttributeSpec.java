package com.mudassir.analytics.model;

import java.util.List;

/** type: string | number | integer | boolean | object | array | any */
public record AttributeSpec(String type, boolean required, List<String> allowed) {
}
