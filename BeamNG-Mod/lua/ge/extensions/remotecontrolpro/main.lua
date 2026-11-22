-- RemoteControlPro — companion mod for the "BeamNG Remote" Android app.
-- Author: umutsevimcann. License: ISC.
--
-- Loads as the GE extension "remotecontrolpro_main". It does not touch the
-- game's own remote controller (core_remoteController, UDP 4444): it runs next
-- to it on its own port, with its own virtual input device and its own inputmap
-- (settings/inputmaps/bngremotectrlprov2.json).
--
-- What it adds over the stock phone remote:
--   * pairing without a code — the phone asks, the mod answers
--   * analog throttle / brake / clutch / handbrake
--   * buttons: shift up/down, horn, lights, fog lights, signals, hazards, recover,
--     drive mode / ESC, 4WD, low range, diff lock, nitrous, door latches,
--     camera, gearbox mode (automatic / manual), pause, big map, walking mode,
--     interact (start missions and jobs), zoom on foot
--   * look around: the phone's drag turns the camera, in the car and on foot
--   * walking: on foot, the same phone walks the character — gas forward, brake
--     back, wheel left/right, shift up jumps, shift down crouches, handbrake sprints
--   * a force-feedback steering wheel: let go of the on-screen wheel and the car
--     turns it (self-centring, counter-steer in a slide), through the game's own
--     virtual-wheel hook (hydros.enableVirtualWheel)
--   * per-vehicle data sent back: rev limit, steering-wheel lock, and which of the
--     optional systems (drive modes, 4WD, nitrous) the car has
--
-- Wire protocol (version 1), UDP port 4450; replies go to the sender's address:
--   phone -> mod   "RCP?|1|<deviceName>[|<wheelReturn>]"  hello (broadcast ok), ~every 3 s
--                    wheelReturn (2.2): 20..200, percent of the force feedback that
--                    turns the released wheel (the "FFB strength" of a wheel set)
--   mod   -> phone "RCP!|1|<pcName>|<modVersion>"         answer to every hello
--   phone -> mod   28 or 36 bytes, little-endian          control, ~100 Hz
--                    char[3] "RCP", uint8 version (1)
--                    float steering   -1 (left) .. 1 (right)
--                    float throttle, brake, clutch, handbrake   0 .. 1
--                    uint32 buttons   (bit n = button n held)
--                    float lookX, lookY (2.3, optional): look drag since the last packet
--   mod   -> phone "RCPW|<0|1>"     the player is on foot (2.3): sent on every change
--                    and with the vehicle info
--   mod   -> phone "RCPV|<maxRpm>|<steeringLockDeg>|<vehicleName>|<features>"   ~2 Hz while driving
--                    features: 1 drive modes/ESC, 2 4WD, 4 nitrous (added in mod 2.1;
--                    older apps ignore the extra field)
--   mod   -> phone "RCPS|<angle>"   ~30 Hz while the phone's wheel is free (mod 2.2)
--                    Control buttons bit 31 = "nobody holds the on-screen wheel". The
--                    mod then lets the game's own force feedback turn the wheel, as it
--                    would a wheel set, and reports the angle, -1 (left) .. 1 (right).
-- A phone that stays silent for 10 s is unplugged.

local M = {}

local logTag = 'remotecontrolpro'
local MOD_VERSION = '2.3.0'
local PROTOCOL = 1
local PORT = 4450
local DEVICE_TYPE = 'bngremotectrlprov2' -- must match the inputmap file name
local N_AXES = 5      -- steering, throttle, brake, clutch, handbrake
local N_BUTTONS = 25  -- see BUTTON order in the Android app (ModProtocol.kt)
local TIMEOUT_MS = 10000
local INFO_PERIOD = 0.5
local FLAG_WHEEL_FREE = 0x80000000 -- control buttons bit 31
local BIT_SHIFT_UP, BIT_SHIFT_DOWN = 0, 1
local BIT_MAP = 21
local BIT_VIEW = 24
local WALK_PERIOD = 0.033 -- seconds between walking updates sent to the character

local min, max = math.min, math.max

local ffi = require_optional('ffi')
local bit = require_optional('bit')
if ffi then
  -- pcall: a Lua reload must not fail on "attempt to redefine".
  pcall(ffi.cdef, [[
  #pragma pack(push, 1)
  typedef struct {
    char magic[3];
    uint8_t version;
    float steering, throttle, brake, clutch, handbrake;
    uint32_t buttons;
  } rcp_control_t;
  typedef struct { float lookX, lookY; } rcp_look_t;
  #pragma pack(pop)
  ]])
