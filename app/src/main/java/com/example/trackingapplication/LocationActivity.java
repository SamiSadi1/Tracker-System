package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.location.Location;

import android.os.Bundle;

import android.view.View;
import android.widget.Toast;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;


public class LocationActivity extends AppCompatActivity {

    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;

    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) {
                return;
            }
            Location location = locationResult.getLastLocation();
           // Toast.makeText(LocationActivity.this, location.getLatitude() + "/" + location.getLongitude(), Toast.LENGTH_SHORT).show();
           LocationNotificationResult locationNotificationResult =
                   new LocationNotificationResult(LocationActivity.this,location);
           locationNotificationResult.LocationNotification();
           locationNotificationResult.getLocationNotification();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);


    }
    /*private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }*/
    private void getLocationUpdate() {

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setMaxWaitTime(15 * 1000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    public void stoplocationupdate(View view) {

        Intent intent = new Intent(this,MyBackGroundLocationService.class);
        stopService(intent);
        Toast.makeText(this, "Stop Location Update", Toast.LENGTH_SHORT).show();

    }

    public void startlocationupdate(View view) {

        Intent intent = new Intent(this,MyBackGroundLocationService.class);
        ContextCompat.startForegroundService(this,intent);
        Toast.makeText(this, "Start Location Update", Toast.LENGTH_SHORT).show();


    }
}