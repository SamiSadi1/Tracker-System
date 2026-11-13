package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trackingapplication.data.TrackerDatabase;
import com.example.trackingapplication.update.ApkUpdater;

/** Sign-in screen. Backed by the on-device database (no Firebase). */
public class LoginActivity extends AppCompatActivity {

    EditText email, password;
    Button loginBtn, gotoRegister;
    TrackerDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = com.example.trackingapplication.data.Database.get(this);

        email = findViewById(R.id.loginEmail);
        password = findViewById(R.id.loginPassword);
        loginBtn = findViewById(R.id.loginBtn);
        gotoRegister = findViewById(R.id.gotoRegister);

        TextView versionText = findViewById(R.id.versionText);
        versionText.setText(getString(R.string.version_label,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        Button checkUpdateBtn = findViewById(R.id.checkUpdateBtn);
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdates();
            }
        });

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkField(email) || !checkField(password)) {
                    return;
                }
                try {
                    TrackerDatabase.User user =
                            db.login(email.getText().toString(), password.getText().toString());
                    Toast.makeText(LoginActivity.this,
                            "Login successful", Toast.LENGTH_SHORT).show();
                    openHomeFor(user);
                } catch (TrackerDatabase.LoginException e) {
                    Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Already signed in (session persists across launches) -> go straight to the home screen.
        TrackerDatabase.User user = db.currentUser();
        if (user != null) {
            openHomeFor(user);
        }
    }

    private void openHomeFor(TrackerDatabase.User user) {
        Class<?> home = user.isAdmin() ? AdminActivity.class : MainActivity.class;
        startActivity(new Intent(getApplicationContext(), home));
        finish();
    }

    public boolean checkField(EditText textField) {
        if (textField.getText().toString().trim().isEmpty()) {
            textField.setError("Required");
            return false;
        }
        textField.setError(null);
        return true;
    }

    /** Checks the published build and installs it when it is newer. */
    private void checkForUpdates() {
        new ApkUpdater(this).checkForUpdate(new ApkUpdater.Listener() {
            @Override
            public void onChecking() {
                Toast.makeText(LoginActivity.this,
                        "Checking for updates…", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUpToDate() {
                Toast.makeText(LoginActivity.this,
                        "You already have the latest version.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDownloading() {
                Toast.makeText(LoginActivity.this,
                        "Downloading the new version…", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInstallerLaunched() {
                Toast.makeText(LoginActivity.this,
                        "Tap Install to finish the update.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void createaccount(View view) {
        startActivity(new Intent(getApplicationContext(), RegisterActivity.class));
        finish();
    }

    /**
     * There is no mail server without a backend, so this asks for the e-mail and a
     * new password directly and updates the local account.
     */
    public void forgetpassword(View v) {
        final EditText resetMail = new EditText(v.getContext());
        resetMail.setHint("Email Address");
        final EditText newPassword = new EditText(v.getContext());
        newPassword.setHint("New Password");

        final android.widget.LinearLayout container = new android.widget.LinearLayout(v.getContext());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.addView(resetMail);
        container.addView(newPassword);

        final AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
        builder.setTitle("Reset password");
        builder.setMessage("Enter your e-mail and choose a new password.");
        builder.setView(container);

        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String mail = resetMail.getText().toString().trim();
                String pass = newPassword.getText().toString();
                if (mail.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(LoginActivity.this,
                            "E-mail and new password are required.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (db.resetPassword(mail, pass)) {
                    Toast.makeText(LoginActivity.this,
                            "Password updated. You can sign in now.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this,
                            "No account found for " + mail, Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // dismissed
            }
        });

        builder.create().show();
    }
}
