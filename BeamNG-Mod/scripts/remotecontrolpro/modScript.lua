-- RemoteControlPro: load the extension when the mod is activated and keep it
-- loaded across level changes.
setExtensionUnloadMode('remotecontrolpro_main', 'manual')
extensions.load('remotecontrolpro_main')
