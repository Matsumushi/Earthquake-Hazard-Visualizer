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
    // ログを出力するための変数
    String logText = "";


    // 現在地を取得するための変数
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationSettingsRequest locationSettingsRequest;


    // ダウンロードの制御に使う変数
    public File file;
    private String fileName;
    private BroadcastReceiver receiver;
    private long downloadID = 0;


    // データを可視化するための配列
    private LatLng[] points = null; // 座標
    private float hazard = 0; // 危険値
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
        // GPS起動
        //==========================================================================================
        // 1. 権限の確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
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
                Location lastLocation = locationResult.getLastLocation();

                LatLng location = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                if (mMap != null) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location));
                }

                TextView textView_Longitude = findViewById(R.id.textView_Longitude);
                textView_Longitude.setText(String.format(Locale.ENGLISH, "%s: %f  ", "経度", lastLocation.getLongitude()));
                longitude = String.valueOf(lastLocation.getLongitude());

                TextView textView_Latitude = findViewById(R.id.textView_Latitude);
                textView_Latitude.setText(String.format(Locale.ENGLISH, "%s: %f  ", "緯度", lastLocation.getLatitude()));
                latitude = String.valueOf(lastLocation.getLatitude());
            }
        };

        //==========================================================================================
        // 4. LocationSettingsRequestの設定（デバイス側のLocationRequestの設定を取得する）
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        locationSettingsRequest = builder.addLocationRequest(locationRequest).build();

        //==========================================================================================
        // 5. Clientの準備
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        //==========================================================================================
        // 6. デバイスがLocationRequestで設定された要求を満たしている場合，そうでない場合の処理を記述する
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
        // ファイルダウンロードの準備
        fileName = "earthquake_hazard.geojson";
        file = new File(getExternalFilesDir(null), fileName);



        ////////////////////////////////////////////////////////////////////////////////////////////
        // BroadcastReceiverの登録
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long intentID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadID == intentID) {
                    // ログを出力
                    Toast.makeText(MapsActivity.this, "Download Completed", Toast.LENGTH_LONG).show();
                    logText += "Download Completed\n";
                    TextView textView_Log = findViewById(R.id.textView_Log);
                    textView_Log.setText(logText);

                    // データを読み込み，解析してログに出力
                    DataAnalyze(ReadData());

                    // データを可視化
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
        // Google Mapの準備
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

    // JSONのデータを落としてくる
    public void DataDownload(View view) {
        String target
                = "https://www.j-shis.bosai.go.jp/map/api/pshm/Y2021/AVR/TTL_MTTL/meshinfo.geojson?position=" + longitude + "," + latitude + "&epsg=4301&attr=T30_I45_PS";

        //==========================================================================================
        // 1. DownloadManagerのインスタンスを取得
        DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);

        // 2. DownloadManager.Requestのインスタンスを生成
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(target));
        request.setDestinationUri(Uri.fromFile(file));
        request.setTitle(fileName);
        request.setDescription("Downloading a GeoJSON data");
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
        request.setMimeType("application/json");

        // 3. REQUESTをキューに追加してダウンロードを開始する
        downloadID = manager.enqueue(request); // 戻り値でlong値が帰ってくる -> この値はステータス確認に使う

        //（4. ログに出力)
        logText += "Download -> " + target + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);
    }

    private String ReadData() {
        //==========================================================================================
        // 1. ファイルの存在確認（ほとんどは大丈夫なはず）
        if (file.exists()) {
            logText += "the downloaded file is exist\n";
            Toast.makeText(this, "the downloaded file is exist", Toast.LENGTH_LONG).show();
        } else {
            logText += "you are already dead\n";
            Toast.makeText(this, "you are already dead", Toast.LENGTH_LONG).show();
            return "";
        }

        //==========================================================================================
        // 2. ストリームを開き，データを読み込む
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
        // 3. 読み込んだデータをログに出力
        logText += builder + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);

        //==========================================================================================
        // 4. ファイルを消去
        if(file.delete()) {
            logText += "downloaded file is deleted\n";
            textView_Log.setText(logText);
            Toast.makeText(MapsActivity.this, "downloaded file is deleted", Toast.LENGTH_LONG).show();
        }

        return builder.toString();
    }

    // JSONデータの解析をする関数
    private void DataAnalyze(String rawData) {
        StringBuilder builder = new StringBuilder();
        String data; // データを追加する媒介をするための変数
        try {
            JSONObject jsonObject = new JSONObject(rawData);

            //======================================================================================
            // 1. featuresを取得（配列）
            JSONObject features = jsonObject.getJSONArray("features").getJSONObject(0);
            // 2. geometryを取得（オブジェクト）
            JSONObject geometry = features.getJSONObject("geometry");
            // 3. coordinatesを取得（配列）（配列の中に配列があり，その中に5つの要素がある構成になっている）
            JSONArray coordinates = geometry.getJSONArray("coordinates").getJSONArray(0);
            // 4. propertiesを取得
            JSONObject properties = features.getJSONObject("properties");
            // 5. T30_I45_PSの値を取得
            String probability = properties.getString("T30_I45_PS").toLowerCase();

            //======================================================================================
            // 1. 値の出力　（座標）
            points = new LatLng[coordinates.length()];
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray position = coordinates.getJSONArray(i);
                String longitude = position.getString(0);
                String latitude = position.getString(1);
                data = "Position:" + i + " [経度：" + longitude + ", 緯度：" + latitude + "]\n";
                builder.append(data);

                // String値をfloat値に変換しながら値を格納
                points[i] = new LatLng(Float.parseFloat(latitude), Float.parseFloat(longitude));
            }
            // 2. 値の出力（危険値）
            data = "Probability: " + probability + "\n";
            builder.append(data);
            hazard = Float.parseFloat(probability);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        //==========================================================================================
        // ログを出力
        logText += builder + "\n";
        TextView textView_Log = findViewById(R.id.textView_Log);
        textView_Log.setText(logText);
    }
}