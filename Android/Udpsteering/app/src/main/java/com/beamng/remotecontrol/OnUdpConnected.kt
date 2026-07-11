package com.beamng.remotecontrol

import java.net.InetAddress

interface OnUdpConnected {
    fun onUdpConnected(hostAddress: InetAddress)

    /** [message] is null when the discovery was cancelled by the user. */
    fun onError(message: String?)

    fun onCancel()
}
