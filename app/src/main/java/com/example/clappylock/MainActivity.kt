package com.example.clappylock

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val ENABLE_BLUETOOTH_REQUEST = 1
    private val ENABLE_DISCOVER_REQUEST = 2

    companion object {
        val EXTRA_ADDRESS: String = "extraAddress"
    }

    private lateinit var selectDeviceList: ListView
    private lateinit var selectDeviceRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            val msg = "This device does not support bluetooth"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val permissionGranted = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                val msg = "Bluetooth permissions required"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }

            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST)
        }

        if (!bluetoothAdapter.isDiscovering) {
            val msg = "Making device discoverable"
            Log.d("MainActivity", msg)
            // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            val enableDiscoverIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            startActivityForResult(enableDiscoverIntent, ENABLE_DISCOVER_REQUEST)
        }

        selectDeviceList = findViewById(R.id.select_device_list)
        selectDeviceRefresh = findViewById(R.id.select_device_refresh)

        selectDeviceRefresh.setOnClickListener {
            getDevicesPaired()
        }
    }

    private fun getDevicesPaired() {
        if (bluetoothAdapter.isEnabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                val msg = "Make device discoverable first"
                Log.d("MainActivity", msg)
            }

            val devices = bluetoothAdapter.bondedDevices
            val list: ArrayList<BluetoothDevice> = ArrayList()
            if (devices.isNotEmpty()) {
                for (device: BluetoothDevice in devices) {
                    list.add(device)
                }
            } else {
                val msg = "No paired device found"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
            selectDeviceList.adapter = adapter
            selectDeviceList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val device: BluetoothDevice = list[position]
                val address: String = device.address

                val intent = Intent(this, ControlActivity::class.java)
                intent.putExtra(EXTRA_ADDRESS, address)
                startActivity(intent)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    val msg = "Connected to bluetooth"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "Could not connect to bluetooth"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }

            ENABLE_DISCOVER_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    val msg = "Device is discoverable"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "Could not make device discoverable"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}