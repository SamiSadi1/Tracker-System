package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    EditText email,password;
    Button loginBtn,gotoRegister;
    FirebaseAuth fAuth;
    FirebaseFirestore fstore;
    boolean valid = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        email = findViewById(R.id.loginEmail);
        password = findViewById(R.id.loginPassword);
        fAuth = FirebaseAuth.getInstance();
        fstore = FirebaseFirestore.getInstance();

        loginBtn = findViewById(R.id.loginBtn);
        gotoRegister = findViewById(R.id.gotoRegister);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                checkField(email);
                checkField(password);

                if (valid){

                    fAuth.signInWithEmailAndPassword(email.getText().toString(),password.getText().toString()).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                        @Override
                        public void onSuccess(AuthResult authResult) {

                            Toast.makeText(LoginActivity.this, "Login Sucessfully", Toast.LENGTH_SHORT).show();
                            checkUserAccesslevel(authResult.getUser().getEmail());


                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {

                            Toast.makeText(LoginActivity.this, "EORRR!!!!"+e.getMessage(), Toast.LENGTH_SHORT).show();

                        }
                    });
                }

            }
        });
    }
    private void checkUserAccesslevel(String email) {

        DocumentReference df = fstore.collection("Users").document(email);
        df.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {

                Log.d("TAG","On success"+documentSnapshot.getData());
                //identify the type of user
                if (documentSnapshot.getString("isAdmin") != null) {
                    //user is Admin
                    startActivity(new Intent(getApplicationContext(), AdminActivity.class));
                    finish();
                }
                if(documentSnapshot.getString("isUser") != null){
                    //user is Client
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
                    finish();
                }
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

    public void createaccount(View view) {

        startActivity(new Intent(getApplicationContext(),RegisterActivity.class));
        finish();
    }
    @Override
    protected void onStart() {
        super.onStart();

        if(FirebaseAuth.getInstance().getCurrentUser() != null){

            DocumentReference df = FirebaseFirestore.getInstance().collection("Users").document(FirebaseAuth.getInstance().getCurrentUser().getUid());
            df.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {

                    if(documentSnapshot.getString("isAdmin") != null){

                        startActivity(new Intent(getApplicationContext(),AdminActivity.class));
                        finish();
                    }

                    if(documentSnapshot.getString("isUser") != null){

                        startActivity(new Intent(getApplicationContext(),MainActivity.class));
                        finish();
                    }



                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(getApplicationContext(),LoginActivity.class));
                    finish();

                }
            });

        }
    }
    public void forgetpassword(View v) {


        final EditText mailtext = new EditText(v.getContext());
        final AlertDialog.Builder passwordreset = new AlertDialog.Builder(v.getContext());
        passwordreset.setTitle("Reset password");
        passwordreset.setMessage("ENTER FOR GMAIL FOR RESET FOR PASSWORD");
        passwordreset.setView(mailtext);

        passwordreset.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                String resetmail = mailtext.getText().toString().trim();
                fAuth.sendPasswordResetEmail(resetmail).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {

                        Toast.makeText(LoginActivity.this, "Reset link sent your email!!!", Toast.LENGTH_SHORT).show();

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                        Toast.makeText(LoginActivity.this, "Error!!!Rest link not sent"+e.getMessage(), Toast.LENGTH_SHORT).show();

                    }
                });



            }
        });
        passwordreset.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        passwordreset.create().show();
    }


}