end

local udpSocket = nil
local stopWalking -- defined with the walking code below; unplug needs it first
-- phones by ip: { ip, port, name, deviceInst, lastSeen, axes = {}, buttons = 0,
--                 wheelFree, wheelVeh (vehicle id the virtual wheel is installed on) }
-- One device per phone, whatever socket it talks from: the app pairs from one
-- UDP port and drives from another, and gets a new one on every reconnect.
local phones = {}
local infoTimer = 0
-- latest vehicle info per vehicle id: "RCPV|..." ready to send
local vehicleInfo = {}

local function clamp01(v)
  if v ~= v then return 0 end -- NaN
  return min(1, max(0, v))
end

local function pcName()
  return (os.getenv('COMPUTERNAME') or os.getenv('HOSTNAME') or 'PC'):gsub('[|\r\n]', '_')
end

local function notify(title, msg)
  guihooks.trigger('toastrMsg', {type = 'info', title = title, msg = msg, config = {timeOut = 4000}})
end

local function openSocket()
  if udpSocket then return true end
  local s = socket.udp()
  if not s then return false end
  if s:setsockname('*', PORT) == nil then
    log('W', logTag, 'unable to open UDP port ' .. PORT .. ' — is another instance running?')
    s:close()
    return false
  end
  s:settimeout(0)
  udpSocket = s
  log('I', logTag, 'listening on UDP ' .. PORT .. ' (mod ' .. MOD_VERSION .. ', protocol ' .. PROTOCOL .. ')')
  return true
end

-- Runs in the vehicle's own Lua. hydros.enableVirtualWheel is the game's hook for a
-- wheel that is simulated instead of plugged in: the game computes the force-feedback
-- torque exactly as for a wheel set and hands it to our function, and asks our
-- other function for the wheel angle every frame.
--   held: the angle is the phone's own steering (what the game already has).
--   free: the wheel is a mass that the torque turns; its angle is the steering.
local VEHICLE_WHEEL_ON = [[
if not rcpWheel then
  -- Start from where the steering is now: the first command may already say "free".
  local start = electrics.values.steering_input or 0
  if start ~= start then start = 0 end
  local w = {free = false, angle = start, vel = 0, t = math.huge, sent = 0,
             inertia = 0.35, damping = 0.9, gain = 1}
  rcpWheel = w
  local clock = os.clockhp or os.clock
  w.force = function(o, id, torque)
    local t = clock()
    local dt = math.min(math.max(t - w.t, 0), 0.05)
    w.t = t
    if not w.free or torque ~= torque then return end
    w.vel = w.vel + dt * (-torque * w.gain - w.damping * w.vel) / w.inertia
    local a = math.min(1, math.max(-1, w.angle + dt * w.vel))
    if a == 1 or a == -1 then w.vel = 0 end
    w.angle = a
    if t - w.sent > 0.033 then
      w.sent = t
      obj:queueGameEngineLua(string.format(
        'if remotecontrolpro_main then remotecontrolpro_main.onWheelAngle(%d, %.4f) end', obj:getId(), a))
    end
  end
  w.angleFn = function()
    if not w.free then
      local a = electrics.values.steering_input or 0
      if a ~= a then a = 0 end
      w.angle = a
      w.vel = 0
    end
    return w.angle
  end
end
if not rcpWheel.on then
  rcpWheel.on = true
  hydros.enableVirtualWheel(true, rcpWheel.angleFn, rcpWheel.force)
end
]]

local VEHICLE_WHEEL_OFF = [[
if rcpWheel and rcpWheel.on then
  rcpWheel.on = false
  rcpWheel.free = false
  hydros.enableVirtualWheel(false)
end
]]

local function wheelOff(phone)
  if not phone.wheelVeh then return end
  local vehicle = getObjectByID(phone.wheelVeh)
  if vehicle then vehicle:queueLuaCommand(VEHICLE_WHEEL_OFF) end
  phone.wheelVeh = nil
end

local function unplug(key)
  local phone = phones[key]
  if not phone then return end
  wheelOff(phone)
  stopWalking(phone)
  if phone.deviceInst then
    extensions.core_input_virtualInput.deleteDevice(phone.deviceInst)
  end
  phones[key] = nil
  log('I', logTag, 'phone unplugged: ' .. tostring(phone.name))
end

