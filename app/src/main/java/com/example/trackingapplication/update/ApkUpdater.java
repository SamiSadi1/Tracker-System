package com.example.trackingapplication.update;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.trackingapplication.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Self-update for a sideloaded APK.
 *
 * Asks the GitHub API for the APK attached to the "debug-latest" release, compares
 * its versionCode with the installed one and, if it is newer, downloads it and
 * hands it to the system installer.
 *
 * Note: this is the same mechanism every non-Play-Store app uses. Android requires
 * a confirmation tap for the actual install (REQUEST_INSTALL_PACKAGES); apps
 * cannot silently install updates — only the Play Store can.
 */
public final class ApkUpdater {

    private static final String TAG = "ApkUpdater";

    private static final String RELEASES_API =
            "https://api.github.com/repos/SamiSadi1/Tracker-System/releases/tags/debug-latest";

    private static final String APK_ASSET_NAME = "TrackerSystem-debug.apk";

    /**
     * Matches both JSON fields and plain release notes. GitHub release bodies are
     * shipped as "versionCode: N" (unquoted, inside a JSON string), so the quotes
     * must be optional. The release title "… — build N" is the fallback.
     */
    private static final Pattern VERSION_CODE_PATTERN =
            Pattern.compile("\\bversionCode\\b\"?\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUILD_NUMBER_PATTERN =
            Pattern.compile("\\bbuild\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 120L;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;

    public ApkUpdater(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /** Callback so a screen can show progress. All methods run on the main thread. */
    public interface Listener {
        void onChecking();

        void onUpToDate();

        void onDownloading();

        void onInstallerLaunched();

        void onError(String message);
    }

    public int installedVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    /**
     * Checks the latest published build and, when it is newer, downloads and
     * launches the installer.
     */
    public void checkForUpdate(final Listener listener) {
        listener.onChecking();

        // Android 8+ needs the user to allow installs from this app once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canRequestPackageInstalls()) {
            openInstallPermissionSettings(listener);
            return;
        }

        Request request = new Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TrackerSystem-Updater")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                postError(listener, "Could not reach GitHub: " + e.getMessage());
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        postError(listener, "GitHub returned HTTP " + response.code());
                        return;
                    }

                    String json = response.body().string();
                    String downloadUrl = findAssetUrl(json);

                    if (downloadUrl == null) {
                        postError(listener, "No APK found in the latest release.");
                        return;
                    }

                    // The published APK is named <app>-<versionCode>.apk. If the release
                    // does not say so, fall back to comparing the release text.
                    int remoteVersion = parseVersionCode(json);
                    if (remoteVersion > 0 && remoteVersion <= BuildConfig.VERSION_CODE) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onUpToDate();
                            }
                        });
                        return;
                    }

                    download(downloadUrl, listener, remoteVersion);

                } catch (IOException e) {
                    postError(listener, "Could not read the release info: " + e.getMessage());
                }
            }
        });
    }

    private void download(String url, final Listener listener, final int remoteVersion) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onDownloading();
            }
        });

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                postError(listener, "Download failed: " + e.getMessage());
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                // writeToFile() reports its own IO failures; nothing here throws
                // IOException, so no try/catch is needed.
                if (!response.isSuccessful() || response.body() == null) {
                    postError(listener, "Download failed: HTTP " + response.code());
                    return;
                }

                File apk = new File(cacheDirectory(),
                        String.format(Locale.US, "update-%d.apk",
                                remoteVersion > 0 ? remoteVersion : System.currentTimeMillis()));

                if (!writeToFile(response.body(), apk)) {
                    postError(listener, "Could not save the downloaded APK.");
                    return;
                }

                install(apk, listener);
            }
        });
    }

    private boolean writeToFile(ResponseBody body, File target) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = body.byteStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed writing " + target, e);
            return false;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            // close() itself does not throw.
            body.close();
        }
    }

    private void install(File apk, final Listener listener) {
        Uri apkUri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".updates", apk);

        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onInstallerLaunched();
                }
            });
        } catch (Exception e) {
            postError(listener, "Could not open the installer: " + e.getMessage());
        }
    }

    private File cacheDirectory() {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create " + dir);
        }
        return dir;
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    private boolean canRequestPackageInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return context.getPackageManager().canRequestPackageInstalls();
    }

    private void openInstallPermissionSettings(final Listener listener) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context,
                        "Allow installing updates from this app, then tap Check for updates again.",
                        Toast.LENGTH_LONG).show();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                postError(listener, "Open Settings -> Special app access -> Install unknown apps.");
            }
        }
    }

    /** True when the app may talk to the network at all. */
    public static boolean hasNetworkPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------
    // Release parsing
    // ------------------------------------------------------------------

    /**
     * Finds the download URL of the APK asset inside a GitHub release payload.
     * Returns null when the release has no APK.
     */
    static String findAssetUrl(String releaseJson) {
        Matcher matcher = Pattern.compile(
                "\"browser_download_url\"\\s*:\\s*\"([^\"]*" + Pattern.quote(APK_ASSET_NAME) + ")\"")
                .matcher(releaseJson);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Reads the versionCode the build was given (the APK is published as
     * TrackerSystem-debug.apk and the release body lists "versionCode": N).
     */
    static int parseVersionCode(String releaseJson) {
        Matcher matcher = VERSION_CODE_PATTERN.matcher(releaseJson);
        if (matcher.find()) {
            int parsed = toInt(matcher.group(1));
            if (parsed > 0) {
                return parsed;
            }
        }
        // Fallback: the release title is "Tracker System — build N".
        Matcher build = BUILD_NUMBER_PATTERN.matcher(releaseJson);
        if (build.find()) {
            int parsed = toInt(build.group(1));
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0;
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void postError(final Listener listener, final String message) {
        Log.w(TAG, message);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(message);
            }
        });
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
