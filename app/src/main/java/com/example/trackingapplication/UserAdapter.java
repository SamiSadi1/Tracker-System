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
import java.util.Locale;

/** Renders one row per user: profile details plus the last saved position. */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private static final String NO_LOCATION_YET = "No location saved";

    private final List<TrackerDatabase.UserLocation> users = new ArrayList<>();

    public void setUsers(List<TrackerDatabase.UserLocation> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
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
        TrackerDatabase.UserLocation entry = users.get(position);
        TrackerDatabase.User user = entry.user;

        holder.name.setText(valueOr(user.name, user.email));
        holder.email.setText(valueOr(user.email, "—"));

        StringBuilder details = new StringBuilder();
        details.append(user.isAdmin() ? "Admin" : "Employee");
        String department = valueOr(user.department, "");
        if (!department.isEmpty()) {
            details.append(" • ").append(department);
        }
        holder.role.setText(details.toString());

        if (entry.hasLocation()) {
            StringBuilder detailsText = new StringBuilder();
            detailsText.append(String.format(Locale.US, "%.5f, %.5f",
                    entry.latitude, entry.longitude));

            String address = valueOr(entry.address, "");
            if (!address.isEmpty()) {
                detailsText.append('\n').append(address);
            }

            if (entry.updatedAt != null) {
                detailsText.append("\nUpdated ")
                        .append(DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(new Date(entry.updatedAt)));
            }

            holder.location.setText(detailsText.toString());
        } else {
            holder.location.setText(NO_LOCATION_YET);
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
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
