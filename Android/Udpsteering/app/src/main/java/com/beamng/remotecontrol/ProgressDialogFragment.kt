package com.beamng.remotecontrol

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment : DialogFragment() {

    private var listener: OnUdpConnected? = null

    fun setListener(listener: OnUdpConnected?) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle("Connecting to BeamNG.drive")

        val progressBar = ProgressBar(requireContext())
        progressBar.setPadding(40, 40, 40, 40)
        builder.setView(progressBar)

        builder.setCancelable(true)
        return builder.create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        listener?.onCancel()
    }

    val isShowing: Boolean
        get() = dialog?.isShowing == true
}
