package com.mobile_computing.originalgpsapplication;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.mobile_computing.originalgpsapplication.databinding.ActivityMapsBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    // ????????????????????????????????????
    String logText = "";


    // ???????????????????????????????????????
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationSettingsRequest locationSettingsRequest;


    // ??????????????????????????????????????????
    public File file;
    private String fileName;
    private BroadcastReceiver receiver;
    private long downloadID = 0;


    // ??????????????????????????????????????????
    private LatLng[] points = null; // ??????
    private float hazard = 0; // ?????????
    private String longitude;
    private String latitude;


    private GoogleMap mMap = null;
    private ActivityMapsBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ////////////////////////////////////////////////////////////////////////////////////////////
        // GPS??????
        //==========================================================================================
        // 1. ???????????????
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        //==========================================================================================
        // 2. LocationRequest?????????
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // ??????????????????
        locationRequest.setInterval(5000); // ?????????????????????
        locationRequest.setFastestInterval(1000); // ????????????1??????????????????

        //==========================================================================================
        // 3. LocationCallback?????????
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location lastLocation = locationResult.getLastLocation();

                LatLng location = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                if (mMap != null) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location));
                }

                TextView textView_Longitude = findViewById(R.id.textView_Longitude);
                textView_Longitude.setText(String.format(Locale.ENGLISH, "%s: %f  ", "??????", lastLocation.getLongitude()));
                longitude = String.valueOf(lastLocation.getLongitude());

                TextView textView_Latitude = findViewById(R.id.textView_Latitude);
                textView_Latitude.setText(String.format(Locale.ENGLISH, "%s: %f  ", "??????", lastLocation.getLatitude()));
                latitude = String.valueOf(lastLocation.getLatitude());
            }
        };

        //==========================================================================================
        // 4. LocationSettingsRequest??????????????????????????????LocationRequest???????????????????????????
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        locationSettingsRequest = builder.addLocationRequest(locationRequest).build();

        //==========================================================================================
        // 5. Client?????????
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        //==========================================================================================
        // 6. ???????????????LocationRequest???????????????????????????????????????????????????????????????????????????????????????????????????
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                         logText += "Failed to acquire location information.\n";
                    }
                });




        ////////////////////////////////////////////////////////////////////////////////////////////
        // ???????????????????????????????????????
        fileName = "earthquake_hazard.geojson";
        file = new File(getExternalFilesDir(null), fileName);



        ////////////////////////////////////////////////////////////////////////////////////////////
        // BroadcastReceiver?????????
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long intentID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadID == intentID) {
                    // ???????????????
                    Toast.makeText(MapsActivity.this, "Download Completed", Toast.LENGTH_LONG).show();
                    logText += "Download Completed\n";
                    TextView textView_Log = findViewById(R.id.textView_Log);
                    textView_Log.setText(logText);

                    // ??????????????????????????????????????????????????????
                    DataAnalyze(ReadData());

                    // ?????????????????????
                    int value =  (int)((float)255 * hazard);
                    if (mMap != null && points != null) {
                        Polygon polygon = mMap.addPolygon(new PolygonOptions()
                                .clickable(true)
                                .add(points)
                                .strokeColor(Color.argb(127, value, 0, 0))
                                .fillColor(Color.argb(127, value, 0, 0))
                        );
                        polygon.setTag(hazard);
                    }
                }
            }
        };
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));



        ////////////////////////////////////////////////////////////////////////////////////////////
        // Google Map?????????
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    // JSON?????????????????????????????????
    public void DataDownload(View view) {
        String target
                = "https://www.j-shis.bosai.go.jp/map/api/pshm/Y2021/AVR/TTL_MTTL/meshinfo.geojson?position=" + longitude + "," + latitude + "&epsg=4301&attr=T30_I45_PS";

        //==========================================================================================
        // 1. DownloadManager??????????????????????????????
        DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);

        // 2. DownloadManager.Request??????????????????????????????
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(target));
        request.setDestinationUri(Uri.fromFile(file));
        request.setTitle(fileName);
        request.setDescription("Downloading a GeoJSON data");
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
        request.setMimeType("application/json");

        // 3. REQUEST????????????????????????????????????????????????????????????
        downloadID = manager.enqueue(request); // ????????????long????????????????????? -> ??????????????????????????????????????????

        //???4. ???????????????)
        logText += "Download -> " + target + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);
    }

    private String ReadData() {
        //==========================================================================================
        // 1. ??????????????????????????????????????????????????????????????????
        if (file.exists()) {
            logText += "the downloaded file is exist\n";
            Toast.makeText(this, "the downloaded file is exist", Toast.LENGTH_LONG).show();
        } else {
            logText += "you are already dead\n";
            Toast.makeText(this, "you are already dead", Toast.LENGTH_LONG).show();
            return "";
        }

        //==========================================================================================
        // 2. ???????????????????????????????????????????????????
        FileInputStream fileInputStream = null;
        BufferedReader bufferedReader = null;
        StringBuilder builder = new StringBuilder();

        try {
            fileInputStream = new FileInputStream(file);
            bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //==========================================================================================
        // 3. ??????????????????????????????????????????
        logText += builder + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);

        //==========================================================================================
        // 4. ?????????????????????
        if(file.delete()) {
            logText += "downloaded file is deleted\n";
            textView_Log.setText(logText);
            Toast.makeText(MapsActivity.this, "downloaded file is deleted", Toast.LENGTH_LONG).show();
        }

        return builder.toString();
    }

    // JSON?????????????????????????????????
    private void DataAnalyze(String rawData) {
        StringBuilder builder = new StringBuilder();
        String data; // ??????????????????????????????????????????????????????
        try {
            JSONObject jsonObject = new JSONObject(rawData);

            //======================================================================================
            // 1. features?????????????????????
            JSONObject features = jsonObject.getJSONArray("features").getJSONObject(0);
            // 2. geometry?????????????????????????????????
            JSONObject geometry = features.getJSONObject("geometry");
            // 3. coordinates?????????????????????????????????????????????????????????????????????5????????????????????????????????????????????????
            JSONArray coordinates = geometry.getJSONArray("coordinates").getJSONArray(0);
            // 4. properties?????????
            JSONObject properties = features.getJSONObject("properties");
            // 5. T30_I45_PS???????????????
            String probability = properties.getString("T30_I45_PS").toLowerCase();

            //======================================================================================
            // 1. ???????????????????????????
            points = new LatLng[coordinates.length()];
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray position = coordinates.getJSONArray(i);
                String longitude = position.getString(0);
                String latitude = position.getString(1);
                data = "Position:" + i + " [?????????" + longitude + ", ?????????" + latitude + "]\n";
                builder.append(data);

                // String??????float????????????????????????????????????
                points[i] = new LatLng(Float.parseFloat(latitude), Float.parseFloat(longitude));
            }
            // 2. ???????????????????????????
            data = "Probability: " + probability + "\n";
            builder.append(data);
            hazard = Float.parseFloat(probability);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        //==========================================================================================
        // ???????????????
        logText += builder + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);
    }
}