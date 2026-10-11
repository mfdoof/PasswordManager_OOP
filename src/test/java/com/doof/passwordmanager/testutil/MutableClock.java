package com.doof.passwordmanager.testutil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose time can be advanced manually. */
public final class MutableClock extends Clock {
    private long millis;

    public MutableClock(long startMillis) {
        this.millis = startMillis;
    }

    public void advance(long deltaMillis) {
        millis += deltaMillis;
    }

    @Override public long millis() { return millis; }
    @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
