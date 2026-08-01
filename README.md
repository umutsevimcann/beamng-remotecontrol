BeamNG.drive Remote Control
===========================

Turn your Android phone into a steering wheel, controller and live dashboard for
the PC game [BeamNG.drive](http://beamng.com/), over your local Wi-Fi.

A modernized, actively maintained fork of the (archived) official Remote Control
app. **v3.0** is a complete rewrite — 100% Kotlin, a new Jetpack Compose UI, and a
live dashboard that works again on current game versions with no mods. The latest
release adds **Auto-Connect**: since BeamNG v0.39 the in-game QR is too dense for
most phone cameras to read, so the app now finds the game over Wi-Fi and pairs
with no QR and no camera.

➡️ **[Download the latest APK](https://github.com/umutsevimcann/beamng-remotecontrol/releases/latest)** · Android 5.0+ (minSdk 21)

![Screenshot](action.png)

## Features

* **Auto-Connect** — camera-free pairing over Wi-Fi; no QR needed, works on any
  phone (scanning the QR or entering the code by hand are still available).
* **Steering, throttle & brake** from the phone — button, slider or gyroscope (tilt) modes.
* **Live dashboard** using the game's built-in OutGauge stream (no mod required):
  speedometer, animated RPM gauge with redline, gear, fuel, engine temperature,
  turbo boost (auto-shown on turbo cars), and real dashboard warning lights
  (turn signals, high beam, handbrake, ABS, TC, oil, battery).
* **Drift meter** — live slip angle above the speedometer plus a session record
  (uses the game's MotionSim stream).
* **0-100 timer** — optional; times launches from standstill and keeps your best.
* **Dashboard-only mode** — hide the controls and use the phone as a pure gauge
  cluster (great with a wheel/keyboard).
* **Crash & gear haptics** — vibration on impacts, gear changes and hard braking.
* **Auto-reconnect** — connect once; the app re-registers with the game after
  being backgrounded.
* **In-app Setup Guide** — a personalized how-to that shows *your* phone's IP and
  the exact ports, so nothing gets mistyped into the game.
* **Languages** — English, Türkçe, Deutsch, with an in-app switcher.

## Setup Guide (for players)

> The app has a built-in **Setup Guide** screen that shows your exact IP and
> ports. The steps below are the same, for reference.

### 1. Connect the controls (required)

1. Install the APK on your Android phone (see [Releases](https://github.com/umutsevimcann/beamng-remotecontrol/releases)).
2. Make sure the phone and the PC are on the **same Wi-Fi network**.
3. In BeamNG.drive open **Options → Controls → Hardware** and set **"Use your
   phone or tablet"** to **Yes**.
4. In the app tap **AUTO-CONNECT** — no QR needed. Drive!

> Prefer scanning? **SCAN QR CODE** and **READ QR FROM A PHOTO** are still there,
> and you can type the 5-digit code by hand. The in-app Setup Guide explains each
> method, including where to find that code.

### 2. Live dashboard (optional — speed / RPM / gear / lights)

Uses BeamNG's built-in **OutGauge** stream, no mod required:

1. Open the app — the start screen (and Setup Guide) shows **your phone's IP**.
2. In BeamNG.drive (v0.39+): **Options → Advanced → Protocols**, tick **OutGauge support** and set:
   * **Address** = the IP shown in the app
   * **Port** = `4445`
   * **Max update rate** = `60`
3. **Reload the vehicle once (Ctrl+R)** — the game reads this when the car spawns.
4. The app's status turns green ("Connected") and the gauges come alive.

### 3. Drift meter (optional)

Same place as OutGauge — in **Options → Advanced → Protocols**, tick **MotionSim
enabled** and set the same IP with **Port `4446`**, then Ctrl+R.

### Troubleshooting

* **Gauges frozen?** Re-check the IP in the app (routers reassign it), then Ctrl+R.
* **Nothing arrives?** Allow BeamNG.drive through the Windows Firewall, and confirm
  phone + PC share the same network (guest Wi-Fi often isolates devices).
* **Throttle/brake feel on/off rather than gradual?** That's stock game behavior —
  it treats them as buttons. True analog pedals need a companion PC mod (planned,
  separate download).
* **Auto-Connect stuck on "Searching"?** Update to the latest version — older
  builds could route over mobile data instead of Wi-Fi. Also confirm the phone
  and PC share the same network (guest/hotel Wi-Fi often isolates devices).
* **Updating from an older version?** v3.0.x installs over the top. Coming from a
  2.x build (a different signing key)? Uninstall the old app first, then install.

## Building from source

Requires **JDK 17+**, **Android SDK 35**, and Android Studio (Koala or newer).

1. Clone the repository.
2. Create `Android/Udpsteering/local.properties` with your SDK path:
   `sdk.dir=/path/to/Android/Sdk`
3. Build a debug APK:
   ```
   cd Android/Udpsteering
   ./gradlew assembleDebug
   ```
4. Run the wire-protocol unit tests: `./gradlew testDebugUnitTest`

**Release builds** read signing config from `local.properties` (never committed):
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`. Without them, `assembleRelease` falls back to the debug
key (for local testing only).

### Tech stack

* 100% **Kotlin** (2.4), **Jetpack Compose** UI (Material 3), coroutines for the
  UDP networking.
* Gradle 8.11, AGP 8.10, minSdk 21 / target & compile SDK 35.
* Lightweight architecture: `protocol/` (pure Kotlin, unit-tested wire format),
  `network/`, `settings/`, `input/` (steering strategy pattern), `ui/` (Compose).

## Protocol reference

All communication is UDP on the local network.

* **Discovery:** app broadcasts `beamng|<device-name>|<code>` on port **4444**;
  the game replies `beamng|<code>` on port **4445**. The `<code>` comes from the
  QR, or Auto-Connect finds it by sweeping the game's 5-digit code space until the
  game answers. The drive screen re-sends this periodically to survive the game's
  10s idle timeout.
* **Control (app → game, port 4444):** 16 bytes, four Big-Endian floats —
  steering (0 = right … 1 = left), throttle 0..1, brake 0..1, packet id.
  The stock game thresholds throttle/brake at 0.5 (on/off); true analog needs the
  companion mod.
* **Telemetry (game → app, port 4445):** the standard 96-byte OutGauge struct,
  sent by BeamNG's built-in `protocols_outgauge` (Options → Advanced → Protocols).
  A companion mod may append a 4-byte odometer (100 bytes).
* **Motion (game → app, port 4446):** the MotionSim struct (`BNG1` magic), used
  for the drift meter.

### OutGauge struct (Little-Endian)

| type           | name        | description                                   | bytes |
|----------------|-------------|-----------------------------------------------|-------|
| unsigned       | time        | milliseconds (0 in-game)                      | 0-3   |
| char[4]        | car         | car name ("beam")                             | 4-7   |
| unsigned short | flags       | info bits (OG_x below)                         | 8-9   |
| char           | gear        | Reverse:0, Neutral:1, First:2 …               | 10    |
| char           | plid        | viewed player id (0)                          | 11    |
| float          | speed       | m/s                                           | 12-15 |
| float          | rpm         | RPM                                           | 16-19 |
| float          | turbo       | BAR                                           | 20-23 |
| float          | engTemp     | °C                                            | 24-27 |
| float          | fuel        | 0..1                                          | 28-31 |
| float          | oilPressure | BAR (0 in-game)                               | 32-35 |
| float          | oilTemp     | °C                                            | 36-39 |
| unsigned       | dashLights  | lights available                             | 40-43 |
| unsigned       | showLights  | lights currently on (DL_x below)              | 44-47 |
| float          | throttle    | 0..1                                          | 48-51 |
| float          | brake       | 0..1                                          | 52-55 |
| float          | clutch      | 0..1                                          | 56-59 |
| char[16]       | display1    | (empty in-game)                               | 60-75 |
| char[16]       | display2    | (empty in-game)                               | 76-91 |
| int            | id          | 0 in the stock game                           | 92-95 |
| unsigned       | odometer    | optional (companion mod), meters              | 96-99 |

```
// OG_x — flags
OG_TURBO   8192    // show turbo gauge (set only on turbo cars)
OG_KM      16384   // if not set, user prefers MILES

// DL_x — showLights bits
DL_SHIFT     1      DL_FULLBEAM  2      DL_HANDBRAKE 4
DL_TC        16     DL_SIGNAL_L  32     DL_SIGNAL_R  64
DL_OILWARN   256    DL_BATTERY   512    DL_ABS       1024
```

## License

ISC — see [LICENSE](LICENSE). Original app © 2014 BeamNG GmbH; this fork continues
under the same license.
