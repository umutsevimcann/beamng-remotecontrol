BeamNG.drive Remote Control (Modernized)
=====================================

Modernized fork of the official Remote Control app for the PC game [BeamNG.drive](http://beamng.com/).

### Modernization Changes (2026)
*   **AndroidX Migration:** Fully migrated from old Support Libraries to AndroidX.
*   **Target SDK 34:** Updated to support Android 14.
*   **Modern QR Scanner:** Replaced outdated ZXing library with `zxing-android-embedded`.
*   **Gradle 8.7:** Updated build system and dependencies to latest stable versions.
*   **Aesthetic & UI Fixes:** Improved UI handling and removed obsolete Google App Indexing code.

## Setup Guide (for players)

### 1. Connect the controls (steering / throttle / brake)

1. Install the APK on your Android phone (see Releases).
2. Make sure the phone and the PC are on the **same Wi-Fi network**.
3. In BeamNG.drive open **Options → Controls → Hardware** and enable the Remote Control QR code.
4. In the app tap **SCAN QR CODE** and scan it. Drive!

### 2. Enable the live dashboard (speed / RPM / gear / lights)

The dashboard uses BeamNG's built-in **OutGauge** stream — no mod required:

1. Open the app's start screen — it shows **your phone's IP address** (e.g. `192.168.0.23`).
2. In BeamNG.drive go to **Options → Other**, scroll to **Protocols** and tick **OutGauge support**.
3. Fill in:
   * **Address** = the IP shown in the app
   * **Port** = `4445`
   * **Max update rate** = `60`
4. **Reload your vehicle once (Ctrl+R)** — the setting is read when the vehicle loads.
5. The app's status turns green ("Connected") and the gauges come alive.

**Troubleshooting**
* Gauges frozen? Re-check the IP in the app (it changes when your router reassigns it), then Ctrl+R.
* Nothing at all? Allow BeamNG.drive through the Windows Firewall, and confirm phone + PC share the same network (guest Wi-Fi networks often isolate devices).
* Throttle/brake feel on/off rather than gradual? That's the stock game behavior (it treats them as buttons). True analog pedals need the companion PC mod — separate download, optional.

### How to Build
1. Clone the repository.
2. Open the project in **Android Studio (Iguana or newer)**.
3. Ensure you have Android SDK 34 installed.
4. Create a `local.properties` file in `Android/Udpsteering/` with your `sdk.dir`.
5. Run `./gradlew assembleDebug`.

![Screenshot](action.png)


### Communication functionality ###

*   App broadcasts `beamng|<device-name>|<code>` on port 4444 (`<code>` comes from the QR).
*   The game replies `beamng|<code>` to port 4445 to confirm the connection.
*   Control packets (app → game, port 4444) are 16 bytes, four Big-Endian floats:
    * Steering-angle between 0 (right) and 1 (left)
    * Throttle 0..1 (stock game thresholds at 0.5 → on/off; analog needs the companion mod)
    * Brakes 0..1 (same threshold behavior)
    * Packet id (float; echoed back only by the companion mod for latency measurement)
*   Telemetry (game → app, port 4445) is the standard 96-byte OutGauge struct sent by
    BeamNG's built-in `protocols_outgauge` when enabled in Options → Other; the
    companion mod may append a 4-byte odometer (100 bytes total).
*   App needs following structure for incoming packages:  

type              | name          | description                                      | bytes  
----------------- | ------------- | ------------------------------------------------ | -------------
unsigned          | time          | time in milliseconds (to check order)            | 0-3  
char              | car[4]        | Car name                                         | 4-7  
unsigned short    | flags         | Info (see OG_x below)                            | 8-9  
char              | gear          | Reverse:0, Neutral:1, First:2...                 | 10  
char              | plid          | Unique ID of viewed player (0 = none)            | 11  
float             | speed         | m/s                                              | 12-15  
float             | rpm           | RPM                                              | 16-19  
float             | turbo         | BAR                                              | 20-23  
float             | engTemp       | C                                                | 24-27  
float             | fuel          | 0 to 1                                           | 28-31  
float             | oilPressure   | BAR                                              | 32-35  
float             | oilTemp       | C                                                | 36-39  
unsigned          | dashLights    | not used                                         | 40-43
unsigned          | showLights    | Dash lights currently switched on                | 44-47  
float             | throttle      | 0 to 1                                           | 48-51  
float             | brake         | 0 to 1                                           | 52-55  
float             | clutch        | 0 to 1                                           | 56-59  
char              | display1[16]  | Usually Fuel                                     | 60-75  
char              | display2[16]  | Usually Settings                                 | 76-91  
int               | id            | 0 in the stock game; echoed id with companion mod | 92-95  
unsigned          | odometer      | optional (companion mod only), meters             | 96-99  


    // OG _x - bits for Flags  
    OG_KM         16384    // if not set - user prefers MILES  

    // DL _x - bits for ShowLights  
    DLSHIFT,           // bit 0    - shift light  
    DLFULLBEAM,        // bit 1    - full beam  
    DLHANDBRAKE,       // bit 2    - handbrake  
    DLPITSPEED,        // bit 3    - pit speed limiter                            //not used  
    DLTC,              // bit 4    - TC active or switched off                    //not used  
    DLSIGNALL,         // bit 5    - left turn signal  
    DLSIGNALR,         // bit 6    - right turn signal  
    DLSIGNALANY,       // bit 7    - shared turn signal  
    DLOILWARN,         // bit 8    - oil pressure warning                         //not used  
    DLBATTERY,         // bit 9    - battery warning                              //not used  
    DLABS,             // bit 10   - ABS active or switched off  
    DLSPARE,           // bit 11                                                  //not used  
