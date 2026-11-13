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

        List<TrackerDatabase.User> contacts = new ArrayList<>();
        for (TrackerDatabase.User user : db.allUsers()) {
            if (!user.email.equalsIgnoreCase(me.email)) {
                contacts.add(user);
            }
        }

        ContactAdapter adapter = new ContactAdapter(contacts,
                new ContactAdapter.OnContactClick() {
                    @Override
                    public void onClick(TrackerDatabase.User user) {
                        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
                        intent.putExtra(ChatActivity.EXTRA_PARTNER, user.email);
                        startActivity(intent);
                        finish();
                    }
                });

        RecyclerView recyclerView = findViewById(R.id.contactList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        Button back = findViewById(R.id.backBtn);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
