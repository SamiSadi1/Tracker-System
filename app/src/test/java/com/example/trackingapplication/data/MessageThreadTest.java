package com.example.trackingapplication.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pure-Java checks on the conversation key rules the Messages table relies on.
 * (The queries themselves need SQLite, which only exists on a device/emulator,
 * so the schema is exercised through instrumented tests instead.)
 */
public class MessageThreadTest {

    @Test
    public void conversationKeyIsOrderIndependent() {
        // The same thread must come back regardless of who opened it.
        assertEquals(low("alice@x.com", "bob@x.com"), low("bob@x.com", "alice@x.com"));
        assertEquals(high("alice@x.com", "bob@x.com"), high("bob@x.com", "alice@x.com"));
    }

    @Test
    public void conversationKeyNormalisesEmailCase() {
        assertEquals("alice@x.com", low(" Alice@X.com ", "bob@x.com"));
        assertEquals("bob@x.com", high("alice@x.com", " BOB@X.com"));
    }

    @Test
    public void lowIsAlwaysLexicographicallyFirst() {
        String lowKey = low("zed@x.com", "amy@x.com");
        assertEquals("amy@x.com", lowKey);
    }

    private static String low(String a, String b) {
        String x = TrackerDatabase.normalizeEmail(a);
        String y = TrackerDatabase.normalizeEmail(b);
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static String high(String a, String b) {
        String x = TrackerDatabase.normalizeEmail(a);
        String y = TrackerDatabase.normalizeEmail(b);
        return x.compareTo(y) <= 0 ? y : x;
    }
}
