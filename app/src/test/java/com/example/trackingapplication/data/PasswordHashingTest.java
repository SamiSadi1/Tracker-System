package com.example.trackingapplication.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-Java checks for the local password hashing used by the on-device
 * database (no Firebase, no Android framework involved).
 */
public class PasswordHashingTest {

    @Test
    public void samePasswordAndSaltProduceSameHash() {
        byte[] salt = TrackerDatabase.fromHex("00112233445566778899aabbccddeeff");
        assertEquals(TrackerDatabase.hash("secret", salt),
                TrackerDatabase.hash("secret", salt));
    }

    @Test
    public void differentPasswordProducesDifferentHash() {
        byte[] salt = TrackerDatabase.fromHex("00112233445566778899aabbccddeeff");
        assertNotEquals(TrackerDatabase.hash("secret", salt),
                TrackerDatabase.hash("secret2", salt));
    }

    @Test
    public void differentSaltProducesDifferentHash() {
        assertNotEquals(
                TrackerDatabase.hash("secret", TrackerDatabase.fromHex("00112233445566778899aabbccddeeff")),
                TrackerDatabase.hash("secret", TrackerDatabase.fromHex("ffeeddccbbaa99887766554433221100")));
    }

    @Test
    public void plainTextIsNotStored() {
        byte[] salt = TrackerDatabase.fromHex("00112233445566778899aabbccddeeff");
        assertFalse(TrackerDatabase.hash("secret", salt).contains("secret"));
    }

    @Test
    public void hexRoundTrips() {
        byte[] bytes = TrackerDatabase.fromHex("00ff10ab");
        assertEquals("00ff10ab", TrackerDatabase.toHex(bytes));
    }

    @Test
    public void constantTimeEqualsMatchesNormalEquality() {
        assertTrue(TrackerDatabase.constantTimeEquals("abc", "abc"));
        assertFalse(TrackerDatabase.constantTimeEquals("abc", "abd"));
        assertFalse(TrackerDatabase.constantTimeEquals("abc", "abcd"));
        assertFalse(TrackerDatabase.constantTimeEquals(null, "abc"));
    }

    @Test
    public void emailsAreNormalized() {
        assertEquals("user@example.com", TrackerDatabase.normalizeEmail("  User@Example.COM "));
    }
}
