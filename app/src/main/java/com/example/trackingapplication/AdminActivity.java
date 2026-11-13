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

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;

import java.util.List;

/**
 * Admin screen: lists every registered user and the last position each one
 * saved to the on-device database (no Firebase).
 */
public class AdminActivity extends AppCompatActivity {

    private TrackerDatabase db;
    private UserAdapter adapter;
    private TextView summaryText;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        db = Database.get(this);

        TrackerDatabase.User currentUser = db.currentUser();
        if (currentUser == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
            finish();
            return;
        }
        if (!currentUser.isAdmin()) {
            // Employees have no business on the admin screen.
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            finish();
            return;
        }

        summaryText = findViewById(R.id.summaryText);
        emptyText = findViewById(R.id.emptyText);

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
                db.signOut();
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadUsers();
    }

    private void loadUsers() {
        List<TrackerDatabase.UserLocation> users = db.usersWithLocations();

        adapter.setUsers(users);

        emptyText.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);

        int located = 0;
        for (TrackerDatabase.UserLocation user : users) {
            if (user.hasLocation()) {
                located++;
            }
        }
        summaryText.setText(users.size() + " users • " + located + " with a saved position");
    }
}