local function plug(key, ip, port, name)
  local deviceInst = extensions.core_input_virtualInput.createDevice(name, DEVICE_TYPE, N_AXES, N_BUTTONS, 0)
  if not deviceInst or deviceInst < 0 then
    log('E', logTag, 'unable to create the virtual input device')
    return nil
  end
  phones[key] = {ip = ip, port = port, name = name, deviceInst = deviceInst, axes = {}, buttons = 0}
  log('I', logTag, 'phone connected: ' .. name .. ' (' .. ip .. ')')
  notify('Remote Control Pro', 'Phone connected: ' .. name)
  return phones[key]
end

local function emitAxis(phone, index, value)
  if phone.axes[index] == value then return end
  phone.axes[index] = value
  extensions.core_input_virtualInput.emit(phone.deviceInst, 'axis', index, 'change', value)
end

local function handleControl(phone, data)
  local pkt = ffi.new('rcp_control_t')
  ffi.copy(pkt, data, ffi.sizeof(pkt))
  if pkt.version ~= PROTOCOL then return end

  local steering = pkt.steering
  if steering ~= steering then steering = 0 end
  -- The inputmap binds the steering axis centred: 0 = full left, 1 = full right.
  phone.steer = min(1, max(-1, steering))
  phone.throttle = clamp01(pkt.throttle)
  phone.brake = clamp01(pkt.brake)
  phone.handbrake = clamp01(pkt.handbrake)
  emitAxis(phone, 0, (min(1, max(-1, steering)) + 1) * 0.5)
  emitAxis(phone, 1, clamp01(pkt.throttle))
  emitAxis(phone, 2, clamp01(pkt.brake))
  emitAxis(phone, 3, clamp01(pkt.clutch))
  emitAxis(phone, 4, clamp01(pkt.handbrake))

  if #data >= 36 then
    local look = ffi.new('rcp_look_t')
    ffi.copy(look, data:sub(29, 36), 8)
    local lx, ly = look.lookX, look.lookY
    -- Summed until the next frame applies them; NaN and absurd values are dropped.
    if lx == lx and ly == ly and math.abs(lx) < 5000 and math.abs(ly) < 5000 then
      phone.lookX = (phone.lookX or 0) + lx
      phone.lookY = (phone.lookY or 0) + ly
    end
  end

  local buttons = tonumber(pkt.buttons)
  phone.wheelFree = buttons >= FLAG_WHEEL_FREE
  -- The map key is handled here, not through the inputmap: once the big map is open
  -- the game blocks the action that opened it, so a bound key could never close it.
  local mapMask = bit.lshift(1, BIT_MAP)
  if bit.band(buttons, mapMask) ~= 0 and bit.band(phone.buttons, mapMask) == 0 and freeroam_bigMapMode then
    freeroam_bigMapMode.toggleBigMap()
  end
  -- Zoom on foot: done in onUpdate, which knows the player.
  local viewMask = bit.lshift(1, BIT_VIEW)
  if bit.band(buttons, viewMask) ~= 0 and bit.band(phone.buttons, viewMask) == 0 then phone.viewToggle = true end
  if buttons ~= phone.buttons then
    for i = 0, N_BUTTONS - 1 do
      local mask = bit.lshift(1, i)
      local now = bit.band(buttons, mask) ~= 0
      local was = bit.band(phone.buttons, mask) ~= 0
      if now ~= was then
        extensions.core_input_virtualInput.emit(phone.deviceInst, 'button', i, now and 'down' or 'up', now and 1 or 0)
      end
    end
    phone.buttons = buttons
  end
end

local function receive(now)
  while true do
    local data, ip, port = udpSocket:receivefrom(256)
    if not data then return end
    local key = ip

    if data:sub(1, 4) == 'RCP?' then
      local args = split(data, '|')
      if tonumber(args[2]) == PROTOCOL then
        local name = (args[3] and args[3] ~= '') and args[3]:sub(1, 48) or 'Phone'
        local phone = phones[key] or plug(key, ip, port, name)
        if phone then
          phone.lastSeen = now
          phone.port = port
          local gain = min(2, max(0.2, (tonumber(args[4]) or 100) / 100))
          if gain ~= gain then gain = 1 end
          if gain ~= phone.wheelGain then
            phone.wheelGain = gain
            phone.wheelSent = nil -- tell the vehicle
          end
          udpSocket:sendto('RCP!|' .. PROTOCOL .. '|' .. pcName() .. '|' .. MOD_VERSION, ip, port)
        end
      end
    elseif #data >= 28 and data:sub(1, 3) == 'RCP' then
      local phone = phones[key]
      if phone then
        phone.lastSeen = now
        phone.port = port -- vehicle info goes to the socket that drives
        handleControl(phone, data)
      end
    end
  end
end

