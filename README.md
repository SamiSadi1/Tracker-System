# Tracker-System — running the app

> **No Firebase, no setup.** This app now stores everything in an on-device
> SQLite database, so it works the moment you install the APK: no Firebase
> project, no `google-services.json`, no API keys, no console steps, no network.

## 1. Install the APK on your phone

**Direct download (no login needed):**

```
https://github.com/SamiSadi1/Tracker-System/releases/download/debug-latest/TrackerSystem-debug.apk
```

Or open **Releases** → `debug-latest` → `TrackerSystem-debug.apk`, or scan the QR
code on the release page. Every push rebuilds and re-uploads that same file.

On the phone: tap the downloaded file → Android may ask to allow
*Install unknown apps* for your browser/files app → allow → **Install** → open
**Tracker System**.

It is a debug build (debug signing key): fine for testing and sharing, not for
the Play Store. Build locally instead with Android Studio (JDK 17) or
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## 1b. Updating the app later

The app can update itself: **Login screen → Check for updates**.

- It reads the `debug-latest` release, compares the published `versionCode`
  with the installed one, downloads the newer APK and opens the installer.
- Android shows its own **Install / Cancel** dialog — a confirmation tap is
  mandatory. Silent updates are only possible for Play Store apps (or with
  root / device-owner privileges), by design.
- The first time, Android asks you to allow *Installing unknown apps* for
  Tracker System; the updater opens that exact settings screen for you.
- Nothing is checked automatically, so the app still works fully offline.
- Every CI build gets a unique, always-increasing `versionCode`
  (`VERSION_CODE = GitHub run number`), which is what makes the update install
  over the previous version instead of being rejected.

## 2. Install and use

1. Install the APK and open the app → the **Login** screen.
2. Tap **Create Account**, fill in the fields, tick *Admin* or *Employee*.
3. As an employee, tap **Open tracking screen** and allow location access —
   the position is written immediately, then at most every 15 seconds.
4. Sign out, register an *Admin* account, sign in as that admin → the admin
   panel lists every registered user and their last saved position.

No internet connection is required at any point.

## 2b. Offline messaging

Both homes have a **Messages** button (labelled *Messages (N new)* when something
is unread).

1. **Messages → New message** → pick any other account registered on the device.
2. Type and tap **Send**. It is stored immediately — no network involved.
3. The inbox shows the last message, time and an unread badge; opening a thread
   marks it read.

### What "offline" honestly means here

Messages are rows in the on-device SQLite database, so they are delivered
instantly, work in airplane mode, and never leave the phone.

**The catch:** two *different phones* cannot talk to each other, because there is
no shared server in between. On one phone, two accounts (for example one Admin
and one Employee) can chat — which is enough to demo, test and use the app
single-device — but a message sent by an account on phone A cannot reach phone B.

If you need real phone-to-phone messaging, that requires a network path. The
options, cheapest first:

| Option | What it gives you | Setup |
|---|---|---|
| **LAN / hotspot sync** | phones on the same Wi-Fi sync messages peer-to-peer | no account, no server; both phones must be on the same network and the app open |
| **Supabase** | real multi-device chat + realtime | free project, one URL + anon key |
| **Firebase** | same, but console toggles again | back to the setup that was removed |

The `Messages` table and the whole UI already exist, so any of these only means
replacing the send/receive functions in `data/TrackerDatabase.java` — the screens
do not change.

## 3. Where the data lives

`tracker.db` (SQLite), in the app's private storage:

```sql
Users     (email PK, name, department, phone, role, passwordSalt, passwordHash, createdAt)
Locations (email PK, latitude, longitude, address, updatedAt)
Session   (id=1, email)          -- keeps you signed in between launches
Messages  (id PK, sender, recipient, body, sentAt, readAt)
```

Passwords are stored as salted, 10 000-round iterated SHA-256 hashes — never in
plain text. `Session` holds the currently signed-in e-mail.

## 3b. Inspecting the database (optional)

SQLite needs **no setup** — it ships with Android and the `tracker.db` file is
created automatically on first launch. Nothing to install, no server, no account.

To look inside the data on your phone:

```bash
# 1. Plug the phone in with USB debugging enabled, then:
adb shell run-as com.example.trackingapplication \
    sqlite3 databases/tracker.db

# 2. Now you are in SQLite. Useful queries:
.headers on
.mode column
.tables
SELECT email, name, role FROM Users;
SELECT email, latitude, longitude, datetime(updatedAt/1000,'unixepoch') FROM Locations;
SELECT body, datetime(sentAt/1000,'unixepoch') FROM Messages ORDER BY sentAt DESC LIMIT 10;
.quit
```

`run-as` works because it runs the shell as your app's own user — so it can only
see your app's private files. Nothing is exposed to other apps.

**Copy the file out** (for a backup or to open it in DB Browser for SQLite):

```bash
adb exec-out run-as com.example.trackingapplication \
    cat databases/tracker.db > tracker.db
```

Then open `tracker.db` with <https://sqlitebrowser.org>.

**Reset everything** (start with no accounts):

```bash
adb shell run-as com.example.trackingapplication rm databases/tracker.db
# or on the phone: Settings -> Apps -> Tracker System -> Storage -> Clear data
```

### What is in the schema (v2)

```sql
Users     (email PK, name, department, phone, role, passwordSalt, passwordHash, createdAt)
Locations (email PK, latitude, longitude, address, updatedAt)
Session   (id=1, email)
Messages  (id PK, sender, recipient, body, sentAt, readAt)
```

Schema changes must go through `onUpgrade()` — never delete the file to "fix" a
schema change, or users lose their accounts.

## 4. Permissions

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (both requested together, as
Android 12+ requires). `INTERNET` is no longer needed by the app logic; it is
still declared only because Google Play services may use it for the geocoder.

### Background tracking

Tracking runs in a **foreground service** and keeps going with the screen off and
the app swiped away — until you stop it. Three ways to stop:

1. The **Stop** action on the persistent notification.
2. **Stop tracking** on the Location screen.
3. Turning off **Track in the background** on the employee home.

Android shows a permanent notification while it runs; that is mandatory for
background location and is also how you can always tell it is on. The
notification shows the live position and opens the app when tapped.

If the system kills the app for memory, the service restarts itself
(`START_STICKY`) and resumes until you stop it.

**Battery:** GPS requests a fix every 4 seconds while running. That is normal for
continuous tracking; stop it when you do not need it.

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
| `MessagesActivity` + `ConversationAdapter` | inbox, unread counters |
| `ChatActivity` + `ChatAdapter` | one conversation, bubbles |
| `NewMessageActivity` + `ContactAdapter` | pick a recipient |
