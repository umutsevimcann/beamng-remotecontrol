package com.beamng.remotecontrol.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Locale

/** Small helpers shared by the welcome screen and the QR/connect flow. */
object NetworkUtils {

    /**
     * The device's active Wi-Fi [Network], or null if none.
     *
     * Discovery sockets are pinned to this so they use Wi-Fi even when mobile
     * data is on. Without it, Android routes app traffic over cellular whenever
     * the Wi-Fi has no internet (a LAN-only game network is exactly that): the
     * broadcast still leaks out and the game registers the device, but the reply
     * never reaches us — the app just sits on "searching".
     */
    fun wifiNetwork(context: Context): Network? {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Phone's Wi-Fi IPv4 as "a.b.c.d", or null when Wi-Fi is down. */
    @Suppress("DEPRECATION")
    fun wifiIpv4String(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) null else String.format(
                Locale.US, "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    /** First non-loopback address on a wlan0/eth0/ap0 interface. */
    fun localInetAddress(): InetAddress? {
        try {
            for (singleInterface in NetworkInterface.getNetworkInterfaces()) {
                for (inetAddress in singleInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress &&
                        (singleInterface.displayName.contains("wlan0") ||
                            singleInterface.displayName.contains("eth0") ||
                            singleInterface.displayName.contains("ap0"))
                    ) {
                        return inetAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }

    /** Broadcast address of the interface owning [inetAddr]. */
    fun broadcastAddress(inetAddr: InetAddress?): InetAddress? {
        try {
            val temp = NetworkInterface.getByInetAddress(inetAddr) ?: return null
            for (interfaceAddress in temp.interfaceAddresses) {
                if (interfaceAddress.broadcast != null) {
                    return interfaceAddress.broadcast
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return null
    }
}
