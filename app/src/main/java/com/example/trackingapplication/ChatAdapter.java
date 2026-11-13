package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.trackingapplication.data.TrackerDatabase;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Chat bubbles: my messages right-aligned, the partner's left-aligned. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private static final int MINE = 1;
    private static final int THEIRS = 0;

    private final List<TrackerDatabase.Message> messages = new ArrayList<>();
    private final String myEmail;

    public ChatAdapter(String myEmail) {
        this.myEmail = myEmail;
    }

    public void setMessages(List<TrackerDatabase.Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).sender.equalsIgnoreCase(myEmail) ? MINE : THEIRS;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackerDatabase.Message message = messages.get(position);
        boolean mine = message.sender.equalsIgnoreCase(myEmail);

        holder.body.setText(message.body);
        holder.body.setBackgroundResource(mine
                ? R.drawable.bg_bubble_mine
                : R.drawable.bg_bubble_theirs);

        holder.time.setText(DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(message.sentAt)));

        // Align the bubble to the correct side.
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) holder.bubble.getLayoutParams();
        params.gravity = mine ? Gravity.END : Gravity.START;
        holder.bubble.setLayoutParams(params);

        LinearLayout.LayoutParams timeParams =
                (LinearLayout.LayoutParams) holder.time.getLayoutParams();
        timeParams.gravity = mine ? Gravity.END : Gravity.START;
        holder.time.setLayoutParams(timeParams);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout bubble;
        final TextView body;
        final TextView time;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.bubble);
            body = itemView.findViewById(R.id.messageBody);
            time = itemView.findViewById(R.id.messageTime);
        }
    }
}
