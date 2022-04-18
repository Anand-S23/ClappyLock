package com.example.clappylock

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
    }

    private lateinit var bluetoothConnectionService: BluetoothConnectionService
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var address: String
    private var ledOn = false
    private var clockRunning = true
    private var paused = true
    private var time = 0
    private var currentTime = 0

    private lateinit var recorder: MediaRecorder
    private var prevAudioAmplitude: Double = 0.0
    private var prevMaxAudioTime: Int = 0


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

                    /*
                       NOTE: Sensing Pipe Line

                       Data Extraction: Utilized the android api for the recorder to get max audio
                       amplitude from microphone, every frame (each fame is 1 sec long)

                       Preprocessing: The max audio amplitude is first normalized and all values
                       that are under audio threshold will be removed by being set to zero

                       Feature Extraction: If the normalized audio amplitude is over 0, it mean that
                       it is a point of interest. When ever this is the case the time it took place
                       along with the value is stored, which will be later used for classification.

                       Classification: The way this is implemented here is that there needs to be
                       two separate audio peaks above the threshold within 3 seconds, then we
                       can classify that as a unlock. This method does have it's limitation as if
                       the client were to clap very fast and both claps are complete within the same
                       frame, it will be classified as just one clap and thus will not toggle the led
                    */

                    val currentAmp: Double = normalizeAmplitude(recorder.maxAmplitude)
                    Log.d("ControlActivityLog", "$currentAmp, $prevAudioAmplitude")

                    if (currentAmp > 0) {
                        Log.d("ControlActivityLog", "$currentAmp, $prevAudioAmplitude")

                        var toggled = false

                        if (prevAudioAmplitude > 0 && (time - prevMaxAudioTime) <= 3) {
                            Log.d("ControlActivityLog", "$time, $prevMaxAudioTime")
                            toggleLED()
                            toggled = true
                        }

                        prevAudioAmplitude = currentAmp
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
                recorder.stop()
            } else {
                controlListening.text = "Stop Listening"
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
    }

    private fun normalizeAmplitude(amp: Int): Double {
        val normalized: Double = (amp / 20_000.0) - 1
        return if (normalized < 0) 0.0 else normalized
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
