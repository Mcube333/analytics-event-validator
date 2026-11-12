package com.mudassir.analytics.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer so the validator core has zero third-party dependencies.
 * Objects -> LinkedHashMap, arrays -> ArrayList, integers -> Long, decimals -> Double.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    public static String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    // ---------------------------------------------------------------- writer

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            out.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                pad(out, indent + 1);
                out.append('"').append(escape(String.valueOf(e.getKey()))).append("\": ");
                write(e.getValue(), out, indent + 1);
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            pad(out, indent);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                pad(out, indent + 1);
                write(list.get(i), out, indent + 1);
                out.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            pad(out, indent);
            out.append(']');
        } else {
            out.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static void pad(StringBuilder out, int indent) {
        out.append("  ".repeat(indent));
    }

    public static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + i);
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        char peek() {
            if (atEnd()) {
                throw error("Unexpected end of JSON");
            }
            return s.charAt(i);
        }

        void expect(char c) {
            skipWhitespace();
            if (peek() != c) {
                throw error("Expected '" + c + "'");
            }
            i++;
        }

        void literal(String word) {
            if (!s.startsWith(word, i)) {
                throw error("Expected " + word);
            }
            i += word.length();
        }

        Object value() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    return number();
            }
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        String string() {
            if (peek() != '"') {
                throw error("Expected string");
            }
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') {
                    return b.toString();
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = peek();
                i++;
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("Bad unicode escape");
                        }
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("Bad escape \\" + e);
                }
            }
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String n = s.substring(start, i);
            if (n.isEmpty()) {
                throw error("Unexpected character '" + s.charAt(start) + "'");
            }
            if (n.contains(".") || n.contains("e") || n.contains("E")) {
                return Double.parseDouble(n);
            }
            try {
                return Long.parseLong(n);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(n);
            }
        }
    }
}
