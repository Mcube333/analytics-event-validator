package com.mudassir.analytics.model;

public enum Severity {
    CRITICAL(3), ERROR(2), WARNING(1);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(Severity other) {
        return rank >= other.rank;
    }
}
