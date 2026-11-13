package com.beamng.remotecontrol.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
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

    /**
     * Pins [socket] to [network] (Wi-Fi) so it can't fall back to cellular. This
     * is what makes discovery and control work with mobile data on — see
     * [wifiNetwork]. No-op when [network] is null (e.g. phone is the hotspot).
     */
    fun bindToNetwork(network: Network?, socket: DatagramSocket) {
        if (network == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        try {
            network.bindSocket(socket)
        } catch (e: IOException) {
            Log.w("NetworkUtils", "Could not pin socket to Wi-Fi", e)
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

    /**
     * The phone's IPv4 on the LAN the game is on: the Wi-Fi client address, or —
     * when the phone itself is the hotspot the PC joined — the hotspot address
     * (WifiManager reports 0 in that case). Null if neither exists.
     */
    fun phoneIpv4String(context: Context): String? =
        wifiIpv4String(context) ?: localInetAddress()?.hostAddress

    /**
     * IPv4 of the Wi-Fi client or hotspot interface (one that has a broadcast
     * address, which discovery needs). Interface names vary by vendor: wlan0/wlan1
     * for the client, ap0/swlan0/softap0/wlan1 for the hotspot. When [preferIp] is
     * given (the Wi-Fi client IP), that interface wins.
     */
    fun localInetAddress(preferIp: String? = null): InetAddress? {
        var fallback: InetAddress? = null
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase(Locale.US)
                if (LAN_IFACE_PREFIXES.none { name.startsWith(it) }) continue
                for (ifaceAddress in iface.interfaceAddresses) {
                    val address = ifaceAddress.address
                    if (address !is Inet4Address || ifaceAddress.broadcast == null) continue
                    if (preferIp == null || address.hostAddress == preferIp) return address
                    if (fallback == null) fallback = address
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return fallback
    }

    private val LAN_IFACE_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "eth")

    /**
     * True only when the phone is a Wi-Fi client on the 5/6 GHz band. Auto-connect
     * uses it to pick its broadcast pace: 2.4 GHz APs (and the phone's own
     * hotspot, where this is false) can't take a fast broadcast sweep.
     */
    @Suppress("DEPRECATION")
    fun isWifi5Ghz(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            info.ipAddress != 0 && info.frequency >= 4900
        } catch (e: Exception) {
            false
        }
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