-- Runs in the vehicle's own Lua: the same controller lookups the game's input
-- actions use, so a feature is reported exactly when its key would do something.
local VEHICLE_QUERY = [[
local f = 0
if controller.getController('driveModes') or controller.getController('esc') then f = f + 1 end
if #controller.getControllersByType('4wd') > 0 then f = f + 2 end
if #controller.getControllersByType('nitrousOxideInjection') > 0 then f = f + 4 end
obj:queueGameEngineLua(string.format(
  "if remotecontrolpro_main then remotecontrolpro_main.onVehicleInfo(%d, %f, %f, %d) end",
  obj:getId(), (electrics.values.maxrpm or 0), ((v.data.input and v.data.input.steeringWheelLock) or 0), f))
]]

-- The player a phone drives for, straight from the game's binding table. (The
-- onInputBindingsChanged hook can fire while createDevice is still running,
-- before the phone is in our table, so it is not a reliable source.)
local function playerOf(phone)
  local players = core_input_bindings and core_input_bindings.assignedPlayers
  local player = players and players['vinput' .. phone.deviceInst]
  return (player and player >= 0) and player or nil
end

-- Installs the virtual wheel on the car a phone drives (and takes it off the car
-- it left), then tells it whether the phone is holding the wheel. Only for phones
-- that have ever let go: tilt and arrow-key steering never touch the game's FFB.
local function syncWheel(phone, vehicle)
  if phone.wheelFree then phone.wheelWanted = true end
  if not phone.wheelWanted then return end
  local vehId = vehicle and vehicle:getId() or nil
  if phone.wheelVeh ~= vehId then
    wheelOff(phone)
    phone.wheelVeh = vehId
    phone.wheelSent = nil
  end
  if not vehicle then return end
  if phone.wheelSent ~= phone.wheelFree then
    phone.wheelSent = phone.wheelFree
    vehicle:queueLuaCommand(VEHICLE_WHEEL_ON .. string.format(
      'rcpWheel.gain = %.3f rcpWheel.free = %s', phone.wheelGain or 1, tostring(phone.wheelFree)))
  end
end


-- On foot the player drives an invisible "unicycle" whose playerController reads
-- walk commands, not the car actions our inputmap binds. So while the player a
-- phone drives is walking, the mod turns the phone's controls into those commands.
local function isWalking(player)
  return player ~= nil and gameplay_walk ~= nil and gameplay_walk.isWalking(player) == true
end

local WALK_CMD = "local p = controller.getControllerSafe('playerController') if p then "
  .. "p.walkUpDownRaw(%.3f) p.walkLeftRightRaw(%.3f) p.setSpeedCoef(%d) %s end"

local function walk(phone, vehicle, now)
  local fwd = (phone.throttle or 0) - (phone.brake or 0)
  local side = phone.steer or 0
  local sprint = (phone.handbrake or 0) > 0.5 and 1 or 0
  local buttons = phone.buttons or 0
  local up = bit.band(buttons, bit.lshift(1, BIT_SHIFT_UP)) ~= 0
  local down = bit.band(buttons, bit.lshift(1, BIT_SHIFT_DOWN)) ~= 0
  local extra = ''
  if up and not phone.walkJumpHeld then extra = extra .. 'p.jump(1) ' end
  if down ~= (phone.walkCrouch == true) then extra = extra .. 'p.crouch(' .. (down and -1 or 1) .. ') ' end
  phone.walkJumpHeld = up
  phone.walkCrouch = down
  local state = string.format('%.3f|%.3f|%d', fwd, side, sprint)
  -- An unchanged state is resent now and then in case a command was lost, but only
  -- while moving: a phone lying still must not keep stopping a character that another
  -- device (a second phone, the keyboard) is walking.
  local still = fwd == 0 and side == 0 and sprint == 0
  if extra == '' and state == phone.walkState and (still or now - (phone.walkSent or 0) < 500) then return end
  if extra == '' and now - (phone.walkSent or 0) < WALK_PERIOD * 1000 then return end
  phone.walkState = state
  phone.walkSent = now
  phone.walkVeh = vehicle:getId()
  vehicle:queueLuaCommand(string.format(WALK_CMD, fwd, side, sprint, extra))
end

-- Leaving walking mode (or the phone going away) must not leave the character walking.
function stopWalking(phone)
  if not phone.walkVeh then return end
  local vehicle = getObjectByID(phone.walkVeh)
  if vehicle then vehicle:queueLuaCommand(string.format(WALK_CMD, 0, 0, 0, 'p.crouch(1) ')) end
  phone.walkVeh = nil
  phone.walkState = nil
  phone.walkCrouch = nil
