package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

/**
 * Admin screen: lists every registered user and the last position each one
 * saved to Firestore -> Location/{uid}.
 */
public class AdminActivity extends AppCompatActivity {

    private FirebaseFirestore fstore;
    private UserAdapter adapter;
    private TextView emptyText;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        fstore = FirebaseFirestore.getInstance();

        emptyText = findViewById(R.id.emptyText);
        errorText = findViewById(R.id.errorText);

        adapter = new UserAdapter();
        RecyclerView recyclerView = findViewById(R.id.userList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        Button refresh = findViewById(R.id.refreshBtn);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadUsers();
            }
        });

        Button signOut = findViewById(R.id.signOutBtn);
        signOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                finish();
            }
        });

        loadUsers();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadUsers();
    }

    private void loadUsers() {
        showError(null);

        fstore.collection("Users").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshots) {
                        final List<DocumentSnapshot> users = snapshots.getDocuments();
                        adapter.setUsers(users);
                        emptyText.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
                        loadLocations(users);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        showError("Could not load users: " + e.getMessage());
                    }
                });
    }

    /** Loads Location/{uid} for every user currently listed. */
    private void loadLocations(List<DocumentSnapshot> users) {
        for (final DocumentSnapshot user : users) {
            final String uid = user.getId();
            fstore.collection("Location").document(uid).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot location) {
                            if (location.exists()) {
                                adapter.setLocation(uid, location);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            showError("Could not load location for " + uid + ": " + e.getMessage());
                        }
                    });
        }
    }

    private void showError(String message) {
        if (errorText == null) {
            return;
        }
        if (message == null) {
            errorText.setVisibility(View.GONE);
        } else {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
