package com.beamng.remotecontrol;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

public class WelcomeActivity extends AppCompatActivity {

    public static final int CAM_PERMISSION_REQUEST = 100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
    }

    public void onScanClick(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.i("BeamNG", "No Camera Permission");

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAM_PERMISSION_REQUEST);
            return;
        }
        Intent intent = new Intent(this, QRCodeScanner.class);
        startActivity(intent);
    }
    
    public void onSettingsClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView hint = findViewById(R.id.textTelemetryHint);
        if (hint != null) {
            hint.setText("In BeamNG: Options → Other → OutGauge support\n"
                    + "Enable it and set:\n"
                    + "IP: " + getWifiIpAddress() + "     Port: 4445");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAM_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, QRCodeScanner.class));
        }
    }

    private String getWifiIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip != 0) {
                return String.format(Locale.US, "%d.%d.%d.%d",
                        ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
            }
        } catch (Exception ignored) {
        }
        return "(connect phone to Wi-Fi)";
    }
}

