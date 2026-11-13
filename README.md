# Tracker-System — running the app

## 1. Build

The APK is built by CI on every push (`.github/workflows/android.yml`).
Download it from **GitHub → Actions → latest green run → Artifacts → app-debug**,
or build locally with Android Studio (JDK 17) / `./gradlew assembleDebug`.

Result: `app/build/outputs/apk/debug/app-debug.apk`

## 2. Firebase setup (required — the app talks to Firebase Auth + Firestore)

The repo ships with `google-services.json` for project **`tracking-9a64f`**.
If you use your own project, replace `app/google-services.json` with yours and
make sure the package name is still `com.example.trackingapplication`.

### Enable these in the Firebase console

| Product | Setting |
|---|---|
| Authentication | Sign-in method → **Email/Password → Enable** |
| Firestore Database | Create the database (production or test mode) |
| Firestore → Rules | Paste `firestore.rules` from this repo and **Publish** |

> Without the rules in step 2 the app looks broken: the default rules deny every
> read and write, so saving a location and loading the admin list both fail.

## 3. What works

| Screen | Behaviour |
|---|---|
| `LoginActivity` (launcher) | Email/password sign-in, "forget password" sends a reset mail, signed-in users are routed by their stored role |
| `RegisterActivity` | Creates the account, stores the profile in `Users/{email}`, choose Admin or Employee |
| `MainActivity` (employee) | Tap **Location** to start tracking; **Sign out** |
| `LocationActivity` | Requests permission, then saves your position to `Location/{uid}` immediately and then at most every 15 s (lat/lng + resolved address + server timestamp) |
| `AdminActivity` | Lists every registered user with role, department and their last saved position; **Refresh**, **Sign out** |

## 4. Permissions

`INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (both are requested
together, as Android 12+ requires).

Tracking only runs while `LocationActivity` is open (`onStart` → `onStop`).
For background tracking you would need a foreground service — not implemented.

## 5. Firestore data model

```
Users/{email}    Name, E-mail, Department, ContactNo, isAdmin?/isUser?, Created
Location/{uid}   Uid, E-mail, Latitude, Longitude, Address, Updated (serverTimestamp)
```

Profiles are keyed by **e-mail** (registration and login both use that key), positions
are keyed by **uid**.
