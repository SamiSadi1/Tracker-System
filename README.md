# Tracker-System — running the app

> **No Firebase, no setup.** This app now stores everything in an on-device
> SQLite database, so it works the moment you install the APK: no Firebase
> project, no `google-services.json`, no API keys, no console steps, no network.

## 1. Build

The APK is built by CI on every push (`.github/workflows/android.yml`).
Download it from **GitHub → Actions → latest green run → Artifacts → app-debug**,
or build locally with Android Studio (JDK 17) / `./gradlew assembleDebug`.

Result: `app/build/outputs/apk/debug/app-debug.apk`

## 2. Install and use

1. Install the APK and open the app → the **Login** screen.
2. Tap **Create Account**, fill in the fields, tick *Admin* or *Employee*.
3. As an employee, tap **Open tracking screen** and allow location access —
   the position is written immediately, then at most every 15 seconds.
4. Sign out, register an *Admin* account, sign in as that admin → the admin
   panel lists every registered user and their last saved position.

No internet connection is required at any point.

## 3. Where the data lives

`tracker.db` (SQLite), in the app's private storage:

```sql
Users     (email PK, name, department, phone, role, passwordSalt, passwordHash, createdAt)
Locations (email PK, latitude, longitude, address, updatedAt)
Session   (id=1, email)          -- keeps you signed in between launches
```

Passwords are stored as salted, 10 000-round iterated SHA-256 hashes — never in
plain text. `Session` holds the currently signed-in e-mail.

## 4. Permissions

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (both requested together, as
Android 12+ requires). `INTERNET` is no longer needed by the app logic; it is
still declared only because Google Play services may use it for the geocoder.

Tracking runs while the tracking screen is open (`onStart` → `onStop`).
True background tracking needs a foreground service — not implemented.

## 5. Why this instead of Firebase

Firebase needs a live project with Email/Password auth enabled and security rules
published before anything works, and it only works online with a valid
`google-services.json`. The app previously shipped one from 2021, so whether it
still worked was unknowable. SQLite removes that whole class of failure.

Trade-off: data is **per device**. Two phones do not share one admin view — the
admin sees only the accounts registered on that same device. If you need real
multi-device tracking later, the cleanest options are:

| Option | Setup | Verdict |
|---|---|---|
| Supabase (Postgres + REST) | free project, anon key, no console toggles | easiest swap, keeps the same repository pattern |
| Firebase again | enable Email/Password + publish rules | works, but back to the console |
| Your own REST backend | you run it | most control, most work |

The data layer is isolated in `data/TrackerDatabase.java` + `data/Database.java`,
so replacing the storage engine only touches those two files and the activities'
call sites.

## 6. Keyboard shortcuts for maintainers

| File | Role |
|---|---|
| `data/TrackerDatabase.java` | schema, accounts, sessions, positions |
| `data/Database.java` | single accessor for the database |
| `LoginActivity` / `RegisterActivity` | local sign-in / sign-up |
| `LocationActivity` | GPS + reverse geocoding + saving |
| `AdminActivity` + `UserAdapter` | admin list |
