package com.mobile_computing.originalgpsapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class GPSManager {
    // 現在地を取得するための変数
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationSettingsRequest locationSettingsRequest;

    // 出力
    private Location location;
    private String logMessage;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // コンストラクタ
    public GPSManager(Activity activity) {

        //==========================================================================================
        // 1. 権限の確認
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        //==========================================================================================
        // 2. LocationRequestの設定
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // 高精度を要求
        locationRequest.setInterval(5000); // ５秒おきに取得
        locationRequest.setFastestInterval(1000); // 最速でも1秒おきに取得

        //==========================================================================================
        // 3. LocationCallbackの登録
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                location = locationResult.getLastLocation();
            }
        };

        //==========================================================================================
        // 4. LocationSettingsRequestの設定（デバイス側のLocationRequestの設定を取得する）
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        locationSettingsRequest = builder.addLocationRequest(locationRequest).build();

        //==========================================================================================
        // 5. Clientの準備
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        settingsClient = LocationServices.getSettingsClient(activity);

        //==========================================================================================
        // 6. デバイスがLocationRequestで設定された要求を満たしている場合，そうでない場合の処理を記述する
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(activity, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
                    }
                })
                .addOnFailureListener(activity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        logMessage = "Failed to acquire location information.\n";
                    }
                });
    }
}
