package com.beamng.remotecontrol

import android.app.Application
import java.net.InetAddress

class RemoteControlApplication : Application() {
    var hostAddress: InetAddress? = null
    var ip: String? = null
}
