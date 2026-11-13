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

import java.util.ArrayList;
import java.util.List;

/**
 * Inbox: one row per conversation, newest first, with unread counters.
 *
 * Offline messaging — messages live in the on-device database. Accounts on the
 * same phone can talk to each other; see README for the multi-device caveat.
 */
public class MessagesActivity extends AppCompatActivity {

    private TrackerDatabase db;
    private TrackerDatabase.User me;
    private ConversationAdapter adapter;
    private TextView emptyText;
    private TextView summaryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        db = Database.get(this);
        me = db.currentUser();
        if (me == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
            finish();
            return;
        }

        emptyText = findViewById(R.id.emptyText);
        summaryText = findViewById(R.id.summaryText);

        adapter = new ConversationAdapter(new ConversationAdapter.OnConversationClick() {
            @Override
            public void onClick(TrackerDatabase.Conversation conversation) {
                openChat(conversation.otherEmail);
            }
        });

        RecyclerView recyclerView = findViewById(R.id.conversationList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        Button newChat = findViewById(R.id.newChatBtn);
        newChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), NewMessageActivity.class));
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
        load();
    }

    private void load() {
        List<TrackerDatabase.Conversation> conversations = db.conversationsFor(me.email);

        List<ConversationAdapter.Row> rows = new ArrayList<>();
        int totalUnread = 0;
        for (TrackerDatabase.Conversation conversation : conversations) {
            rows.add(new ConversationAdapter.Row(conversation));
            totalUnread += conversation.unreadCount;
        }

        adapter.setRows(rows);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        summaryText.setText(totalUnread == 0
                ? "No unread messages"
                : totalUnread + (totalUnread == 1 ? " unread message" : " unread messages"));
    }

    private void openChat(String partnerEmail) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PARTNER, partnerEmail);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        load();
    }
}
