package com.example.trackingapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;

import java.util.List;

/**
 * One conversation. Messages are written straight to the on-device database, so
 * sending works with no network at all.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PARTNER = "partner_email";

    private TrackerDatabase db;
    private TrackerDatabase.User me;
    private String partnerEmail;

    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private EditText input;
    private TextView header;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = Database.get(this);
        me = db.currentUser();
        if (me == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
            finish();
            return;
        }

        partnerEmail = getIntent().getStringExtra(EXTRA_PARTNER);
        if (TextUtils.isEmpty(partnerEmail)) {
            finish();
            return;
        }

        TrackerDatabase.User partner = db.findUser(partnerEmail);
        String partnerName = partner != null ? partner.name : partnerEmail;

        header = findViewById(R.id.chatTitle);
        header.setText(partnerName);

        TextView subtitle = findViewById(R.id.chatSubtitle);
        subtitle.setText(partnerEmail + "  •  on-device messages");

        adapter = new ChatAdapter(me.email);
        recyclerView = findViewById(R.id.messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        input = findViewById(R.id.messageInput);

        Button send = findViewById(R.id.sendBtn);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
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

    @Override
    protected void onStart() {
        super.onStart();
        loadMessages();
    }

    private void loadMessages() {
        List<TrackerDatabase.Message> messages = db.conversation(me.email, partnerEmail);
        adapter.setMessages(messages);
        if (!messages.isEmpty()) {
            recyclerView.scrollToPosition(messages.size() - 1);
        }
        // Reading the thread marks the partner's messages as read.
        db.markConversationRead(me.email, partnerEmail);
    }

    private void sendMessage() {
        String body = input.getText().toString().trim();
        if (body.isEmpty()) {
            return;
        }
        db.sendMessage(me.email, partnerEmail, body);
        input.setText("");
        loadMessages();
    }
}
