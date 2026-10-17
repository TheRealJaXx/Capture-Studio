package com.example.capture

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.serenegiant.usb.USBMonitor

/**
 * Manages USB device discovery, connection state, and permissions using com.serenegiant.usb.USBMonitor.
 */
class UsbMonitorManager(
    private val context: Context,
    private val onDeviceAttached: (UsbDevice) -> Unit,
    private val onDeviceDetached: (UsbDevice) -> Unit,
    private val onDeviceOpened: (UsbDevice, USBMonitor.UsbControlBlock) -> Unit,
    private val onDeviceClosed: (UsbDevice) -> Unit,
    private val onPermissionDenied: (UsbDevice) -> Unit
) {
    companion object {
        private const val TAG = "UsbMonitorManager"
        const val USB_CLASS_VIDEO = 14
    }

    private var usbMonitor: USBMonitor? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val deviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            device?.let {
                Log.d(TAG, "USB onAttach: ${it.deviceName} (Vendor: ${it.vendorId}, Product: ${it.productId})")
                onDeviceAttached(it)
            }
        }

        override fun onDetach(device: UsbDevice?) {
            device?.let {
                Log.d(TAG, "USB onDetach: ${it.deviceName}")
                onDeviceDetached(it)
            }
        }

        override fun onDeviceOpen(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            if (device != null && ctrlBlock != null) {
                Log.d(TAG, "USB onDeviceOpen: ${device.deviceName}")
                onDeviceOpened(device, ctrlBlock)
            }
        }

        override fun onDeviceClose(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            device?.let {
                Log.d(TAG, "USB onDeviceClose: ${it.deviceName}")
                onDeviceClosed(it)
            }
        }

        override fun onCancel(device: UsbDevice?) {
            device?.let {
                Log.d(TAG, "USB onCancel (Permission denied): ${it.deviceName}")
                onPermissionDenied(it)
            }
        }

        override fun onError(device: UsbDevice?, e: USBMonitor.USBException?) {
            Log.e(TAG, "USB onError for device: ${device?.deviceName}, error: ${e?.message}")
        }
    }

    fun start() {
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(context, deviceConnectListener)
        }
        usbMonitor?.let {
            if (!it.isRegistered) {
                it.register()
                Log.d(TAG, "USBMonitor registered")
            }
        }
    }

    fun stop() {
        usbMonitor?.let {
            if (it.isRegistered) {
                it.unregister()
                Log.d(TAG, "USBMonitor unregistered")
            }
        }
    }

    fun destroy() {
        stop()
        usbMonitor?.destroy()
        usbMonitor = null
    }

    fun isUvcDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == USB_CLASS_VIDEO) {
            return true
        }
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_VIDEO) {
                return true
            }
        }
        return false
    }

    fun getConnectedUvcDevices(): List<UsbDevice> {
        val deviceList = mutableListOf<UsbDevice>()
        val deviceMap = usbManager.deviceList
        for (device in deviceMap.values) {
            if (isUvcDevice(device)) {
                deviceList.add(device)
            }
        }
        // If no device strictly matches Video class, check all devices as some dongles report MISC
        if (deviceList.isEmpty()) {
            deviceList.addAll(deviceMap.values)
        }
        return deviceList
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbMonitor?.hasPermission(device) ?: usbManager.hasPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        usbMonitor?.requestPermission(device)
    }

    fun getDeviceName(device: UsbDevice): String {
        val productName = device.productName?.trim()
        if (!productName.isNullOrEmpty()) {
            return productName
        }
        val manufacturer = device.manufacturerName?.trim()
        if (!manufacturer.isNullOrEmpty()) {
            return "$manufacturer Video Capture"
        }
        return "USB Video Capture (${device.deviceName})"
    }
}
