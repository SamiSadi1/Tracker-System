package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.Sync;
import com.example.trackingapplication.data.TrackerDatabase;

import java.util.ArrayList;
import java.util.List;

/** Pick someone to message. Lists every account except the signed-in one. */
public class NewMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_message);

        TrackerDatabase db = Database.get(this);
        TrackerDatabase.User me = db.currentUser();
        if (me == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
            finish();
            return;
        }

        final RecyclerView recyclerView = findViewById(R.id.contactList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Sync.loadContacts(this, me.email,
                new Sync.Callback<List<TrackerDatabase.User>>() {
                    @Override
                    public void onSuccess(List<TrackerDatabase.User> contacts) {
                        recyclerView.setAdapter(new ContactAdapter(contacts,
                                new ContactAdapter.OnContactClick() {
                                    @Override
                                    public void onClick(TrackerDatabase.User user) {
                                        Intent intent = new Intent(getApplicationContext(),
                                                ChatActivity.class);
                                        intent.putExtra(ChatActivity.EXTRA_PARTNER, user.email);
                                        startActivity(intent);
                                        finish();
                                    }
                                }));
                    }

                    @Override
                    public void onError(String message) {
                        android.widget.Toast.makeText(NewMessageActivity.this,
                                message, android.widget.Toast.LENGTH_LONG).show();
                    }
                });

        Button back = findViewById(R.id.backBtn);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
