BeamNG.drive Remote Control
===========================

Turn your Android phone into a steering wheel, controller and live dashboard for
the PC game [BeamNG.drive](http://beamng.com/), over your local Wi-Fi.

A modernized, actively maintained fork of the (archived) official Remote Control
app. **v3.0** is a complete rewrite — 100% Kotlin, a new Jetpack Compose UI, and a
live dashboard that works again on current game versions with no mods.
**v3.0.3** redraws the whole app (a steering wheel you can actually turn, four
dashboard designs, light and dark themes, nine languages) and adds an optional
game mod, **RemoteControlPro**, for code-free pairing, gear shifting, handbrake,
horn and lights.
**v3.0.4** adds force-feedback steering (let go of the wheel and the car turns
it), eight steering-wheel designs, four more dashboards, and keys for drive
modes, 4WD, nitrous, doors, camera, gearbox mode and pause.
**v3.0.5** adds **walking** (with mod 2.3): get out of the car and the phone turns
into a walking screen (stick, drag to look, jump, crouch, interact, zoom), plus
finer vibration and more choices for how the controls work.
**v3.0.6** adds **menu control** (with mod 2.4): open the game's menu and steer it
from the phone like a gamepad.

➡️ **[Download the latest APK](https://github.com/umutsevimcann/beamng-remotecontrol/releases/latest)** · Android 5.0+ (minSdk 21)

![Screenshot](action.png)

## Features

* **Auto-Connect** — camera-free pairing over Wi-Fi; no QR needed, works on any
  phone (scanning the QR or entering the code by hand are still available).
* **Steering, throttle & brake** from the phone — tilt the phone, turn an on-screen
  wheel, or use arrow keys. Steering is linear: the app cancels the game's own
  steering curve, so a small turn is a small turn.
* **Eight dashboard designs** — Modern, Classic, Race, Minimal, Retro (LCD
  digits), Cockpit (aircraft tapes), Drift (the car from above) and Telemetry
  (a rolling graph) — plus light, dark and follow-the-system themes, and a
  mirrored layout for left-handers.
* **Eight steering wheels** — Sport, GT, Formula, Classic, Drift, Truck, Yoke
  and Retro.
* **Your choice of layout** — pedals side by side or one per thumb (tilt
  steering), handbrake held or latched, horn under the wheel, in its centre or
  both, every mod key switched on or off on its own.
* **RemoteControlPro mod (optional)** — pairs in about a second with no code, and
  adds shift up/down, handbrake, horn, headlights, fog lights, turn signals,
  hazards and recover, analog pedals, force-feedback steering, keys for drive
  mode / ESC, 4WD, low range, diff lock, nitrous, doors, camera, gearbox mode,
  reset, pause, map and interact, a rev counter and steering wheel that match
  the car you are driving, and a **walking screen** on foot. See [The optional mod](#the-optional-mod-remotecontrolpro).
* **Live dashboard** using the game's built-in OutGauge stream (no mod required):
  speedometer, animated RPM gauge with redline, gear, fuel, engine temperature,
  turbo boost (auto-shown on turbo cars), and real dashboard warning lights
  (turn signals, high beam, handbrake, ABS, TC, oil, battery).
* **Drift meter** — live slip angle above the speedometer plus a session record
  (uses the game's MotionSim stream).
* **0-100 timer** — optional; times launches from standstill and keeps your best.
* **Dashboard-only mode** — hide the controls and use the phone as a pure gauge
  cluster (great with a wheel/keyboard).
* **Vibration** — crashes (read from the car's body acceleration, stronger the
  harder the hit), hard landings, gear changes, ABS, traction control / ESC and
  the rev limiter; each one switched on or off on its own.
* **Auto-reconnect** — connect once; the app re-registers with the game after
  being backgrounded.
* **In-app Setup Guide** — a personalized how-to that shows *your* phone's IP and
  the exact ports, so nothing gets mistyped into the game.
* **Languages** — English, Türkçe, Deutsch, Русский, Українська, Polski, Français,
  Português (BR) and Español, switched in the app. (The six newest are unreviewed
  by native speakers — corrections are welcome.)

## Setup Guide (for players)

> The app has a built-in **Setup Guide** screen that shows your exact IP and
> ports. The steps below are the same, for reference.

### 1. Connect the controls (required)

1. Install the APK on your Android phone (see [Releases](https://github.com/umutsevimcann/beamng-remotecontrol/releases)).
2. Make sure the phone and the PC are on the **same Wi-Fi network**.
3. In BeamNG.drive open **Options → Controls → Hardware** and set **"Use your
   phone or tablet"** to **Yes**.
4. In the app tap **CONNECT** — no QR needed. Drive!

> On **2.4 GHz Wi-Fi** the very first search can take 2–3 minutes: the app has to
> try the game's 5-digit code space slowly, or the router drops the game's answer.
> After that it remembers the PC and reconnects in seconds. On 5 GHz, or with the
> mod installed, it is near-instant.

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

### The optional mod: RemoteControlPro

The app works without it. Add it and the phone can do what the game's own phone
remote cannot:

| Without the mod | With the mod |
|---|---|
| Steering, gas, brake | + shift up/down, handbrake, horn, lights, fog lights, signals, hazards, recover |
| Pairing searches for the game's code (first time: up to 2–3 min on 2.4 GHz) | Pairs in about a second, no code, no QR |
| Pedals are on/off | Analog pedals |
| Rev counter guesses the redline; on-screen wheel always turns 450° | Both match the car you are driving |
| Let go of the on-screen wheel and it returns by estimate | The car turns it with the game's own force feedback, as on a wheel set |
| — | Drive mode / ESC, 4WD, low range, diff lock, nitrous, doors (shown only on cars that have them) |
| — | Camera, automatic/manual gearbox, reset, pause, big map, interact (start missions and jobs) |
| — | Drag the gauges to look around from the car |
| — | **Menu control**: open the game's menu and steer it from the phone like a gamepad (D-pad, select, back, tabs) |
| — | **Walking screen**: get out and walk with a stick, drag (or turn the phone) to look, jump, crouch, zoom, get back in |

Install:

1. Download `remotecontrolpro_umutsevimcann.zip` from the
   [latest release](https://github.com/umutsevimcann/beamng-remotecontrol/releases/latest).
2. Move the zip, **without extracting it**, into the game's mods folder:
   `%LOCALAPPDATA%\BeamNG\BeamNG.drive\current\mods`
3. Start the game and tap **CONNECT** in the app. A **MOD** label on the drive
   screen shows the link is active.

The mod follows BeamNG's modding rules: it replaces no game file, runs next to the
stock phone remote on its own UDP port (4450) with its own input device, and is
removed by deleting the zip. The gauges still come from OutGauge (step 2 above) —
the mod adds controls, not gauges. Source: [`BeamNG-Mod/`](BeamNG-Mod/).

#### Walking (mod 2.3 or newer)

Tap **WALK** on the game page of the key row (or press the game's own key) and
the phone switches to a walking screen by itself; get back in and the drive
screen returns.

* **Stick** on the left half (it appears where your thumb lands): walk; push
  it to the edge to sprint, or use a separate sprint key.
* **Drag the right half** to look around, like a mouse. Optionally turn the
  phone to aim, and invert the up/down look.
* **Keys**: jump, crouch (held or latched), use / interact, zoom (like
  binoculars), get back in, and the map.
* **Left-handed?** Put the stick on the right in Settings → Walking.

The game's own phone remote cannot walk; all of this needs the mod.

### Troubleshooting

* **Gauges frozen?** Re-check the IP in the app (routers reassign it), then Ctrl+R.
* **Nothing arrives?** Allow BeamNG.drive through the Windows Firewall, and confirm
  phone + PC share the same network (guest Wi-Fi often isolates devices).
* **Throttle/brake feel on/off rather than gradual?** That's stock game behavior —
  it treats them as buttons. Install the optional mod (above) for analog pedals.
* **Using the phone's hotspot instead of a router?** Supported: connect the PC to
  the hotspot and tap CONNECT.
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
  The stock game thresholds throttle/brake at 0.5 (on/off) and applies a steering
  curve of `input^2.5`, which the app cancels by sending `input^(1/2.5)`.
* **RemoteControlPro mod (port 4450, protocol 1):** hello `RCP?|1|<device>` →
  reply `RCP!|1|<pc-name>|<mod-version>`; control is 28 bytes Little-Endian
  (36 from app 3.0.5: two more floats, the look drag; 40 with mod 2.4: a uint32 of
  menu keys, which the mod passes straight to the game's UI navigation) —
  `"RCP"`, version byte, floats steering (-1..1), throttle, brake, clutch,
  handbrake (0..1), then a uint32 button mask (bit 0 shift up, 1 shift down,
  2 horn, 3 headlights, 4 fog, 5 left signal, 6 right signal, 7 hazards,
  8 recover, 9 reset, 10 drive mode/ESC, 11 4WD, 12 low range, 13 diff lock,
  14 nitrous, 15 nitrous hold, 16 doors open, 17 doors close, 18 camera,
  19 gearbox mode, 20 pause, 21 big map, 22 walking mode, 23 interact,
  24 zoom on foot; bit 31 is a flag, "nobody holds the on-screen
  wheel"). On foot the mod reads the same axes as walking: forward = throttle −
  brake, sideways = steering, handbrake = sprint, shift up = jump, shift down =
  crouch. The mod sends back
  `RCPV|<maxRpm>|<steeringLockDeg>|<vehicle>|<features>` about twice a second
  (features: 1 drive modes/ESC, 2 4WD, 4 nitrous) and, while the wheel is
  free, `RCPS|<angle>` about 30 times a second: the angle its force feedback
  has turned the wheel to. The hello may end with `|<wheelReturn%>` (20..200).
  `RCPW|1` / `RCPW|0` says the player got out of the car / back in; the
  phone swaps screens on it.
* **Telemetry (game → app, port 4445):** the standard 96-byte OutGauge struct,
  sent by BeamNG's built-in `protocols_outgauge` (Options → Advanced → Protocols).
  A companion mod may append a 4-byte odometer (100 bytes).
* **Motion (game → app, port 4446):** the MotionSim struct (`BNG1` magic), used
  for the drift meter, the released-wheel estimate and crash / landing vibration
  (body acceleration at bytes 28–39, gravity excluded).

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
