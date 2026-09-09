package com.example.micswitcher

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Wraps AudioManager's system-wide "communication device" APIs (Android 12+).
 *
 * Setting a communication device changes which input is used for phone calls
 * and VoIP apps that go through the standard telecom/communication audio
 * path, system-wide, until cleared or the device disconnects. This is the
 * closest thing to a global mic switch a non-system app can perform without
 * root — it does NOT force arbitrary apps (e.g. a camera app doing its own
 * raw AudioRecord capture) onto a specific device, since Android doesn't
 * expose that level of control to third-party apps.
 */
object CommunicationDeviceManager {

    private const val PREFS = "mic_switcher_prefs"
    private const val KEY_DEVICE_TYPE = "preferred_device_type"

    fun availableDevices(audioManager: AudioManager): List<AudioDeviceInfo> =
        audioManager.availableCommunicationDevices

    fun currentDevice(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.communicationDevice

    /**
     * setCommunicationDevice() doesn't work off the same device list as
     * normal mic-input enumeration — it works off call-routing endpoints
     * (earpiece, speaker, wired headset, Bluetooth SCO, USB headset).
     * Wired/USB/Bluetooth mics share the same AudioDeviceInfo.type constant
     * on both lists, so they match directly. The built-in mic doesn't: its
     * routing endpoint is the earpiece (or speaker on devices with no
     * earpiece, e.g. tablets), so it needs an explicit mapping.
     */
    fun resolveCommunicationDevice(audioManager: AudioManager, inputDevice: AudioDeviceInfo): AudioDeviceInfo? {
        val available = availableDevices(audioManager)

        available.firstOrNull { it.type == inputDevice.type }?.let { return it }

        if (inputDevice.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }?.let { return it }
            available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { return it }
        }

        if (inputDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE) {
            available.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }?.let { return it }
        }

        return null
    }

    /** Returns true if the system accepted the switch. */
    fun setDevice(context: Context, audioManager: AudioManager, inputDevice: AudioDeviceInfo): Boolean {
        val target = resolveCommunicationDevice(audioManager, inputDevice) ?: return false
        val ok = audioManager.setCommunicationDevice(target)
        if (ok) {
            prefs(context).edit().putInt(KEY_DEVICE_TYPE, inputDevice.type).apply()
        }
        return ok
    }

    fun clearDevice(context: Context, audioManager: AudioManager) {
        audioManager.clearCommunicationDevice()
        prefs(context).edit().remove(KEY_DEVICE_TYPE).apply()
    }

    /** Cycles to the next available device after whatever's currently active. */
    fun cycleDevice(context: Context, audioManager: AudioManager): AudioDeviceInfo? {
        val devices = availableDevices(audioManager)
        if (devices.isEmpty()) return null
        val current = currentDevice(audioManager)
        val currentIndex = devices.indexOfFirst { it.id == current?.id }
        val next = devices[(currentIndex + 1) % devices.size]
        return if (setDevice(context, audioManager, next)) next else null
    }

    fun lastPreferredType(context: Context): Int =
        prefs(context).getInt(KEY_DEVICE_TYPE, -1)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
