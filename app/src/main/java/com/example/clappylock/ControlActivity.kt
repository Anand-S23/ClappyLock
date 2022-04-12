package com.example.clappylock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.*
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException

class ControlActivity: AppCompatActivity() {

    companion object {
        var deviceUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var bluetoothSocket: BluetoothSocket? = null
        lateinit var bluetoothAdapter: BluetoothAdapter
        var isConnected: Boolean = false
        lateinit var address: String
    }

    var time = 0
    var currentTime = 0
    var ledOn = false
    var clockRunning = true
    var paused = true

    private lateinit var controlLed: Button
    private lateinit var controlListening: Button
    private lateinit var deviceDisconnect: Button
    private lateinit var listenText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.control_layout)
        address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS).toString()

        ConnectToDevice(this).execute()
        initClock(1)

        GlobalScope.launch(Dispatchers.Default) {
            while (clockRunning) {
                if (time != currentTime) {
                    time = currentTime
                    Log.d("ControlActivityLog", "$time")
                    // TODO: Update audio
                }
            }
        }

        controlLed = findViewById(R.id.control_led)
        controlListening = findViewById(R.id.control_listening)
        deviceDisconnect = findViewById(R.id.device_disconnect)
        listenText = findViewById(R.id.listen_text)

        controlLed.setOnClickListener {
            if (!ledOn) {
                sendCommand("a")
                controlLed.text = getString(R.string.turn_off)
            } else {
                sendCommand("b")
                controlLed.text = getString(R.string.turn_on)
            }

            ledOn = !ledOn
        }

        controlListening.setOnClickListener {
            if (!paused) {
                controlListening.text = "Start Listening"
                // TODO: Pause listening
            } else {
                controlListening.text = "Stop Listening"
                // TODO: Start listening
            }

            toggleClock()
        }

        deviceDisconnect.setOnClickListener { disconnect() }
    }

    private fun initClock(tickRate: Int) {
        Thread {
            while (clockRunning) {
                if (!paused) {
                    currentTime += tickRate
                    Thread.sleep(1000)
                }
            }
        }.start()
    }

    private fun toggleClock() {
        paused = !paused
    }

    private fun sendCommand(input: String) {
        if (bluetoothSocket != null) {
            try{
                bluetoothSocket!!.outputStream.write(input.toByteArray())
            } catch(e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun disconnect() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket!!.close()
                bluetoothSocket = null
                isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        finish()
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context = c

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                if (bluetoothSocket == null || !isConnected) {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { }
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(deviceUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
            } else {
                isConnected = true
            }
        }
    }
}