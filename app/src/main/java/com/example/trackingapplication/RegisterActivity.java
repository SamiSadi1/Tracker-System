package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText fullName,email,password,phone,department;
    Button registerBtn,goToLogin;
    boolean valid = true;
    FirebaseAuth fAuth;
    FirebaseFirestore fstore;
    CheckBox isAdmin,isUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        fAuth = FirebaseAuth.getInstance();
        fstore = FirebaseFirestore.getInstance();

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
                if (buttonView.isChecked()){

                    isAdmin.setChecked(false);
                }
            }
        });

        isAdmin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()){

                    isUser.setChecked(false);
                }
            }
        });

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkField(fullName);
                checkField(email);
                checkField(password);
                checkField(phone);

                if (!(isAdmin.isChecked() || isUser.isChecked())){

                    Toast.makeText(RegisterActivity.this, "Select your Account type", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (valid){

                    fAuth.createUserWithEmailAndPassword(email.getText().toString(),password.getText().toString()).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                        @Override
                        public void onSuccess(AuthResult authResult) {


                            FirebaseUser user = fAuth.getCurrentUser();
                            Toast.makeText(RegisterActivity.this, "Account Create Successfully", Toast.LENGTH_SHORT).show();
                            DocumentReference df = fstore.collection("Users").document(user.getEmail());
                            Map<String,Object> userinfo = new HashMap<>();
                            userinfo.put("Name",fullName.getText().toString());
                            userinfo.put("E-mail",email.getText().toString());
                            userinfo.put("Department",department.getText().toString());
                            userinfo.put("ContactNo",phone.getText().toString());


                            if (isAdmin.isChecked()){

                                userinfo.put("isAdmin","1");
                            }

                            if (isUser.isChecked()){

                                userinfo.put("isUser","1");
                            }
                            userinfo.put("Created",new Timestamp(new Date()));

                            df.set(userinfo);

                            if (isAdmin.isChecked()){

                                startActivity(new Intent(getApplicationContext(),AdminActivity.class));
                                finish();
                            }

                            if (isUser.isChecked()){

                                startActivity(new Intent(getApplicationContext(),LoginActivity.class));
                                finish();
                            }


                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(RegisterActivity.this, "Account Create unsuccessfully"+e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });


                }
            }
        });

        goToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startActivity(new Intent(getApplicationContext(),LoginActivity.class));
                finish();
            }
        });
    }
    public boolean checkField(EditText textField){
        if(textField.getText().toString().isEmpty()){
            textField.setError("Error");
            valid = false;
        }else {
            valid = true;
        }

        return valid;


    }
}