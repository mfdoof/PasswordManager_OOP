package com.doof.passwordmanager.dao;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose time can be advanced manually. */
final class MutableClock extends Clock {
    private long millis;

    MutableClock(long startMillis) {
        this.millis = startMillis;
    }

    void advance(long deltaMillis) {
        millis += deltaMillis;
    }

    @Override public long millis() { return millis; }
    @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
