package com.beamng.remotecontrol.protocol

/**
 * The two UDP ports shared with BeamNG.drive
 * (lua/ge/extensions/core/remoteController.lua: listenPort / appPort).
 */
object Ports {
    /** Game listens here: discovery handshake + control packets. */
    const val GAME = 4444

    /** Phone listens here: discovery reply + OutGauge telemetry. */
    const val APP = GAME + 1

    /** Phone listens here: optional MotionSim stream (drift meter). */
    const val MOTION = 4446

    /** The RemoteControlPro mod listens here (its own port; it replies to the sender). */
    const val MOD = 4450
}
