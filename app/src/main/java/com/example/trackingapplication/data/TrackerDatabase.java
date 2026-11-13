package com.example.trackingapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * On-device SQLite storage: accounts, sessions and saved positions.
 *
 * Replaces Firebase Auth + Firestore, so no project, API key or network is required.
 */
public class TrackerDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "tracker.db";
    private static final int DB_VERSION = 2;

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_EMPLOYEE = "employee";

    private static final String CREATE_USERS =
            "CREATE TABLE Users (" +
                    "  email TEXT PRIMARY KEY," +
                    "  name TEXT NOT NULL," +
                    "  department TEXT," +
                    "  phone TEXT," +
                    "  role TEXT NOT NULL," +
                    "  passwordSalt TEXT NOT NULL," +
                    "  passwordHash TEXT NOT NULL," +
                    "  createdAt INTEGER NOT NULL)";

    private static final String CREATE_LOCATIONS =
            "CREATE TABLE Locations (" +
                    "  email TEXT PRIMARY KEY," +
                    "  latitude REAL NOT NULL," +
                    "  longitude REAL NOT NULL," +
                    "  address TEXT," +
                    "  updatedAt INTEGER NOT NULL)";

    /**
     * One row per delivered message. A conversation is identified by
     * (userLow, userHigh) so a thread is independent of who sent what.
     */
    private static final String CREATE_MESSAGES =
            "CREATE TABLE Messages (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  sender TEXT NOT NULL," +
                    "  recipient TEXT NOT NULL," +
                    "  body TEXT NOT NULL," +
                    "  sentAt INTEGER NOT NULL," +
                    "  readAt INTEGER)";

    private static final String CREATE_MESSAGES_INDEX =
            "CREATE INDEX idx_messages_thread ON Messages (sender, recipient, sentAt)";

    private static final String CREATE_SESSION =
            "CREATE TABLE Session (" +
                    "  id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "  email TEXT NOT NULL)";

    private TrackerDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public static TrackerDatabase create(Context context) {
        return new TrackerDatabase(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS);
        db.execSQL(CREATE_LOCATIONS);
        db.execSQL(CREATE_SESSION);
        db.execSQL(CREATE_MESSAGES);
        db.execSQL(CREATE_MESSAGES_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Existing installs already hold accounts and positions, so migrations must
        // only add what is missing — never drop data.
        if (oldVersion < 2) {
            db.execSQL(CREATE_MESSAGES);
            db.execSQL(CREATE_MESSAGES_INDEX);
        }
    }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    /** A registered account. The password hash never leaves this class. */
    public static class User {
        public final String email;
        public final String name;
        public final String department;
        public final String phone;
        public final String role;

        public User(String email, String name, String department, String phone, String role) {
            this.email = email;
            this.name = name;
            this.department = department;
            this.phone = phone;
            this.role = role;
        }

        public boolean isAdmin() {
            return ROLE_ADMIN.equals(role);
        }
    }

    /** A user plus their last known position, as shown on the admin screen. */
    public static class UserLocation {
        public final User user;
        public final Double latitude;
        public final Double longitude;
        public final String address;
        public final Long updatedAt;

        public UserLocation(User user, Double latitude, Double longitude,
                            String address, Long updatedAt) {
            this.user = user;
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address;
            this.updatedAt = updatedAt;
        }

        public boolean hasLocation() {
            return latitude != null && longitude != null;
        }
    }

    /** Thrown when registration cannot be completed. */
    public static class RegistrationException extends Exception {
        public RegistrationException(String message) {
            super(message);
        }
    }

    /** Thrown when a login attempt fails. */
    public static class LoginException extends Exception {
        public LoginException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    /**
     * Creates an account and starts its session.
     *
     * @throws RegistrationException if the e-mail is already registered.
     */
    public User register(String name, String email, String password,
                         String department, String phone, String role)
            throws RegistrationException {

        String key = normalizeEmail(email);
        if (findUser(key) != null) {
            throw new RegistrationException("An account with this e-mail already exists.");
        }

        byte[] salt = newSalt();
        ContentValues values = new ContentValues();
        values.put("email", key);
        values.put("name", name.trim());
        values.put("department", department == null ? "" : department.trim());
        values.put("phone", phone == null ? "" : phone.trim());
        values.put("role", ROLE_ADMIN.equals(role) ? ROLE_ADMIN : ROLE_EMPLOYEE);
        values.put("passwordSalt", toHex(salt));
        values.put("passwordHash", hash(password, salt));
        values.put("createdAt", System.currentTimeMillis());

        getWritableDatabase().insertOrThrow("Users", null, values);
        setCurrentUserEmail(key);

        User user = findUser(key);
        if (user == null) {
            throw new RegistrationException("Could not create the account.");
        }
        return user;
    }

    /**
     * Verifies the credentials and starts a session.
     *
     * @throws LoginException if the e-mail is unknown or the password is wrong.
     */
    public User login(String email, String password) throws LoginException {
        String key = normalizeEmail(email);

        Cursor cursor = getReadableDatabase().query(
                "Users",
                new String[]{"email", "name", "department", "phone", "role",
                        "passwordSalt", "passwordHash"},
                "email = ?", new String[]{key}, null, null, null);

        try {
            if (!cursor.moveToFirst()) {
                throw new LoginException("No account found for this e-mail.");
            }
            byte[] salt = fromHex(cursor.getString(5));
            String expected = cursor.getString(6);
            if (!constantTimeEquals(expected, hash(password, salt))) {
                throw new LoginException("Incorrect password.");
            }
            setCurrentUserEmail(key);
            return new User(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4));
        } finally {
            cursor.close();
        }
    }

    public User findUser(String email) {
        Cursor cursor = getReadableDatabase().query(
                "Users",
                new String[]{"email", "name", "department", "phone", "role"},
                "email = ?", new String[]{normalizeEmail(email)}, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new User(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4));
        } finally {
            cursor.close();
        }
    }

    /** Every registered account, oldest first. */
    public List<User> allUsers() {
        List<User> users = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                "Users",
                new String[]{"email", "name", "department", "phone", "role"},
                null, null, null, null, "createdAt ASC");
        try {
            while (cursor.moveToNext()) {
                users.add(new User(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3), cursor.getString(4)));
            }
        } finally {
            cursor.close();
        }
        return users;
    }

    /**
     * Local stand-in for the "reset password" e-mail: sets a new password when the
     * account exists. Returns false when there is no such account.
     */
    public boolean resetPassword(String email, String newPassword) {
        String key = normalizeEmail(email);
        if (findUser(key) == null) {
            return false;
        }
        byte[] salt = newSalt();
        ContentValues values = new ContentValues();
        values.put("passwordSalt", toHex(salt));
        values.put("passwordHash", hash(newPassword, salt));
        int rows = getWritableDatabase().update("Users", values, "email = ?", new String[]{key});
        return rows > 0;
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /** Returns the signed-in user, or null when nobody is signed in. */
    public User currentUser() {
        Cursor cursor = getReadableDatabase().query(
                "Session", new String[]{"email"}, null, null, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return findUser(cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    public void setCurrentUserEmail(String email) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("email", normalizeEmail(email));
        getWritableDatabase().insertWithOnConflict(
                "Session", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void signOut() {
        getWritableDatabase().delete("Session", "id = 1", null);
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    /** Inserts or replaces the position saved for a user. */
    public void saveLocation(String email, double latitude, double longitude, String address) {
        ContentValues values = new ContentValues();
        values.put("email", normalizeEmail(email));
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("address", address == null ? "" : address);
        values.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "Locations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public UserLocation locationFor(String email) {
        Cursor cursor = getReadableDatabase().query(
                "Locations",
                new String[]{"latitude", "longitude", "address", "updatedAt"},
                "email = ?", new String[]{normalizeEmail(email)}, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new UserLocation(null, cursor.getDouble(0), cursor.getDouble(1),
                    cursor.getString(2), cursor.getLong(3));
        } finally {
            cursor.close();
        }
    }

    /** Every user together with their last saved position (admin screen). */
    public List<UserLocation> usersWithLocations() {
        List<UserLocation> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT u.email, u.name, u.department, u.phone, u.role, " +
                        "       l.latitude, l.longitude, l.address, l.updatedAt " +
                        "FROM Users u LEFT JOIN Locations l ON l.email = u.email " +
                        "ORDER BY u.createdAt ASC",
                null);
        try {
            while (cursor.moveToNext()) {
                User user = new User(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3), cursor.getString(4));
                boolean located = !cursor.isNull(5) && !cursor.isNull(6);
                result.add(new UserLocation(user,
                        located ? cursor.getDouble(5) : null,
                        located ? cursor.getDouble(6) : null,
                        located ? cursor.getString(7) : null,
                        located ? cursor.getLong(8) : null));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Messages (offline: stored and delivered on this device)
    // ------------------------------------------------------------------

    /** A single message between two accounts. */
    public static class Message {
        public final long id;
        public final String sender;
        public final String recipient;
        public final String body;
        public final long sentAt;
        public final Long readAt;

        public Message(long id, String sender, String recipient, String body,
                       long sentAt, Long readAt) {
            this.id = id;
            this.sender = sender;
            this.recipient = recipient;
            this.body = body;
            this.sentAt = sentAt;
            this.readAt = readAt;
        }
    }

    /** A conversation summary for the inbox list. */
    public static class Conversation {
        public final String otherEmail;
        public final String otherName;
        public final String lastMessage;
        public final long lastAt;
        public final int unreadCount;

        public Conversation(String otherEmail, String otherName, String lastMessage,
                            long lastAt, int unreadCount) {
            this.otherEmail = otherEmail;
            this.otherName = otherName;
            this.lastMessage = lastMessage;
            this.lastAt = lastAt;
            this.unreadCount = unreadCount;
        }
    }

    /** Inserts a message with a known timestamp (used when caching cloud history). */
    public long insertMessage(String sender, String recipient, String body,
                              long sentAt, Long readAt) {
        ContentValues values = new ContentValues();
        values.put("sender", normalizeEmail(sender));
        values.put("recipient", normalizeEmail(recipient));
        values.put("body", body == null ? "" : body);
        values.put("sentAt", sentAt);
        if (readAt != null) {
            values.put("readAt", readAt);
        }
        return getWritableDatabase().insert("Messages", null, values);
    }

    /** Removes both directions of a conversation (used before re-caching from cloud). */
    public void clearConversation(String a, String b) {
        String low = threadKeyLow(a, b);
        String high = threadKeyHigh(a, b);
        getWritableDatabase().delete("Messages",
                "(sender = ? AND recipient = ?) OR (sender = ? AND recipient = ?)",
                new String[]{low, high, high, low});
    }

    /** Stores a message and returns its id. */
    public long sendMessage(String sender, String recipient, String body) {
        ContentValues values = new ContentValues();
        values.put("sender", normalizeEmail(sender));
        values.put("recipient", normalizeEmail(recipient));
        values.put("body", body == null ? "" : body.trim());
        values.put("sentAt", System.currentTimeMillis());
        return getWritableDatabase().insert("Messages", null, values);
    }

    /**
     * Full history of a conversation, oldest first.
     *
     * @param userLow  the lexicographically smaller e-mail
     * @param userHigh the other one
     */
    public List<Message> conversation(String a, String b) {
        String low = threadKeyLow(a, b);
        String high = threadKeyHigh(a, b);

        List<Message> messages = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                "Messages",
                new String[]{"id", "sender", "recipient", "body", "sentAt", "readAt"},
                "(sender = ? AND recipient = ?) OR (sender = ? AND recipient = ?)",
                new String[]{low, high, high, low},
                null, null, "sentAt ASC, id ASC");
        try {
            while (cursor.moveToNext()) {
                messages.add(new Message(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getLong(4),
                        cursor.isNull(5) ? null : cursor.getLong(5)));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    /**
     * Inbox for a user: one entry per conversation partner, newest first, with the
     * number of unread messages from that partner.
     */
    public List<Conversation> conversationsFor(String email) {
        String me = normalizeEmail(email);

        List<Conversation> result = new ArrayList<>();
        // NOTE: no window functions here. ROW_NUMBER() OVER (...) needs SQLite 3.25+,
        // which only ships from API 29 (Android 10) onwards; minSdk is 23, so the
        // inbox would throw "near OVER: syntax error" on Android 6-9. A grouped
        // query does the same job with syntax every supported version understands.
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT partner, body, lastAt FROM (" +
                        "  SELECT CASE WHEN sender = ? THEN recipient ELSE sender END AS partner," +
                        "         MAX(sentAt) AS lastAt" +
                        "  FROM Messages WHERE sender = ? OR recipient = ?" +
                        "  GROUP BY partner" +
                        ") summary JOIN Messages m" +
                        "  ON m.sentAt = summary.lastAt" +
                        " AND (CASE WHEN m.sender = ? THEN m.recipient ELSE m.sender END) = summary.partner" +
                        " GROUP BY summary.partner" +
                        " ORDER BY lastAt DESC",
                new String[]{me, me, me, me});
        try {
            while (cursor.moveToNext()) {
                String partner = cursor.getString(0);
                String last = cursor.getString(1);
                long lastAt = cursor.getLong(2);

                User partnerUser = findUser(partner);
                String partnerName = partnerUser != null ? partnerUser.name : partner;

                result.add(new Conversation(partner, partnerName, last, lastAt,
                        unreadCount(partner, me)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /** Number of messages from {@code from} that {@code to} has not read yet. */
    public int unreadCount(String from, String to) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM Messages " +
                        "WHERE sender = ? AND recipient = ? AND readAt IS NULL",
                new String[]{normalizeEmail(from), normalizeEmail(to)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /** Marks everything a partner sent as read. */
    public void markConversationRead(String me, String partner) {
        ContentValues values = new ContentValues();
        values.put("readAt", System.currentTimeMillis());
        getWritableDatabase().update("Messages", values,
                "sender = ? AND recipient = ? AND readAt IS NULL",
                new String[]{normalizeEmail(partner), normalizeEmail(me)});
    }

    public int totalUnread(String email) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM Messages WHERE recipient = ? AND readAt IS NULL",
                new String[]{normalizeEmail(email)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static String threadKeyLow(String a, String b) {
        String x = normalizeEmail(a);
        String y = normalizeEmail(b);
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static String threadKeyHigh(String a, String b) {
        String x = normalizeEmail(a);
        String y = normalizeEmail(b);
        return x.compareTo(y) <= 0 ? y : x;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.US);
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /** PBKDF2-style iterated SHA-256; suitable for a local demo database. */
    static String hash(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = password.getBytes(Charset.forName("UTF-8"));
            digest.update(salt);
            byte[] hashed = digest.digest(value);
            for (int i = 0; i < 10_000; i++) {
                digest.reset();
                digest.update(salt);
                hashed = digest.digest(hashed);
            }
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    static byte[] fromHex(String hex) {
        int length = hex.length() / 2;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
