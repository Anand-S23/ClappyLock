package com.example.clappylock

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.util.*
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ControlActivity: AppCompatActivity() {

    companion object {
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"

        var deviceUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    lateinit var bluetoothConnectionService: BluetoothConnectionService
    lateinit var bluetoothAdapter: BluetoothAdapter

    lateinit var address: String
    var ledOn = false
    var clockRunning = true
    var paused = true
    var time = 0
    var currentTime = 0

    lateinit var recorder: MediaRecorder
    var prevAudioAmplitude: Double = 0.0
    var prevMaxAudioTime: Int = 0


    private lateinit var controlLed: Button
    private lateinit var controlListening: Button
    private lateinit var deviceDisconnect: Button
    private lateinit var listenText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.control_layout)
        address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS).toString()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)

        initClock(1)
        bluetoothConnectionService = BluetoothConnectionService(this, handler)
        bluetoothConnectionService.connect(device)
        Log.d("ControlLog", "Connecting...")

        recorder = MediaRecorder()

        if (recorder == null) {
            val msg = "Microphone is disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            disconnect()
        }

        val filename = applicationContext.getExternalFilesDir(null)!!.absolutePath + "/file.acc"
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(128000)
            setOutputFile(filename)
            prepare()
        }

        GlobalScope.launch(Dispatchers.Default) {
            while (clockRunning) {
                if (time != currentTime) {
                    time = currentTime

                    val currentAmp = recorder.maxAmplitude

                    if (currentAmp > 20000) {
                        Log.d("ControlActivityLog", "$currentAmp, $prevAudioAmplitude")

                        var toggled = false

                        if (prevAudioAmplitude > 20000 && (time - prevMaxAudioTime) < 5) {
                            Log.d("ControlActivityLog", "$time, $prevMaxAudioTime")
                            toggleLED()
                            toggled = true
                        }

                        prevAudioAmplitude = currentAmp.toDouble()
                        prevMaxAudioTime = if (toggled) 0 else time
                    }
                }
            }
        }

        controlLed = findViewById(R.id.control_led)
        controlListening = findViewById(R.id.control_listening)
        deviceDisconnect = findViewById(R.id.device_disconnect)
        listenText = findViewById(R.id.listen_text)

        controlLed.setOnClickListener {
            toggleLED()
            if (ledOn) {
                controlLed.text = getString(R.string.turn_off)
            } else {
                controlLed.text = getString(R.string.turn_on)
            }
        }

        controlListening.setOnClickListener {
            if (!paused) {
                controlListening.text = "Start Listening"
                // TODO: Pause listening
                recorder.stop()
            } else {
                controlListening.text = "Stop Listening"
                // TODO: Start listening
                recorder.start()
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
        if (bluetoothConnectionService!!.state !== BluetoothConnectionService.STATE_CONNECTED) {
            Toast.makeText(this, "Device is not connected", Toast.LENGTH_SHORT).show()
            return
        }

        if (input.isNotEmpty()) {
            val send = input.toByteArray()
            bluetoothConnectionService.write(send)
        }
    }

    private fun toggleLED() {
        if (!ledOn) {
            sendCommand("b")
        } else {
            sendCommand("a")
        }

        ledOn = !ledOn
        prevMaxAudioTime = 0
    }

    private fun disconnect() {
        clockRunning = false
        recorder.reset()
        recorder.release()
        bluetoothConnectionService.stop()
        finish()
    }

    private val handler = object : Handler() {}
}
