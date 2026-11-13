package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.trackingapplication.data.TrackerDatabase;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Inbox rows: partner name, last message, time and unread badge. */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClick {
        void onClick(TrackerDatabase.Conversation conversation);
    }

    /** Plain wrapper so the list can be swapped without touching the DB. */
    public static class Row {
        public final TrackerDatabase.Conversation conversation;

        public Row(TrackerDatabase.Conversation conversation) {
            this.conversation = conversation;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnConversationClick listener;

    public ConversationAdapter(OnConversationClick listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final TrackerDatabase.Conversation conversation = rows.get(position).conversation;

        holder.name.setText(conversation.otherName);
        holder.preview.setText(conversation.lastMessage);
        holder.time.setText(DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(conversation.lastAt)));

        if (conversation.unreadCount > 0) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(String.valueOf(conversation.unreadCount));
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView preview;
        final TextView time;
        final TextView badge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.conversationName);
            preview = itemView.findViewById(R.id.conversationPreview);
            time = itemView.findViewById(R.id.conversationTime);
            badge = itemView.findViewById(R.id.unreadBadge);
        }
    }
}
