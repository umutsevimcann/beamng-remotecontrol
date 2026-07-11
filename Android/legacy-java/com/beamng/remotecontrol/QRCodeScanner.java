package com.beamng.remotecontrol;

import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

public class QRCodeScanner extends AppCompatActivity {
    private DecoratedBarcodeView barcodeView;
    private WifiManager wifiManager;
    private UdpExploreSenderFragment exploreSenderFragment;
    private ProgressDialogFragment progressDialogFragment;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_qr_scanner); // XML tabanlı yapıya geçiyoruz
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        barcodeView = findViewById(R.id.barcode_scanner);
        barcodeView.decodeContinuous(callback);

        FragmentManager fm = getSupportFragmentManager();
        exploreSenderFragment = (UdpExploreSenderFragment) fm.findFragmentByTag("exploreSender");
        if (exploreSenderFragment == null) {
            exploreSenderFragment = new UdpExploreSenderFragment();
            fm.beginTransaction().add(exploreSenderFragment, "exploreSender").commit();
        }
        
        progressDialogFragment = (ProgressDialogFragment) fm.findFragmentByTag("progressDialog");
        if (progressDialogFragment == null) {
            progressDialogFragment = new ProgressDialogFragment();
        }
        progressDialogFragment.setListener(exploreSenderFragment);
    }

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null) return;
            
            barcodeView.pause();
            handleScanResult(result.getText());
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {}
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        barcodeView.resume();
        
        if (!exploreSenderFragment.isRunning()) {
            if (progressDialogFragment.isShowing()) {
                progressDialogFragment.dismiss();
            }
        } else {
            barcodeView.pause();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) {
            exploreSenderFragment.cancelTask();
        }
        barcodeView.pause();
    }

    private void handleScanResult(String rawResult) {
        String[] parts = rawResult.split("#");
        if (parts.length != 2) {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_LONG).show();
            barcodeView.resume();
            return;
        }
        final String securityCode = parts[1];

        // Validate securityCode: max 64 chars, alphanumeric + underscore + hyphen only
        if (securityCode.length() > 64 || !securityCode.matches("^[a-zA-Z0-9_\\-]+$")) {
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show();
            barcodeView.resume();
            return;
        }

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        try {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

            ((RemoteControlApplication) getApplication()).setIp(ip);

            InetAddress broadcastAddress = getBroadcastAddress(getIpAddress());
            Log.i("Broadcast Address", broadcastAddress.getHostAddress());

            exploreSenderFragment.execute(broadcastAddress, this, ip, securityCode);
            progressDialogFragment.show(getSupportFragmentManager(), "progressDialog");
        } catch(Exception e) {
            Toast.makeText(this, "You must be connected to the same WiFi as your PC", Toast.LENGTH_LONG).show();
            barcodeView.resume();
        }
    }

    private InetAddress getBroadcastAddress(InetAddress inetAddr) {
        try {
            NetworkInterface temp = NetworkInterface.getByInetAddress(inetAddr);
            if (temp == null) return null;
            
            List<InterfaceAddress> addresses = temp.getInterfaceAddresses();
            for (InterfaceAddress inetAddress : addresses) {
                if (inetAddress.getBroadcast() != null)
                    return inetAddress.getBroadcast();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public InetAddress getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> networkInterface = NetworkInterface.getNetworkInterfaces(); 
                 networkInterface.hasMoreElements(); ) {
                NetworkInterface singleInterface = networkInterface.nextElement();
                for (Enumeration<InetAddress> IpAddresses = singleInterface.getInetAddresses(); 
                     IpAddresses.hasMoreElements(); ) {
                    InetAddress inetAddress = IpAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (singleInterface.getDisplayName().contains("wlan0") ||
                            singleInterface.getDisplayName().contains("eth0") ||
                            singleInterface.getDisplayName().contains("ap0"))) {
                        return inetAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void onError(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        if (progressDialogFragment.isShowing()) {
            progressDialogFragment.dismiss();
        }
        barcodeView.resume();
    }
}
