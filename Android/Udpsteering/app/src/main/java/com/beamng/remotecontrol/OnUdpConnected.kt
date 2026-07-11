package com.beamng.remotecontrol

import java.net.InetAddress

interface OnUdpConnected {
    fun onUdpConnected(hostAddress: InetAddress)
    fun onError(message: String)
    fun onCancel()
}
