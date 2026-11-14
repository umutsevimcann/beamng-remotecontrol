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
--   * buttons: shift up/down, horn, lights, fog lights, signals, hazards, recover
--   * per-vehicle data sent back: rev limit and steering-wheel lock
--
-- Wire protocol (version 1), UDP port 4450; replies go to the sender's address:
--   phone -> mod   "RCP?|1|<deviceName>"                  hello (broadcast ok), ~every 3 s
--   mod   -> phone "RCP!|1|<pcName>|<modVersion>"         answer to every hello
--   phone -> mod   28 bytes, little-endian                control, ~100 Hz
--                    char[3] "RCP", uint8 version (1)
--                    float steering   -1 (left) .. 1 (right)
--                    float throttle, brake, clutch, handbrake   0 .. 1
--                    uint32 buttons   (bit n = button n held)
--   mod   -> phone "RCPV|<maxRpm>|<steeringLockDeg>|<vehicleName>"   ~2 Hz while driving
-- A phone that stays silent for 10 s is unplugged.

local M = {}

local logTag = 'remotecontrolpro'
local MOD_VERSION = '2.0.0'
local PROTOCOL = 1
local PORT = 4450
local DEVICE_TYPE = 'bngremotectrlprov2' -- must match the inputmap file name
local N_AXES = 5      -- steering, throttle, brake, clutch, handbrake
local N_BUTTONS = 10  -- see BUTTON order in the Android app (ModProtocol.kt)
local TIMEOUT_MS = 10000
local INFO_PERIOD = 0.5

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
  #pragma pack(pop)
  ]])
end

local udpSocket = nil
-- phones by ip: { ip, port, name, deviceInst, lastSeen, axes = {}, buttons = 0 }
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

local function unplug(key)
  local phone = phones[key]
  if not phone then return end
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
  emitAxis(phone, 0, (min(1, max(-1, steering)) + 1) * 0.5)
  emitAxis(phone, 1, clamp01(pkt.throttle))
  emitAxis(phone, 2, clamp01(pkt.brake))
  emitAxis(phone, 3, clamp01(pkt.clutch))
  emitAxis(phone, 4, clamp01(pkt.handbrake))

  local buttons = tonumber(pkt.buttons)
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

-- Asks each driven vehicle for its rev limit and steering lock. The vehicle Lua
-- answers through onVehicleInfo; nothing here blocks on it.
-- The player a phone drives for, straight from the game's binding table. (The
-- onInputBindingsChanged hook can fire while createDevice is still running,
-- before the phone is in our table, so it is not a reliable source.)
local function playerOf(phone)
  local players = core_input_bindings and core_input_bindings.assignedPlayers
  local player = players and players['vinput' .. phone.deviceInst]
  return (player and player >= 0) and player or nil
end

local function requestVehicleInfo()
  for _, phone in pairs(phones) do
    local player = playerOf(phone)
    local vehicle = player and getPlayerVehicle(player) or nil
    if vehicle then
      vehicle:queueLuaCommand(
        'obj:queueGameEngineLua(string.format("if remotecontrolpro_main then remotecontrolpro_main.onVehicleInfo(%d, %f, %f) end", ' ..
        'obj:getId(), (electrics.values.maxrpm or 0), ((v.data.input and v.data.input.steeringWheelLock) or 0)))')
      local info = vehicleInfo[vehicle:getId()]
      if info then udpSocket:sendto(info, phone.ip, phone.port) end
    end
  end
end

local function onVehicleInfo(vehId, maxRpm, steeringLock)
  local vehicle = getObjectByID(vehId)
  local name = vehicle and vehicle:getJBeamFilename() or ''
  vehicleInfo[vehId] = string.format('RCPV|%d|%d|%s', maxRpm or 0, steeringLock or 0, tostring(name):gsub('[|\r\n]', '_'))
end

local function onUpdate(dtReal)
  if not udpSocket then return end
  local now = Engine.Platform.getSystemTimeMS()

  for key, phone in pairs(phones) do
    if now - (phone.lastSeen or now) > TIMEOUT_MS then unplug(key) end
  end

  receive(now)

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

return M
