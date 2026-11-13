package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.trackingapplication.data.TrackerDatabase;

import java.util.ArrayList;
import java.util.List;

/** Simple list of accounts available to message. */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

    public interface OnContactClick {
        void onClick(TrackerDatabase.User user);
    }

    private final List<TrackerDatabase.User> contacts = new ArrayList<>();
    private final OnContactClick listener;

    public ContactAdapter(List<TrackerDatabase.User> contacts, OnContactClick listener) {
        this.contacts.addAll(contacts);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final TrackerDatabase.User user = contacts.get(position);
        holder.name.setText(user.name);
        holder.detail.setText(user.email + "  •  "
                + (user.isAdmin() ? "Admin" : "Employee"));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.contactName);
            detail = itemView.findViewById(R.id.contactDetail);
        }
    }
}
