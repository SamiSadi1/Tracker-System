package com.example.trackingapplication.data;

import android.content.Context;

/**
 * Single entry point for the app's data storage.
 *
 * The original app required a live Firebase project. It now uses on-device
 * SQLite, so the app works immediately after installing the APK with no
 * console setup, no API keys and no network.
 */
public final class Database {

    private static volatile TrackerDatabase instance;

    private Database() {
        // no instances
    }

    public static TrackerDatabase get(Context context) {
        if (instance == null) {
            synchronized (Database.class) {
                if (instance == null) {
                    instance = TrackerDatabase.create(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
}
