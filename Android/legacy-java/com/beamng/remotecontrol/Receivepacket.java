package com.beamng.remotecontrol;

import android.util.Log;

import org.apache.commons.io.EndianUtils;

import java.nio.ByteBuffer;

/**
 * Parses the OutGauge telemetry packet received from BeamNG.drive.
 * Struct layout: see outgauge_t definition below.
 */
public class Receivepacket {
    // Stock BeamNG OutGauge (Options > Other) sends the standard 96-byte LFS struct;
    // the companion mod appends a 4-byte odometer (100 bytes). Accept both.
    public static final int MIN_PACKET_LENGTH = 96;

    private int time;
    private int flags;
    private static final int FLAG_KMH = 16384;
    private boolean[] flagsarray = new boolean[5];
    private byte gear;
    private String gearStr;
    private float speed;
    private float rpm;
    private float engTemp;
    private float fuel;
    private float throttle;
    private float brake;
    private int odometer;
    private int dashLights;
    private int id;
    private boolean[] dasharray = new boolean[11];
    private int showLights;
    // Bit values from the game's lua/vehicle/protocols/outgauge.lua (DL_x constants).
    private static final int FLAG_SHIFTLIGHT = 1;
    private static final int FLAG_FULLBEAM = 2;
    private static final int FLAG_HANDBREAK = 4; // DL_HANDBRAKE = 2^2; 16 is DL_TC (was wrongly lighting on traction control)
    private static final int FLAG_SIGNAL_L = 32;
    private static final int FLAG_SIGNAL_R = 64;
    private static final int FLAG_OILWARN = 256;
    private static final int FLAG_BATTERY = 512;
    private static final int FLAG_ABS = 1024;
    private boolean[] lightsArray = new boolean[11];
    private boolean valid = false;

    /**
     * OutGauge struct layout (Little-Endian):
     * 0-3:   unsigned time
     * 4-7:   char car[4]
     * 8-9:   unsigned short flags
     * 10:    char gear
     * 11:    char plid
     * 12-15: float speed (m/s)
     * 16-19: float rpm
     * 20-23: float turbo (BAR)
     * 24-27: float engTemp (C)
     * 28-31: float fuel (0-1)
     * 32-35: float oilPressure (BAR)
     * 36-39: float oilTemp (C)
     * 40-43: unsigned dashLights
     * 44-47: unsigned showLights
     * 48-51: float throttle (0-1)
     * 52-55: float brake (0-1)
     * 56-59: float clutch (0-1)
     * 60-75: char display1[16]
     * 76-91: char display2[16]
     * 92-95: int id
     * 96-99: unsigned odometer
     */
    public Receivepacket(byte[] data, int length) {
        if (length < MIN_PACKET_LENGTH) {
            Log.w("Receivepacket", "Packet too short: " + length + " bytes (need " + MIN_PACKET_LENGTH + ")");
            return;
        }

        try {
            ByteBuffer bb = ByteBuffer.wrap(data);
            time = Integer.reverseBytes(bb.getInt(0));
            flags = EndianUtils.readSwappedUnsignedShort(data, 8);
            gear = bb.get(10);
            speed = EndianUtils.readSwappedFloat(data, 12);
            rpm = EndianUtils.readSwappedFloat(data, 16);
            engTemp = EndianUtils.readSwappedFloat(data, 24);
            fuel = EndianUtils.readSwappedFloat(data, 28);
            throttle = EndianUtils.readSwappedFloat(data, 48);
            brake = EndianUtils.readSwappedFloat(data, 52);
            dashLights = EndianUtils.readSwappedInteger(data, 40);
            showLights = EndianUtils.readSwappedInteger(data, 44);
            id = EndianUtils.readSwappedInteger(data, 92);
            odometer = length >= 100 ? EndianUtils.readSwappedInteger(data, 96) : 0;
            bb.clear();
            valid = true;
        } catch (Exception e) {
            Log.e("Receivepacket", "Failed to parse packet", e);
        }
    }

    public boolean isValid() {
        return valid;
    }

    public boolean[] getFlagsArray(){
        // Reset flags first to avoid sticky-true bug
        for (int i = 0; i < flagsarray.length; i++) {
            flagsarray[i] = false;
        }
        if ((flags & FLAG_KMH) == FLAG_KMH) {
            flagsarray[3] = true;
        }
        return flagsarray;
    }

    public boolean[] getDashUsedArr(){
        return dasharray;
    }

    public boolean[] getActiveLightsArr(){
        // Reset all lights first (fixes bug where lights never turn off)
        for (int i = 0; i < lightsArray.length; i++) {
            lightsArray[i] = false;
        }

        if((showLights & FLAG_SHIFTLIGHT) == FLAG_SHIFTLIGHT) {
            lightsArray[0] = true;
        }
        if((showLights & FLAG_FULLBEAM) == FLAG_FULLBEAM) {
            lightsArray[1] = true;
        }
        if((showLights & FLAG_HANDBREAK) == FLAG_HANDBREAK){
            lightsArray[2] = true;
        }
        if((showLights & FLAG_SIGNAL_L) == FLAG_SIGNAL_L) {
            lightsArray[5] = true;
        }
        if((showLights & FLAG_SIGNAL_R) == FLAG_SIGNAL_R) {
            lightsArray[6] = true;
        }
        // FLAG_SIGNAL_ANY (128) intentionally not mapped: BeamNG never sets it (N/A in game source).
        if((showLights & FLAG_OILWARN) == FLAG_OILWARN) {
            lightsArray[8] = true;
        }
        if((showLights & FLAG_BATTERY) == FLAG_BATTERY) {
            lightsArray[9] = true;
        }
        if((showLights & FLAG_ABS) == FLAG_ABS) {
            lightsArray[10] = true;
        }

        return lightsArray;
    }

    public String getGear(){
        switch (gear){
            case 0:  gearStr = "R"; break;
            case 1:  gearStr = "N"; break;
            case 2:  gearStr = "1"; break;
            case 3:  gearStr = "2"; break;
            case 4:  gearStr = "3"; break;
            case 5:  gearStr = "4"; break;
            case 6:  gearStr = "5"; break;
            case 7:  gearStr = "6"; break;
            case 8:  gearStr = "7"; break;
            case 9:  gearStr = "8"; break;
            case 10: gearStr = "9"; break;
            case 11: gearStr = "10"; break;
            default: gearStr = "?"; break;
        }
        return gearStr;
    }

    public float getSpeed(){
        return speed;
    }

    public float getRPM(){
        return rpm;
    }

    public float getEngineTemp(){
        return engTemp;
    }

    public float getFuel(){
        return fuel;
    }

    public float getThrottle() { return throttle; }

    public float getBrake() { return brake; }

    public int getOdometer() { return odometer; }

    public int getID() { return id; }
}
