package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentSnapshot;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders one row per user: profile details plus the last saved position. */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private static final String NO_LOCATION_YET = "No location saved";

    private final List<DocumentSnapshot> users = new ArrayList<>();
    private final Map<String, DocumentSnapshot> locations = new HashMap<>();

    public void setUsers(List<DocumentSnapshot> newUsers) {
        users.clear();
        users.addAll(newUsers);
        // Drop locations for users that disappeared.
        Map<String, DocumentSnapshot> kept = new HashMap<>();
        for (DocumentSnapshot user : users) {
            DocumentSnapshot location = locations.get(user.getId());
            if (location != null) {
                kept.put(user.getId(), location);
            }
        }
        locations.clear();
        locations.putAll(kept);
        notifyDataSetChanged();
    }

    public void setLocation(String uid, DocumentSnapshot location) {
        locations.put(uid, location);
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(uid)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        DocumentSnapshot user = users.get(position);
        DocumentSnapshot location = locations.get(user.getId());

        String name = valueOr(user.getString("Name"), user.getId());
        String email = valueOr(user.getString("E-mail"), "—");
        String role = roleOf(user);
        String department = valueOr(user.getString("Department"), "");

        holder.name.setText(name);
        holder.email.setText(email);

        StringBuilder details = new StringBuilder();
        details.append(role);
        if (!department.isEmpty()) {
            details.append(" • ").append(department);
        }
        holder.role.setText(details.toString());

        if (location != null && location.exists()) {
            Double lat = location.getDouble("Latitude");
            Double lng = location.getDouble("Longitude");
            String address = valueOr(location.getString("Address"), "");

            StringBuilder position = new StringBuilder();
            if (lat != null && lng != null) {
                position.append(String.format(Locale.US, "%.5f, %.5f", lat, lng));
            } else {
                position.append("Position unavailable");
            }
            if (!address.isEmpty()) {
                position.append('\n').append(address);
            }

            Date updated = location.getDate("Updated");
            if (updated != null) {
                position.append("\nUpdated ")
                        .append(DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.SHORT).format(updated));
            }

            holder.location.setText(position.toString());
        } else {
            holder.location.setText(NO_LOCATION_YET);
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    private static String roleOf(DocumentSnapshot user) {
        if (user.getString("isAdmin") != null) {
            return "Admin";
        }
        if (user.getString("isUser") != null) {
            return "Employee";
        }
        return "No role";
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView email;
        final TextView role;
        final TextView location;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.userName);
            email = itemView.findViewById(R.id.userEmail);
            role = itemView.findViewById(R.id.userRole);
            location = itemView.findViewById(R.id.userLocation);
        }
    }
}
