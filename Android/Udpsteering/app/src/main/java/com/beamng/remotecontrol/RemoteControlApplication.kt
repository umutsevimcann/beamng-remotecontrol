package com.beamng.remotecontrol

import android.app.Application
import java.net.InetAddress

class RemoteControlApplication : Application() {
    var hostAddress: InetAddress? = null

    /**
     * Set when the current link is the RemoteControlPro mod (code-free, more
     * controls); null when we are talking to the stock game.
     */
    var modInfo: com.beamng.remotecontrol.protocol.ModProtocol.ModInfo? = null

    /** QR security code of the current session — fuels the reconnect heartbeat. */
    var securityCode: String? = null
}
