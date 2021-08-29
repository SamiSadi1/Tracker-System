package com.example.trackingapplication;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyBackGroundLocationService extends Service {



    private FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;

    FirebaseAuth fAuth;
    FirebaseFirestore fstore;
    String userId;
   // String email;
    //String name;


    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) {
                return;
            }

            Location location = locationResult.getLastLocation();
            // Toast.makeText(LocationActivity.this, location.getLatitude() + "/" + location.getLongitude(), Toast.LENGTH_SHORT).show();
            LocationNotificationResult locationNotificationResult =
                    new LocationNotificationResult(MyBackGroundLocationService.this,location);
            locationNotificationResult.LocationNotification();
            locationNotificationResult.getLocationNotification();

            //Toast.makeText(MyBackGroundLocationService.this, location.getLatitude() + "/" + location.getLongitude(),
                    //Toast.LENGTH_SHORT).show();

            fAuth = FirebaseAuth.getInstance();
            fstore = FirebaseFirestore.getInstance();
            userId = fAuth.getCurrentUser().getEmail();


            DocumentReference df = fstore.collection("Location").document(userId);
            Map<String,Object> userinfo = new HashMap<>();

            userinfo.put("Latitude",location.getLatitude());
            userinfo.put("Longitude",location.getLongitude());
            userinfo.put("Address",getCOmpleteAddress(location.getLatitude(),location.getLongitude() ));
            userinfo.put("Update Time",new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date()));
            userinfo.put("Update Date",new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
            df.set(userinfo).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {

                    if (task.isSuccessful()){

                        Toast.makeText(MyBackGroundLocationService.this, "Location saved", Toast.LENGTH_SHORT).show();

                    }
                    else {
                        Toast.makeText(MyBackGroundLocationService.this, "Location Not Saved", Toast.LENGTH_SHORT).show();
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MyBackGroundLocationService.this, "Error!!!"+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });


        }
    };

    private String getCOmpleteAddress(double latitude, double longitude) {

        String address = "";

        Geocoder geocoder = new Geocoder(MyBackGroundLocationService.this, Locale.getDefault());

        try{

            List<Address> addresses = geocoder.getFromLocation(latitude,longitude,1);

            if(address!=null){

                Address returnAddress = addresses.get(0);
                StringBuilder stringBuilderReturnAddress =  new StringBuilder("");

                for(int i=0; i<=returnAddress.getMaxAddressLineIndex();i++){
                    stringBuilderReturnAddress.append(returnAddress.getAddressLine(i)).append("\n");
                }

                address = stringBuilderReturnAddress.toString();

            }
            else{
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
            }

        }
        catch (Exception e){
            Toast.makeText(this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
        }


        return address;


    }

    public MyBackGroundLocationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Toast.makeText(this, "Service Start", Toast.LENGTH_SHORT).show();
       startForeground(1001,getNotification());
        getLocationUpdate();

        return START_STICKY;
    }

    private Notification getNotification() {

        NotificationCompat.Builder notificationbuilder = null;
        notificationbuilder = new NotificationCompat.Builder(getApplicationContext(),App.CHANNEL_ID)
                .setContentTitle("Location Notification")
                .setContentText("Location Service in Background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true);

        return notificationbuilder.build();

    }

    private void getLocationUpdate() {

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
           stopForeground(true);
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    public IBinder onBind(Intent intent) {
     return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "service Destroy", Toast.LENGTH_SHORT).show();
        stopForeground(true);
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        
    }
}