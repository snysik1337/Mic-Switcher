# Mic Switcher

A small Android (Kotlin) app that lists the microphone inputs currently
available on the device — built-in mic, wired headset, USB mic, connected
Bluetooth mic — and lets you pick which one to record from. Recording is
written as a WAV file so you can verify the switch actually changed the
capture source.

## How it works

- `AudioManager.getDevices(GET_DEVICES_INPUTS)` enumerates the available
  input devices.
- Tapping a device in the list calls `AudioRecord.setPreferredDevice()`,
  which routes capture to that device (supported since API 23).
- Selecting a Bluetooth mic additionally opens the SCO link
  (`startBluetoothSco()` / `isBluetoothScoOn = true`), since a Bluetooth
  headset mic won't carry audio until SCO is active.
- Recording is done with raw `AudioRecord` (not `MediaRecorder`), since
  `AudioRecord.setPreferredDevice()` is the most reliably supported way to
  target a specific device pre-API 28. The PCM stream is wrapped into a
  standard 16-bit mono WAV file when you stop recording.
- Files are saved to the app's external files dir under `Music/`, e.g.
  `Android/data/com.example.micswitcher/files/Music/recording_<timestamp>.wav`.

## Opening the project

1. Open Android Studio (Koala/2024.1 or newer recommended).
2. **File > Open** and select the `MicSwitcher` folder.
3. Let Android Studio sync Gradle. This project doesn't ship the Gradle
   wrapper jar (binary file), so on first open Android Studio will offer to
   regenerate it automatically — accept that prompt (or run
   `gradle wrapper --gradle-version 8.7` yourself if you have Gradle
   installed locally).
4. Run on a device or emulator. A physical device is strongly recommended
   for testing Bluetooth/USB mic switching, since emulators only expose a
   virtual built-in mic.

## Dark mode

The app is built on `Theme.MaterialComponents.DayNight`, so it already
follows the system's light/dark setting automatically — no extra code
needed for that. On top of that, the "Theme: ..." button in the top-right
lets you force Light, Dark, or System regardless of the OS setting; your
choice is saved and re-applied on next launch (via `AppCompatDelegate`).
Tapping it recreates the activity to apply the change immediately, so if
you're mid-recording or mid-test it'll stop — that's expected.

## Mic tester

The "Test mic" button opens a live level meter (`MicLevelTester`) on
whichever device is currently selected, without writing anything to disk —
useful for confirming a device is actually picking up sound before you
commit to recording or before jumping into a call. It shows a 0-100 bar
plus an approximate dBFS reading, computed from the RMS of each audio
buffer. If you switch devices while the test is running, it restarts on
the newly selected device automatically. Recording and testing are mutually
exclusive (only one `AudioRecord` session at a time) — stop one before
starting the other.

## Global switch (system communication device)

Selecting a device in the app also calls `AudioManager.setCommunicationDevice()`,
which sets the system-wide **communication device** — the input used for phone
calls and any VoIP app that goes through the standard telecom audio path
(`MODE_IN_COMMUNICATION`). This applies until you clear it (via the "Clear
system call mic override" button, or Android resets it itself when the
device disconnects).

**Important limit:** this does not let a third-party app force *all* apps
(camera apps doing raw recording, apps with custom audio pipelines, etc.)
onto a specific mic — Android doesn't expose that level of control without
root. `setCommunicationDevice()` is the broadest lever a normal app gets.

### Quick Settings Tile

Long-press your Quick Settings panel, tap "Edit", and drag the "Mic input"
tile in. Tapping it cycles through the currently available communication
devices (built-in → wired → USB → Bluetooth, depending on what's plugged
in/connected) and shows the active one as the tile subtitle — no need to
open the app.

## Permissions

- `RECORD_AUDIO` — required, requested at runtime.
- `BLUETOOTH_CONNECT` — required, requested at runtime (minSdk is now 31, so
  this is always needed), for Bluetooth device names and the SCO link.
- `MODIFY_AUDIO_SETTINGS` — declared in the manifest, no runtime prompt
  needed; required for `setCommunicationDevice()`.
- `BIND_QUICK_SETTINGS_TILE` — system-held permission on the tile service
  itself; nothing you need to request.

Note: `minSdk` is now **31** (Android 12), since `setCommunicationDevice()`
and `availableCommunicationDevices` require it.

## Known limitations / things to extend

- Only mono 44.1kHz 16-bit capture is implemented; adjust `WavRecorder` if
  you need stereo or a different sample rate.
- Bluetooth SCO connection is asynchronous in real life — this app starts
  it optimistically. For production use, listen for
  `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` and wait for
  `SCO_AUDIO_STATE_CONNECTED` before calling `startRecording()`.
- No playback UI is included; pull the WAV file off the device (e.g. via
  `adb pull` or a file manager) to check it.
