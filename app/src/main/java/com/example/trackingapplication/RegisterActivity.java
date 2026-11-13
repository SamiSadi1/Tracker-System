package com.example.trackingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.example.trackingapplication.data.Sync;
import com.example.trackingapplication.data.TrackerDatabase;

/** Registration screen. Backed by the on-device database (no Firebase). */
public class RegisterActivity extends AppCompatActivity {

    EditText fullName, email, password, phone, department;
    Button registerBtn, goToLogin;
    CheckBox isAdmin, isUser;
    TrackerDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = com.example.trackingapplication.data.Database.get(this);

        fullName = findViewById(R.id.registerName);
        email = findViewById(R.id.registerEmail);
        password = findViewById(R.id.registerPassword);
        department = findViewById(R.id.Department);
        phone = findViewById(R.id.registerPhone);
        registerBtn = findViewById(R.id.registerBtn);
        goToLogin = findViewById(R.id.gotoLogin);
        isAdmin = findViewById(R.id.isAdmin);
        isUser = findViewById(R.id.isUser);

        isUser.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    isAdmin.setChecked(false);
                }
            }
        });

        isAdmin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    isUser.setChecked(false);
                }
            }
        });

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(isAdmin.isChecked() || isUser.isChecked())) {
                    Toast.makeText(RegisterActivity.this,
                            "Select your Account type", Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean fieldsValid = checkField(fullName) & checkField(email)
                        & checkField(password) & checkField(phone) & checkField(department);
                if (!fieldsValid) {
                    return;
                }

                String role = isAdmin.isChecked()
                        ? TrackerDatabase.ROLE_ADMIN
                        : TrackerDatabase.ROLE_EMPLOYEE;

                registerBtn.setEnabled(false);
                Sync.authenticate(RegisterActivity.this,
                        email.getText().toString(),
                        password.getText().toString(),
                        fullName.getText().toString(),
                        department.getText().toString(),
                        phone.getText().toString(),
                        isAdmin.isChecked() ? TrackerDatabase.ROLE_ADMIN : TrackerDatabase.ROLE_EMPLOYEE,
                        true,
                        new Sync.Callback<TrackerDatabase.User>() {
                            @Override
                            public void onSuccess(TrackerDatabase.User user) {
                                registerBtn.setEnabled(true);
                                Toast.makeText(RegisterActivity.this,
                                        "Account created successfully", Toast.LENGTH_SHORT).show();
                                Class<?> home = user.isAdmin()
                                        ? AdminActivity.class : MainActivity.class;
                                startActivity(new Intent(getApplicationContext(), home));
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                registerBtn.setEnabled(true);
                                email.setError(message);
                                Toast.makeText(RegisterActivity.this,
                                        message, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        goToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                finish();
            }
        });
    }

    /** Validates one field; every field is checked, nothing is overwritten. */
    public boolean checkField(EditText textField) {
        if (textField.getText().toString().trim().isEmpty()) {
            textField.setError("Error");
            return false;
        }
        textField.setError(null);
        return true;
    }
}
