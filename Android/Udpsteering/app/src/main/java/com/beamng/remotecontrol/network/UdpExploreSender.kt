package com.beamng.remotecontrol.network

import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import com.beamng.remotecontrol.OnUdpConnected
import com.beamng.remotecontrol.protocol.Ports
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

/**
 * Discovery handshake with the game (verified against remoteController.lua):
 * broadcasts "beamng|<deviceName>|<code>" to port 4444 and waits for the
 * game's "beamng|<code>" reply on port 4445.
 */
@Suppress("DEPRECATION")
class UdpExploreSender(
    private val netadress: InetAddress,
    private val listener: OnUdpConnected,
    private val iadr: String,
    @Suppress("UNUSED_PARAMETER") ctx: Context
) : AsyncTask<String, String, String>() {

    private val hostPORT = Ports.GAME
    private val localPORT = Ports.APP
    private var hostadress: InetAddress? = null

    private val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                capitalize(model)
            } else {
                capitalize(manufacturer) + " " + model
            }
        }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val first = s[0]
        return if (first.isUpperCase()) s else first.uppercaseChar() + s.substring(1)
    }

    override fun doInBackground(vararg args: String?): String? {
        val securityCode = args[0]

        val safeName = deviceName.replace(Regex("[|#\\n\\r]"), "_")
        val sendString = "beamng|$safeName|$securityCode"
        if (Log.isLoggable("BeamNG", Log.DEBUG)) {
            Log.i("SendString", sendString)
        }
        val buffer = sendString.toByteArray()

        var socketS: DatagramSocket? = null
        var channel: DatagramChannel? = null
        try {
            socketS = DatagramSocket()
            channel = DatagramChannel.open()
            val socketR = channel.socket()
            socketR.reuseAddress = true
            socketR.bind(InetSocketAddress(iadr, localPORT))
            socketR.soTimeout = 250
            val waitingFor = "beamng|$securityCode"
            val receiveBuf = ByteArray(32)
            val packetR = DatagramPacket(receiveBuf, receiveBuf.size)
            val packetS = DatagramPacket(buffer, buffer.size, netadress, hostPORT)

            var tries = 0
            while (!isCancelled) {
                try {
                    socketS.send(packetS)
                    if (Log.isLoggable("BeamNG", Log.DEBUG)) {
                        Log.i("packet", packetS.toString())
                    }
                    socketR.receive(packetR)
                } catch (e: IOException) {
                    if (++tries > 10) {
                        return "Connection timeout."
                    }
                    e.printStackTrace()
                    try {
                        Thread.sleep(1000)
                    } catch (e1: InterruptedException) {
                        break
                    }
                    continue
                }
                val message = String(receiveBuf, 0, packetR.length)
                hostadress = packetR.address
                if (Log.isLoggable("BeamNG", Log.DEBUG)) {
                    Log.i(
                        "UDP SERVER", "Received: " + message + " IP " +
                            packetR.address.hostAddress + ":" + packetR.port +
                            " / waiting for: " + waitingFor
                    )
                }
                if (message == waitingFor) {
                    publishProgress(message)
                    break
                }
            }
        } catch (e: Exception) {
            return e.toString()
        } finally {
            if (channel != null) {
                try {
                    channel.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            socketS?.close()
        }
        return null
    }

    override fun onProgressUpdate(vararg values: String?) {
        super.onProgressUpdate(*values)
        hostadress?.let { listener.onUdpConnected(it) }
        cancel(true)
    }

    override fun onPostExecute(result: String?) {
        listener.onError(result)
    }
}
