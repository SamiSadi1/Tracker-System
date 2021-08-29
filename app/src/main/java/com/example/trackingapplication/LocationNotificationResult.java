package com.example.trackingapplication;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import androidx.core.app.NotificationCompat;

public class LocationNotificationResult {

    private Context context;
    private Location location;

    public LocationNotificationResult(Context context, Location location) {
        this.context = context;
        this.location = location;
    }

    public String getLocationNotification(){

        if (location == null){

            return "Location not received";
        }else{

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(location.getLongitude());
            stringBuilder.append("/");
            stringBuilder.append(location.getLongitude());

            return stringBuilder.toString();


        }



    }

    public void LocationNotification(){

        Intent notificationintent = new Intent(context,LocationActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        stackBuilder.addParentStack(LocationActivity.class);
        stackBuilder.addNextIntent(notificationintent);

        PendingIntent notificationpendingIntent =
                stackBuilder.getPendingIntent(0,PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notificationbuilder=null;


            notificationbuilder = new NotificationCompat.Builder(context,App.CHANNEL_ID)
                    .setContentTitle("Location Notification")
                    .setContentText(getLocationNotification())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setContentIntent(notificationpendingIntent);
            getNotificationManager().notify(0,notificationbuilder.build());

    }

    private NotificationManager getNotificationManager() {

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager;
    }
}
