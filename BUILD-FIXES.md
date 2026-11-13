# Build fix report — Tracker-System (`com.example.trackingapplication`)

Branch: `arena/60ad3ea6-tracker-system`
Status: **builds successfully** — `Android CI` run
[43359076772](https://github.com/SamiSadi1/Tracker-System/actions/runs/43359076772):
`assembleDebug` ✅, `testDebugUnitTest` ✅, debug APK uploaded (9.6 MB).

---

## 1. Why the project could not build

All of the below were blocking a build before a single line of Java was compiled.

| # | Error | Symptom |
|---|-------|---------|
| 1 | `plugins { id 'com.android.application' }` applied in `app/build.gradle`, but the AGP classpath was only declared in the root `buildscript` block | `Plugin [id: 'com.android.application'] was not found` |
| 2 | `jcenter()` used in both repositories blocks | repository is dead/read-only → dependency resolution fails |
| 3 | `gradle-6.7.1` wrapper with AGP 4.2.2 | AGP 4.2.2 needs Gradle 6.7.1–6.9 **and** JDK 8–11; modern JDK 17/21 environments fail |
| 4 | `gradlew` committed as mode `100644` | `./gradlew: Permission denied` on Linux/macOS/CI |
| 5 | Legacy DSL: `compileSdkVersion`, `buildToolsVersion "30.0.3"`, no `namespace` | build-tools 30.0.3 not installed in any current SDK; AGP 8 requires `namespace` |
| 6 | 2021-era dependencies only resolvable from jcenter, plus unused ones (`dexter`, `degreenrobot:eventbus`, `play-services-maps`, `firebase-database`, `org.jetbrains:annotations`) | resolution failures, and maps/billing keys belong to another project |
| 7 | No `android:exported` on any activity with `targetSdk` 31+ | AGP manifest-merger **error** |
| 8 | `INTERNET` permission missing | Firebase Auth/Firestore fail at runtime (silent in build) |

## 2. Rebuild — build system

| Item | Before | After |
|------|--------|-------|
| Gradle wrapper | 6.7.1 | **8.9** (new `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `chmod +x`) |
| Android Gradle Plugin | 4.2.2 (buildscript only) | **8.7.3** via `plugins {}` block |
| Google services plugin | 4.3.8 | **4.4.2** |
| Repositories | `google()`, `jcenter()` | `google()`, `mavenCentral()` (in `settings.gradle`) |
| compileSdk / targetSdk | 30 / 30 | **34 / 34** (minSdk stays 23) |
| buildToolsVersion | pinned `30.0.3` | removed (AGP picks its default) |
| namespace | none (used `package` in manifest) | `com.example.trackingapplication` |
| Java | 1.8 | **17** |
| `gradle.properties` | — | added `android.nonTransitiveRClass=true` |

Dependencies were consolidated onto the Firebase BoM so versions stay compatible:

```gradle
implementation platform('com.google.firebase:firebase-bom:33.7.0')
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.android.gms:play-services-location:21.3.0'
```

## 3. Rebuild — manifest

- `android:exported="true"` on `LoginActivity` (launcher), `false` on the other four.
- Added `INTERNET`, `ACCESS_COARSE_LOCATION` (Android 12+ requires fine **and** coarse to be requested together), kept `ACCESS_FINE_LOCATION`.
- `package` attribute removed (owned by `namespace` in Gradle now).
- Activity order tidied; launcher entry unchanged.

## 4. Real bugs found while rebuilding

**`LoginActivity.onStart()` — signed-in users were never routed.**
`RegisterActivity` writes the profile as `Users/<e-mail>`, but `onStart()` read
`Users/<uid>`. The document never existed, so every already-signed-in launch sat
on the login screen. Now both use the e-mail key, and a profile with no role flag
signs out instead of hanging.

**`RegisterActivity.checkField()` — form validation was broken.**
Every call overwrote the shared `valid` field, so filling *any single* field made
the entire form pass. Now each field's result is combined
(`checkField(a) & checkField(b) & …`) and `Department` is validated too.

**`LocationActivity` — three crash paths.**
- `if (address != null)` tested the address **String** (never null) instead of the
  geocoder result list → `IndexOutOfBoundsException` on an empty list.
- `fAuth.getCurrentUser().getEmail()` could NPE when not signed in.
- Snapshot callback dereferenced a possibly-null `DocumentSnapshot`.
  Also guarded the fused client in `onStop()` and suppressed the deprecated
  synchronous geocoder call.

**`activity_main.xml` — the menu was invisible.**
The clickable "Location" `TextView` had `match_parent` height, so it covered the
whole screen. It is now `wrap_content` with a real ripple, and the click is wired
in `MainActivity` (`setOnClickListener`) instead of `android:onClick`, so renaming
the method can no longer break it at runtime. The legacy `location(View)` method is
kept for compatibility.

## 5. CI added

`.github/workflows/android.yml` — JDK 17, Android SDK (runner-provided with a
command-line-tools fallback), `assembleDebug`, `testDebugUnitTest`, and upload of
the debug APK. Runs on every push and pull request.


## 6. Runtime features implemented after the rebuild

The build being fixed did **not** make the app actually track: `getLastLocation()`
(the only method writing to Firestore) was commented out at both call sites and
the live-update callback only logged to Logcat. `AdminActivity` was an empty
`ConstraintLayout`. Both are now implemented:

**`LocationActivity` (rewritten)**
- Saves `Location/{uid}` directly from the location callback: `Uid`, `E-mail`,
  `Latitude`, `Longitude`, `Address`, `Updated` (`serverTimestamp`).
- First fix saves immediately, later updates are throttled to every 15 s.
- Reverse geocoding runs on a background `ExecutorService` (it does network/disk
  I/O), with a coordinates-only fallback when no geocoder backend is available.
- Migrated to `LocationRequest.Builder` + `Priority` (the old setters are deprecated).
- Requests FINE **and** COARSE together, as Android 12+ requires.
- Inline status text for the current fix and the save result, plus explicit
  messages for denied permission / unavailable provider (previously silent).

**`AdminActivity` + `UserAdapter`**
- Lists every document under `Users/` with name, e-mail, role and department,
  and joins `Location/{uid}` for the last known position and its timestamp.
- Empty state, error banner, Refresh and Sign out.

**Supporting changes**
- `firestore.rules` — the default rules deny all reads/writes, which would make
  both saving and the admin list fail. Rules allow a user to read/write their own
  profile and position, and an admin (profile carrying `isAdmin`) to read all.
- `README.md` — Firebase setup steps (enable Email/Password, publish rules) and
  the data model.
- `MainActivity` gains a Sign out button.

**Key consistency:** profiles are keyed by e-mail (`Users/{email}`), positions by
uid (`Location/{uid}`) — writer and reader both use uid, verified.
