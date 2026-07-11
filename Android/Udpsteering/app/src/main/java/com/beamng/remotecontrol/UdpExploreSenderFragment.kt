package com.beamng.remotecontrol

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import androidx.fragment.app.Fragment
import java.net.InetAddress

@Suppress("DEPRECATION")
class UdpExploreSenderFragment : Fragment(), OnUdpConnected {

    // data object we want to retain
    private var exploreSender: UdpExploreSender? = null
    private var scannerActivity: QRCodeScanner? = null

    // this method is only called once for this fragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // retain this fragment
        retainInstance = true
    }

    fun execute(broadcastAddress: InetAddress, parent: QRCodeScanner, ip: String, securityCode: String) {
        check(exploreSender == null)
        exploreSender = UdpExploreSender(broadcastAddress, this, ip, parent).also {
            it.execute(securityCode)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is QRCodeScanner) {
            scannerActivity = context
        }
    }

    override fun onUdpConnected(hostAddress: InetAddress) {
        scannerActivity?.let { activity ->
            val intent = Intent(activity, MainActivity::class.java)
            (activity.application as RemoteControlApplication).hostAddress = hostAddress
            startActivity(intent)
        }
    }

    override fun onError(message: String?) {
        scannerActivity?.onError(message)
    }

    override fun onCancel() {
        if (exploreSender != null) {
            cancelTask()
            scannerActivity?.onError(null)
        }
    }

    fun cancelTask() {
        if (isRunning) {
            exploreSender?.cancel(true)
        }
    }

    val isRunning: Boolean
        get() = exploreSender?.status == AsyncTask.Status.RUNNING
}
