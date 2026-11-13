package com.example.trackingapplication.data;

import android.util.Log;

import com.example.trackingapplication.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal Supabase client (Auth + REST), no SDK needed.
 *
 * Configure it by putting SUPABASE_URL and SUPABASE_ANON_KEY into
 * local.properties. When they are empty the app stays in offline SQLite mode,
 * so a build without credentials still runs.
 */
public final class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static SupabaseClient instance;

    private final OkHttpClient http;
    private final String baseUrl;
    private final String anonKey;

    /** Access token of the signed-in user, used as the Bearer token. */
    private String accessToken;
    private String refreshToken;
    private String userEmail;

    private SupabaseClient(String baseUrl, String anonKey) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.anonKey = anonKey;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /** True when the build was given Supabase credentials. */
    public static boolean isConfigured() {
        return !isEmpty(BuildConfig.SUPABASE_URL) && !isEmpty(BuildConfig.SUPABASE_ANON_KEY);
    }

    public static SupabaseClient get() {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) {
                    instance = new SupabaseClient(
                            BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY);
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    public boolean isSignedIn() {
        return !isEmpty(accessToken) && !isEmpty(userEmail);
    }

    public String userEmail() {
        return userEmail;
    }

    public void restoreSession(String email, String accessToken, String refreshToken) {
        this.userEmail = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public void signOut() {
        accessToken = null;
        refreshToken = null;
        userEmail = null;
    }

    /** Thrown for any failed remote call; carries a message safe to show a user. */
    public static class CloudException extends Exception {
        public CloudException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------
    // Auth (GoTrue)
    // ------------------------------------------------------------------

    public void signUp(String email, String password) throws CloudException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
        } catch (JSONException e) {
            throw new CloudException("Invalid credentials");
        }

        JSONObject response = post("/auth/v1/signup", body.toString(), false);
        captureSession(response);
    }

    public void signIn(String email, String password) throws CloudException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
        } catch (JSONException e) {
            throw new CloudException("Invalid credentials");
        }

        JSONObject response = post("/auth/v1/token?grant_type=password", body.toString(), false);
        captureSession(response);
    }

    private void captureSession(JSONObject response) throws CloudException {
        try {
            if (response.has("access_token")) {
                accessToken = response.getString("access_token");
            }
            if (response.has("refresh_token")) {
                refreshToken = response.getString("refresh_token");
            }
            if (response.has("user")) {
                JSONObject user = response.getJSONObject("user");
                userEmail = user.optString("email", null);
            }
            if (isEmpty(accessToken) || isEmpty(userEmail)) {
                throw new CloudException("Sign-up succeeded but email confirmation is still "
                        + "required in your Supabase project (Authentication -> Providers -> Email).");
            }
        } catch (JSONException e) {
            throw new CloudException("Unexpected response from Supabase");
        }
    }

    // ------------------------------------------------------------------
    // Profiles
    // ------------------------------------------------------------------

    /** Creates or updates this user's profile row. */
    public void upsertProfile(String name, String department, String phone, String role)
            throws CloudException {
        JSONObject row = new JSONObject();
        try {
            row.put("email", userEmail);
            row.put("name", name);
            row.put("department", department);
            row.put("phone", phone);
            row.put("role", role);
        } catch (JSONException e) {
            throw new CloudException("Could not build the profile");
        }

        Request request = authed("/rest/v1/profiles")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(row.toString(), JSON))
                .build();
        execute(request);
    }

    /** Every profile except the signed-in one (for the contact list). */
    public List<JSONObject> profiles() throws CloudException {
        Request request = authed("/rest/v1/profiles?select=*&order=name.asc").get().build();
        return executeArray(request);
    }

    // ------------------------------------------------------------------
    // Locations
    // ------------------------------------------------------------------

    /** Publishes the current position of the signed-in user. */
    public void putLocation(double latitude, double longitude, String address, String name)
            throws CloudException {
        JSONObject row = new JSONObject();
        try {
            row.put("email", userEmail);
            row.put("name", name);
            row.put("latitude", latitude);
            row.put("longitude", longitude);
            row.put("address", address);
            row.put("updated_at", isoNow());
        } catch (JSONException e) {
            throw new CloudException("Could not build the location");
        }

        Request request = authed("/rest/v1/locations")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(row.toString(), JSON))
                .build();
        execute(request);
    }

    /** Latest known position of everybody (used by the live map / admin list). */
    public List<JSONObject> locations() throws CloudException {
        Request request = authed("/rest/v1/locations?select=*&order=updated_at.desc").get().build();
        return executeArray(request);
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public void sendMessage(String recipient, String body) throws CloudException {
        JSONObject row = new JSONObject();
        try {
            row.put("sender", userEmail);
            row.put("recipient", recipient);
            row.put("body", body);
            row.put("sent_at", isoNow());
        } catch (JSONException e) {
            throw new CloudException("Could not build the message");
        }

        Request request = authed("/rest/v1/messages")
                .addHeader("Prefer", "return=minimal")
                .post(RequestBody.create(row.toString(), JSON))
                .build();
        execute(request);
    }

    /**
     * Messages in the conversation with {@code partner}, oldest first.
     * PostgREST "or" syntax selects both directions in one request.
     */
    public List<JSONObject> conversation(String partner, String afterIso) throws CloudException {
        String filter = "or=(and(sender.eq." + userEmail + ",recipient.eq." + partner + "),"
                + "and(sender.eq." + partner + ",recipient.eq." + userEmail + "))";
        String url = "/rest/v1/messages?select=*&" + filter + "&order=sent_at.asc";
        if (!isEmpty(afterIso)) {
            url += "&sent_at=gt." + afterIso;
        }
        return executeArray(authed(url).get().build());
    }

    /** Marks everything the partner sent as read. */
    public void markRead(String partner) throws CloudException {
        Request request = authed("/rest/v1/messages?sender=eq." + partner
                + "&recipient=eq." + userEmail + "&read_at=is.null")
                .addHeader("Prefer", "return=minimal")
                .patch(RequestBody.create("{\"read_at\":\"" + isoNow() + "\"}", JSON))
                .build();
        execute(request);
    }

    /** Simple poll for an inbox: latest message per conversation partner. */
    public List<JSONObject> recentMessages() throws CloudException {
        String url = "/rest/v1/messages?select=*"
                + "&or=(sender.eq." + userEmail + ",recipient.eq." + userEmail + ")"
                + "&order=sent_at.desc&limit=200";
        return executeArray(authed(url).get().build());
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private Request.Builder authed(String path) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path);
        builder.addHeader("apikey", anonKey);
        if (!isEmpty(accessToken)) {
            builder.addHeader("Authorization", "Bearer " + accessToken);
        } else {
            builder.addHeader("Authorization", "Bearer " + anonKey);
        }
        return builder;
    }

    private JSONObject post(String path, String json, boolean auth) throws CloudException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON));

        builder.addHeader("Authorization", "Bearer " + anonKey);

        String body = execute(builder.build());
        try {
            return isEmpty(body) ? new JSONObject() : new JSONObject(body);
        } catch (JSONException e) {
            throw new CloudException("Unexpected response from Supabase");
        }
    }

    private String execute(Request request) throws CloudException {
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                Log.w(TAG, "HTTP " + response.code() + " for " + request.url() + ": " + text);
                throw new CloudException(friendlyError(response.code(), text));
            }
            return text;
        } catch (IOException e) {
            throw new CloudException("No connection: " + e.getMessage());
        }
    }

    private List<JSONObject> executeArray(Request request) throws CloudException {
        String text = execute(request);
        List<JSONObject> rows = new ArrayList<>();
        if (isEmpty(text)) {
            return rows;
        }
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                rows.add(array.getJSONObject(i));
            }
        } catch (JSONException e) {
            throw new CloudException("Unexpected response from Supabase");
        }
        return rows;
    }

    private static String friendlyError(int code, String body) {
        if (code == 401 || code == 403) {
            return "Not allowed (check the Supabase table policies).";
        }
        if (code == 404) {
            return "Supabase table missing — run the SQL from README first.";
        }
        if (code == 409) {
            return "That record already exists.";
        }
        String detail = body == null ? "" : body.trim();
        if (detail.length() > 160) {
            detail = detail.substring(0, 160) + "…";
        }
        return "Server error " + code + (detail.isEmpty() ? "" : ": " + detail);
    }

    private static String isoNow() {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
