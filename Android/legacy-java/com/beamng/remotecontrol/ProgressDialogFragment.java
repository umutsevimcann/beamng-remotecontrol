package com.beamng.remotecontrol;

import android.app.Dialog;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

public class ProgressDialogFragment extends DialogFragment {
    private OnUdpConnected listener;

    public void setListener(OnUdpConnected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Connecting to BeamNG.drive");
        
        // Progress bar içeren basit bir layout
        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setPadding(40, 40, 40, 40);
        builder.setView(progressBar);
        
        builder.setCancelable(true);
        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        if (listener != null) {
            listener.onCancel();
        }
    }

    public boolean isShowing() {
        return getDialog() != null && getDialog().isShowing();
    }
}

