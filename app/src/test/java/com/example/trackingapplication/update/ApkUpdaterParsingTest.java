package com.example.trackingapplication.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Pure-Java checks for reading a GitHub release payload (no Android involved). */
public class ApkUpdaterParsingTest {

    /** Shaped like the real API payload: notes are a JSON string, so unquoted. */
    private static final String RELEASE_JSON =
            "{\"tag_name\":\"debug-latest\"," +
            "\"name\":\"Tracker System — build 87\"," +
            "\"body\":\"Direct APK download\\nversionCode: 87 (used by the in-app updater)\"," +
            "\"assets\":[{" +
            "\"name\":\"TrackerSystem-debug.apk\"," +
            "\"state\":\"uploaded\"," +
            "\"browser_download_url\":\"https://github.com/SamiSadi1/Tracker-System/releases/download/debug-latest/TrackerSystem-debug.apk\"" +
            "}]}";

    @Test
    public void findsTheApkDownloadUrl() {
        assertEquals(
                "https://github.com/SamiSadi1/Tracker-System/releases/download/debug-latest/TrackerSystem-debug.apk",
                ApkUpdater.findAssetUrl(RELEASE_JSON));
    }

    @Test
    public void readsVersionCodeFromReleaseNotes() {
        assertEquals(87, ApkUpdater.parseVersionCode(RELEASE_JSON));
    }

    @Test
    public void readsVersionCodeFromQuotedJsonField() {
        assertEquals(42, ApkUpdater.parseVersionCode("{\"versionCode\": 42}"));
    }

    @Test
    public void fallsBackToTheBuildNumberInTheTitle() {
        assertEquals(99, ApkUpdater.parseVersionCode(
                "{\"name\":\"Tracker System — build 99\",\"body\":\"no version here\"}"));
    }

    @Test
    public void returnsNullWhenReleaseHasNoApk() {
        assertNull(ApkUpdater.findAssetUrl("{\"assets\":[]}"));
    }

    @Test
    public void unknownVersionCodeIsZeroSoItNeverForcesAnUpdate() {
        assertEquals(0, ApkUpdater.parseVersionCode("{\"assets\":[]}"));
    }
}
