package com.beamng.remotecontrol

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment : DialogFragment() {

    /** Invoked when the user cancels the dialog (back press / outside tap). */
    var onCancelAction: (() -> Unit)? = null

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
        onCancelAction?.invoke()
    }

    val isShowing: Boolean
        get() = dialog?.isShowing == true
}