end

local function vehicleOf(phone)
  local player = playerOf(phone)
  return player and getPlayerVehicle(player) or nil
end

local function onWheelAngle(vehId, angle)
  if not udpSocket then return end
  for _, phone in pairs(phones) do
    if phone.wheelVeh == vehId and phone.wheelFree then
      udpSocket:sendto(string.format('RCPS|%.4f', angle), phone.ip, phone.port)
    end
  end
end

-- Asks each driven vehicle for its rev limit and steering lock. The vehicle Lua
-- answers through onVehicleInfo; nothing here blocks on it.
local function requestVehicleInfo()
  for _, phone in pairs(phones) do
    local player = playerOf(phone)
    local vehicle = player and getPlayerVehicle(player) or nil
    -- A reset or reload wipes the vehicle's Lua: install the wheel again.
    phone.wheelSent = nil
    if vehicle then
      vehicle:queueLuaCommand(VEHICLE_QUERY)
      local info = vehicleInfo[vehicle:getId()]
      if info then udpSocket:sendto(info, phone.ip, phone.port) end
      -- Repeated now and then, in case the change itself was lost.
      if phone.walkingSent ~= nil then udpSocket:sendto(phone.walkingSent and 'RCPW|1' or 'RCPW|0', phone.ip, phone.port) end
    end
  end
end

local function onVehicleInfo(vehId, maxRpm, steeringLock, features)
  local vehicle = getObjectByID(vehId)
  local name = vehicle and vehicle:getJBeamFilename() or ''
  local safeName = tostring(name):gsub('[|\r\n]', '_')
  vehicleInfo[vehId] = string.format('RCPV|%d|%d|%s|%d', maxRpm or 0, steeringLock or 0, safeName, features or 0)
end

local function onUpdate(dtReal)
  if not udpSocket then return end
  local now = Engine.Platform.getSystemTimeMS()

  for key, phone in pairs(phones) do
    if now - (phone.lastSeen or now) > TIMEOUT_MS then unplug(key) end
  end

  receive(now)

  for _, phone in pairs(phones) do
    local player = playerOf(phone)
    local vehicle = player and getPlayerVehicle(player) or nil
    local walking = isWalking(player) and vehicle ~= nil
    if walking ~= phone.walkingSent then
      -- The phone swaps to its walking screen on this, so it goes out at once.
      phone.walkingSent = walking
      udpSocket:sendto(walking and 'RCPW|1' or 'RCPW|0', phone.ip, phone.port)
    end
    if walking then
      -- No force feedback on foot: the character is not a car.
      if phone.wheelVeh then wheelOff(phone) end
      walk(phone, vehicle, now)
      -- On foot the game always looks through the character's eyes; what it can do
      -- is zoom (55° to 27°, like binoculars). That only works in its walking camera.
      if phone.viewToggle then
        if core_camera.getActiveCamName(player) ~= 'unicycle' then core_camera.setByName(player, 'unicycle') end
        core_camera.unicycleZoomToggle(player)
      end
    else
      stopWalking(phone)
      if phone.wheelFree or phone.wheelWanted then syncWheel(phone, vehicle) end
    end
    phone.viewToggle = nil
    -- Look: the phone's drag turns the camera the way a mouse does, in the car too.
    if player and ((phone.lookX or 0) ~= 0 or (phone.lookY or 0) ~= 0) then
      core_camera.rotate_yaw_relative(phone.lookX or 0, player)
      core_camera.rotate_pitch_relative(-(phone.lookY or 0), player)
    end
    phone.lookX, phone.lookY = 0, 0
  end

  infoTimer = infoTimer + (dtReal or 0)
  if infoTimer >= INFO_PERIOD then
    infoTimer = 0
    requestVehicleInfo()
  end
end

local function onVehicleDestroyed(vehId)
  vehicleInfo[vehId] = nil
end

local function onExtensionLoaded()
  if not ffi or not bit then
    log('E', logTag, 'RemoteControlPro needs LuaJIT ffi and bit — not loading')
    return false
  end
  if not openSocket() then return false end
  return true
end

local function onExtensionUnloaded()
  for key in pairs(phones) do unplug(key) end
  if udpSocket then
    udpSocket:close()
    udpSocket = nil
  end
end

M.onUpdate = onUpdate
M.onExtensionLoaded = onExtensionLoaded
M.onExtensionUnloaded = onExtensionUnloaded
M.onVehicleDestroyed = onVehicleDestroyed
M.onVehicleInfo = onVehicleInfo
M.onWheelAngle = onWheelAngle

return M
