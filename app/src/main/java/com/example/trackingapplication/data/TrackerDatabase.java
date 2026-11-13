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
    private static final int DB_VERSION = 1;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migrations yet — bump DB_VERSION and add them here when the schema changes.
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
