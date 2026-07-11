package com.beamng.remotecontrol;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import java.net.InetAddress;

public class UdpExploreSenderFragment extends Fragment implements OnUdpConnected {

    // data object we want to retain
    private UdpExploreSender exploreSender;
    private QRCodeScanner scannerActivity;

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void execute(InetAddress broadcastAddress, QRCodeScanner parent, String ip, String securityCode) {
        //Log.i("IP", broadcastAddress.getHostAddress());
        assert(exploreSender == null);
        exploreSender = new UdpExploreSender(broadcastAddress, this, ip, parent);
        exploreSender.execute(securityCode);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof QRCodeScanner) {
            this.scannerActivity = (QRCodeScanner) context;
        }
    }

    @Override
    public void onUdpConnected(InetAddress hostAddress) {
        if (scannerActivity != null) {
            Intent intent = new Intent(this.scannerActivity, MainActivity.class);
            ((RemoteControlApplication) scannerActivity.getApplication()).setHostAddress(hostAddress);
            startActivity(intent);
        }
    }

    @Override
    public void onError(String message) {
        if (scannerActivity != null) {
            scannerActivity.onError(message);
        }
    }

    @Override
    public void onCancel() {
        if (exploreSender != null) {
            cancelTask();
            if (scannerActivity != null) {
                scannerActivity.onError(null);
            }
        }
    }

    public void cancelTask() {
        if (isRunning()) {
            exploreSender.cancel(true);
        }
    }

    public boolean isRunning() {
        if (exploreSender == null) {
            return false;
        }
        return exploreSender.getStatus() == AsyncTask.Status.RUNNING;
    }
}
