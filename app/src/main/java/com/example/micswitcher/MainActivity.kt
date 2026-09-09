package com.example.micswitcher

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var refreshButton: Button
    private lateinit var clearOverrideButton: Button
    private lateinit var testMicButton: Button
    private lateinit var themeToggleButton: Button
    private lateinit var levelMeter: ProgressBar
    private lateinit var levelText: TextView

    private var selectedDevice: AudioDeviceInfo? = null
    private var recorder: WavRecorder? = null
    private var isRecording = false
    private var micTester: MicLevelTester? = null
    private var isTesting = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            refreshDevices()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved theme choice before inflating any views.
        ThemePrefs.applySavedMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        recyclerView = findViewById(R.id.deviceList)
        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)
        refreshButton = findViewById(R.id.refreshButton)
        clearOverrideButton = findViewById(R.id.clearOverrideButton)
        testMicButton = findViewById(R.id.testMicButton)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        levelMeter = findViewById(R.id.levelMeter)
        levelText = findViewById(R.id.levelText)

        themeToggleButton.text = ThemePrefs.label(ThemePrefs.currentMode(this))

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter { device -> onDeviceSelected(device) }
        recyclerView.adapter = adapter

        refreshButton.setOnClickListener { refreshDevices() }
        recordButton.setOnClickListener { toggleRecording() }
        testMicButton.setOnClickListener { toggleMicTest() }
        clearOverrideButton.setOnClickListener {
            CommunicationDeviceManager.clearDevice(this, audioManager)
            statusText.text = "System call mic override cleared"
        }
        themeToggleButton.setOnClickListener {
            // setDefaultNightMode triggers AppCompat to recreate this activity
            // automatically, so the new theme applies immediately.
            val mode = ThemePrefs.cycleMode(this)
            themeToggleButton.text = ThemePrefs.label(mode)
        }

        ensurePermissionsThenRefresh()
    }

    private fun ensurePermissionsThenRefresh() {
        val needed = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            refreshDevices()
        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in SUPPORTED_TYPES }
            .distinctBy { it.id }

        adapter.submitList(devices)

        if (devices.isEmpty()) {
            statusText.text = "No input devices found"
        } else if (selectedDevice == null || devices.none { it.id == selectedDevice?.id }) {
            onDeviceSelected(devices.first())
        }
    }

    private fun onDeviceSelected(device: AudioDeviceInfo) {
        selectedDevice = device
        adapter.setSelected(device.id)
        statusText.text = "Selected: ${deviceLabel(device)}"

        // A Bluetooth mic needs its SCO link explicitly opened to carry audio.
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }

        recorder?.setPreferredDevice(device)

        // This is the "global" part: make it the system's communication
        // device too, so phone calls / VoIP apps also route through it,
        // not just this app's own recording.
        val appliedGlobally = CommunicationDeviceManager.setDevice(this, audioManager, device)
        if (!appliedGlobally) {
            Toast.makeText(this, "No matching system call route found for this device", Toast.LENGTH_SHORT).show()
        }

        // If the level meter is running, follow the newly selected device.
        if (isTesting) {
            micTester?.stop()
            micTester = startTester(device)
        }
    }

    private fun toggleMicTest() {
        val device = selectedDevice
        if (device == null) {
            Toast.makeText(this, "Pick an input device first", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRecording) {
            Toast.makeText(this, "Stop recording before testing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTesting) {
            val tester = startTester(device)
            if (tester != null) {
                micTester = tester
                isTesting = true
                testMicButton.text = getString(R.string.stop_test)
                recordButton.isEnabled = false
            } else {
                Toast.makeText(this, "Could not start mic test", Toast.LENGTH_SHORT).show()
            }
        } else {
            micTester?.stop()
            micTester = null
            isTesting = false
            testMicButton.text = getString(R.string.test_mic)
            levelMeter.progress = 0
            levelText.text = ""
            recordButton.isEnabled = true
        }
    }

    private fun startTester(device: AudioDeviceInfo): MicLevelTester? {
        val tester = MicLevelTester { level, dbfs ->
            levelMeter.progress = level
            levelText.text = String.format(Locale.getDefault(), "%.0f dBFS", dbfs)
        }
        return if (tester.start(device)) tester else null
    }

    private fun toggleRecording() {
        val device = selectedDevice
        if (device == null) {
            Toast.makeText(this, "Pick an input device first", Toast.LENGTH_SHORT).show()
            return
        }
        if (isTesting) {
            Toast.makeText(this, "Stop the mic test before recording", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isRecording) {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val outFile = File(dir, "recording_${System.currentTimeMillis()}.wav")
            val newRecorder = WavRecorder(outFile)
            val started = newRecorder.start(device)
            if (started) {
                recorder = newRecorder
                isRecording = true
                recordButton.text = "Stop recording"
                testMicButton.isEnabled = false
                statusText.text = "Recording via ${deviceLabel(device)}"
            } else {
                Toast.makeText(this, "Could not start recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            recorder?.stop()
            isRecording = false
            recordButton.text = getString(R.string.start_recording)
            testMicButton.isEnabled = true
            statusText.text = "Saved recording (input: ${deviceLabel(device)})"
        }
    }

    private fun deviceLabel(device: AudioDeviceInfo): String {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return name ?: typeLabel(device.type)
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.stop()
        micTester?.stop()
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    companion object {
        val SUPPORTED_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )

        fun typeLabel(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset mic"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset mic"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB microphone"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mic"
            else -> "Unknown input"
        }
    }
}
