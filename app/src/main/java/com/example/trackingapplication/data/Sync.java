package com.example.trackingapplication.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glue between the screens, the cloud (Supabase) and the local cache (SQLite).
 *
 * Rules:
 *  - With Supabase configured, reads come from the cloud and every successful
 *    read also refreshes the local cache, so a later offline launch still shows
 *    something useful.
 *  - Sending writes to SQLite first (so the message is never lost) and then tries
 *    the cloud; a queued message can be retried later.
 *  - With no Supabase credentials the app simply stays local, exactly as before.
 *
 * All cloud work runs on a background executor; callbacks are delivered on the
 * main thread so activities can touch views directly.
 */
public final class Sync {

    private static final String TAG = "Sync";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Sync() {
    }

    /** Thrown to report a failure to the UI thread. */
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    public static boolean isCloudEnabled() {
        return SupabaseClient.isConfigured();
    }

    private static void background(Runnable work) {
        IO.execute(work);
    }

    private static <T> void deliver(final Callback<T> callback, final T value) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(value);
            }
        });
    }

    private static <T> void deliverError(final Callback<T> callback, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Sign in / sign up
    // ------------------------------------------------------------------

    /**
     * Signs up or in depending on {@code isNewAccount}. Falls back to the local
     * database when the cloud is not configured, so the app is never unusable.
     */
    public static void authenticate(final Context context,
                                    final String email,
                                    final String password,
                                    final String name,
                                    final String department,
                                    final String phone,
                                    final String role,
                                    final boolean isNewAccount,
                                    final Callback<TrackerDatabase.User> callback) {

        final TrackerDatabase db = Database.get(context);

        if (!isCloudEnabled()) {
            background(new Runnable() {
                @Override
                public void run() {
                    try {
                        TrackerDatabase.User user = isNewAccount
                                ? db.register(name, email, password, department, phone, role)
                                : db.login(email, password);
                        deliver(callback, user);
                    } catch (TrackerDatabase.RegistrationException
                             | TrackerDatabase.LoginException e) {
                        deliverError(callback, e.getMessage());
                    }
                }
            });
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                SupabaseClient client = SupabaseClient.get();
                try {
                    if (isNewAccount) {
                        client.signUp(email, password);
                        client.upsertProfile(name, department, phone, role);
                    } else {
                        client.signIn(email, password);
                    }

                    // Mirror the account locally so the app still works offline.
                    TrackerDatabase.User user;
                    try {
                        user = isNewAccount
                                ? db.register(name, email, password, department, phone, role)
                                : db.login(email, password);
                    } catch (TrackerDatabase.RegistrationException
                             | TrackerDatabase.LoginException e) {
                        user = db.findUser(email);
                        if (user == null && isNewAccount) {
                            // Cloud account exists but local cache could not be created;
                            // keep the cloud identity so messaging works.
                            user = new TrackerDatabase.User(
                                    TrackerDatabase.normalizeEmail(email),
                                    name, department, phone,
                                    TrackerDatabase.ROLE_ADMIN.equals(role)
                                            ? TrackerDatabase.ROLE_ADMIN
                                            : TrackerDatabase.ROLE_EMPLOYEE);
                        }
                    }

                    if (user != null) {
                        db.setCurrentUserEmail(user.email);
                        deliver(callback, user);
                    } else {
                        deliverError(callback, "Signed in, but the profile could not be loaded.");
                    }
                } catch (SupabaseClient.CloudException e) {
                    deliverError(callback, e.getMessage());
                }
            }
        });
    }

    public static void signOut(Context context) {
        Database.get(context).signOut();
        if (isCloudEnabled()) {
            SupabaseClient.get().signOut();
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    /** Sends a message: local copy first (never lost), then the cloud copy. */
    public static void sendMessage(final Context context,
                                   final String from,
                                   final String to,
                                   final String body,
                                   final Callback<Void> callback) {

        final TrackerDatabase db = Database.get(context);
        db.sendMessage(from, to, body);

        if (!isCloudEnabled()) {
            deliver(callback, null);
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                try {
                    SupabaseClient.get().sendMessage(to, body);
                    deliver(callback, null);
                } catch (SupabaseClient.CloudException e) {
                    // Saved locally; report the cloud failure but keep the message.
                    deliverError(callback, "Saved on this device, not delivered: " + e.getMessage());
                }
            }
        });
    }

    /** Loads a conversation from the cloud (and caches it locally). */
    public static void loadConversation(final Context context,
                                        final String me,
                                        final String partner,
                                        final Callback<List<TrackerDatabase.Message>> callback) {

        final TrackerDatabase db = Database.get(context);

        if (!isCloudEnabled()) {
            deliver(callback, db.conversation(me, partner));
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                try {
                    List<JSONObject> rows = SupabaseClient.get().conversation(partner, null);
                    List<TrackerDatabase.Message> messages = new ArrayList<>();
                    for (JSONObject row : rows) {
                        messages.add(new TrackerDatabase.Message(
                                row.optLong("id"),
                                row.optString("sender"),
                                row.optString("recipient"),
                                row.optString("body"),
                                parseIso(row.optString("sent_at")),
                                row.isNull("read_at") ? null : parseIso(row.optString("read_at"))));
                    }
                    cacheConversation(db, me, partner, messages);
                    deliver(callback, messages);
                } catch (SupabaseClient.CloudException e) {
                    // Offline: show the cached history instead of an empty screen.
                    Log.w(TAG, "Falling back to local messages: " + e.getMessage());
                    deliver(callback, db.conversation(me, partner));
                }
            }
        });
    }

    /** Marks the partner's messages read, locally and in the cloud. */
    public static void markRead(final Context context, final String me, final String partner) {
        final TrackerDatabase db = Database.get(context);
        db.markConversationRead(me, partner);
        if (!isCloudEnabled()) {
            return;
        }
        background(new Runnable() {
            @Override
            public void run() {
                try {
                    SupabaseClient.get().markRead(partner);
                } catch (SupabaseClient.CloudException e) {
                    Log.w(TAG, "Could not mark read remotely: " + e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Locations
    // ------------------------------------------------------------------

    /** Publishes a position to the cloud; always keeps the local copy. */
    public static void publishLocation(final Context context,
                                       final String email,
                                       final String name,
                                       final double latitude,
                                       final double longitude,
                                       final String address) {

        Database.get(context).saveLocation(email, latitude, longitude, address);

        if (!isCloudEnabled()) {
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                try {
                    SupabaseClient.get().putLocation(latitude, longitude, address, name);
                } catch (SupabaseClient.CloudException e) {
                    Log.w(TAG, "Location not published: " + e.getMessage());
                }
            }
        });
    }

    /** Everyone's latest position, from the cloud when available. */
    public static void loadAllLocations(final Context context,
                                        final Callback<List<TrackerDatabase.UserLocation>> callback) {

        final TrackerDatabase db = Database.get(context);

        if (!isCloudEnabled()) {
            deliver(callback, db.usersWithLocations());
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                try {
                    List<JSONObject> rows = SupabaseClient.get().locations();
                    List<TrackerDatabase.UserLocation> result = new ArrayList<>();
                    for (JSONObject row : rows) {
                        String email = row.optString("email");
                        TrackerDatabase.User user = new TrackerDatabase.User(
                                email,
                                row.optString("name", email),
                                "", "", TrackerDatabase.ROLE_EMPLOYEE);
                        result.add(new TrackerDatabase.UserLocation(
                                user,
                                row.optDouble("latitude", 0),
                                row.optDouble("longitude", 0),
                                row.optString("address", ""),
                                parseIso(row.optString("updated_at"))));
                    }
                    deliver(callback, result);
                } catch (SupabaseClient.CloudException e) {
                    Log.w(TAG, "Falling back to local locations: " + e.getMessage());
                    deliver(callback, db.usersWithLocations());
                }
            }
        });
    }

    /** Contact list for "New message". */
    public static void loadContacts(final Context context,
                                    final String me,
                                    final Callback<List<TrackerDatabase.User>> callback) {

        final TrackerDatabase db = Database.get(context);

        if (!isCloudEnabled()) {
            deliver(callback, contactsFromLocal(db, me));
            return;
        }

        background(new Runnable() {
            @Override
            public void run() {
                try {
                    List<JSONObject> rows = SupabaseClient.get().profiles();
                    List<TrackerDatabase.User> users = new ArrayList<>();
                    for (JSONObject row : rows) {
                        String email = row.optString("email");
                        if (email.equalsIgnoreCase(me)) {
                            continue;
                        }
                        users.add(new TrackerDatabase.User(
                                email,
                                row.optString("name", email),
                                row.optString("department", ""),
                                row.optString("phone", ""),
                                row.optString("role", TrackerDatabase.ROLE_EMPLOYEE)));
                    }
                    deliver(callback, users);
                } catch (SupabaseClient.CloudException e) {
                    deliver(callback, contactsFromLocal(db, me));
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<TrackerDatabase.User> contactsFromLocal(TrackerDatabase db, String me) {
        List<TrackerDatabase.User> users = new ArrayList<>();
        for (TrackerDatabase.User user : db.allUsers()) {
            if (!user.email.equalsIgnoreCase(me)) {
                users.add(user);
            }
        }
        return users;
    }

    private static void cacheConversation(TrackerDatabase db, String me, String partner,
                                          List<TrackerDatabase.Message> messages) {
        // Simple cache strategy: replace the local thread with what the cloud has.
        db.clearConversation(me, partner);
        for (TrackerDatabase.Message message : messages) {
            db.insertMessage(message.sender, message.recipient, message.body,
                    message.sentAt, message.readAt);
        }
    }

    /** Parses Supabase's ISO-8601 timestamp into epoch millis. */
    static long parseIso(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return 0L;
        }
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return format.parse(iso).getTime();
        } catch (java.text.ParseException e) {
            // Postgres may omit milliseconds.
            try {
                java.text.SimpleDateFormat fallback = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                fallback.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return fallback.parse(iso).getTime();
            } catch (java.text.ParseException ignored) {
                return 0L;
            }
        }
    }
}
