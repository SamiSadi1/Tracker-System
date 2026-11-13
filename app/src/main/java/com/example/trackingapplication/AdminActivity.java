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

import android.os.Handler;
import android.os.Looper;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.Sync;
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

    /** Live refresh interval for the admin view. */
    private static final long LIVE_INTERVAL_MS = 5000L;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private Runnable liveTask;

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

        final Button messages = findViewById(R.id.messagesBtn);
        messages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), MessagesActivity.class));
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
        startLiveUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLiveUpdates();
    }

    /** Refreshes the board every few seconds so positions look "live". */
    private void startLiveUpdates() {
        if (!Sync.isCloudEnabled()) {
            return;
        }
        liveTask = new Runnable() {
            @Override
            public void run() {
                loadUsers();
                liveHandler.postDelayed(this, LIVE_INTERVAL_MS);
            }
        };
        liveHandler.postDelayed(liveTask, LIVE_INTERVAL_MS);
    }

    private void stopLiveUpdates() {
        if (liveTask != null) {
            liveHandler.removeCallbacks(liveTask);
            liveTask = null;
        }
    }

    private void loadUsers() {
        final TrackerDatabase.User me = db.currentUser();
        int unread = me == null ? 0 : db.totalUnread(me.email);
        Button messagesButton = findViewById(R.id.messagesBtn);
        messagesButton.setText(unread > 0
                ? "Messages (" + unread + " new)"
                : getString(R.string.open_messages));

        Sync.loadAllLocations(this,
                new Sync.Callback<List<TrackerDatabase.UserLocation>>() {
                    @Override
                    public void onSuccess(List<TrackerDatabase.UserLocation> users) {
                        adapter.setUsers(users);
                        emptyText.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);

                        int located = 0;
                        for (TrackerDatabase.UserLocation user : users) {
                            if (user.hasLocation()) {
                                located++;
                            }
                        }
                        summaryText.setText(users.size() + " users • " + located
                                + " with a saved position"
                                + (Sync.isCloudEnabled() ? " • live" : " • on this device"));
                    }

                    @Override
                    public void onError(String message) {
                        summaryText.setText(message);
                    }
                });
    }

}
