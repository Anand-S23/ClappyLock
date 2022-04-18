package com.example.clappylock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothConnectionService(_context: Context, private val handler: Handler) {

    companion object {
        private const val NAME = "BluetoothConnectionService"
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
    }

    val context: Context = _context
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var currentState: Int = STATE_NONE

    var state: Int
        @Synchronized get() = currentState
        @Synchronized private set(state) {
            currentState = state
            handler.obtainMessage(ControlActivity.MESSAGE_STATE_CHANGE, state, -1)
                .sendToTarget()
        }

    @Synchronized
    fun start() {
        // Cancel threads trying to make a connection
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }
        // Cancel threads currently connected
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }
        state = STATE_LISTEN
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel threads trying to make a connection
        if (currentState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread!!.cancel()
                connectThread = null
            }
        }

        // Cancel threads currently connected
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to connect with the input device
        connectThread = ConnectThread(device)
        connectThread!!.start()

        state = STATE_CONNECTING
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Cancel the thread that completed connecting
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel threads currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Cancel the accept thread
        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()

        val msg = handler.obtainMessage(ControlActivity.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { }
        bundle.putString(ControlActivity.DEVICE_NAME, device.name)
        msg.data = bundle
        handler.sendMessage(msg)

        state = STATE_CONNECTED
    }

    @Synchronized
    fun stop() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        state = STATE_NONE
    }

    fun write(out: ByteArray) {
        val r: ConnectedThread?

        synchronized(this) {
            if (currentState != STATE_CONNECTED) return
            r = connectedThread
        }

        r!!.write(out)
    }

    private fun connectionFailed() {
        state = STATE_LISTEN

        val msg = handler.obtainMessage(ControlActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(ControlActivity.TOAST, "Unable to connect device")
        msg.data = bundle

        handler.sendMessage(msg)
    }

    private fun connectionLost() {
        state = STATE_LISTEN

        val msg = handler.obtainMessage(ControlActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(ControlActivity.TOAST, "Device connection was lost")
        msg.data = bundle

        handler.sendMessage(msg)
    }

    private inner class AcceptThread : Thread() {
        private val bluetoothServerSocket: BluetoothServerSocket?

        init {
            var temp: BluetoothServerSocket? = null

            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { }
                temp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
            } catch (e: IOException) {
                // TODO: Logging
            }

            bluetoothServerSocket = temp
        }

        override fun run() {
            name = "AcceptThread"
            var socket: BluetoothSocket?
            while (currentState != STATE_CONNECTED) {
                try {
                    socket = bluetoothServerSocket!!.accept()
                } catch (e: IOException) {
                    // TODO: Logging
                    break
                }

                if (socket != null) {
                    synchronized(this@BluetoothConnectionService) {
                        when (currentState) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                connected(socket, socket.remoteDevice)
                            STATE_NONE, STATE_CONNECTED -> {
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    // TODO: Logging
                                }
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                bluetoothServerSocket!!.close()
            } catch (e: IOException) {
                // TODO: Logging
            }
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val bluetoothSocket: BluetoothSocket?

        init {
            var temp: BluetoothSocket? = null

            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { }
                temp = device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                // TODO: Logging
            }

            bluetoothSocket = temp
        }

        override fun run() {
            name = "ConnectThread"
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) { }
            try {
                bluetoothSocket!!.connect()
            } catch (e: IOException) {
                connectionFailed()
                try {
                    bluetoothSocket!!.close()
                } catch (e2: IOException) {
                    // TODO: Logging
                }

                this@BluetoothConnectionService.start()
                return
            }

            synchronized(this@BluetoothConnectionService) {
                connectThread = null
            }

            connected(bluetoothSocket, device)
        }

        fun cancel() {
            try {
                bluetoothSocket!!.close()
            } catch (e: IOException) {
                // TODO: Logging
            }
        }
    }

    private inner class ConnectedThread(private val bluetoothSocket: BluetoothSocket) : Thread() {
        private val inStream: InputStream?
        private val outStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = bluetoothSocket.inputStream
                tmpOut = bluetoothSocket.outputStream
            } catch (e: IOException) {
            }

            inStream = tmpIn
            outStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = inStream!!.read(buffer)
                    handler.obtainMessage(ControlActivity.MESSAGE_READ, bytes, -1, buffer)
                        .sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                outStream!!.write(buffer)
                handler.obtainMessage(ControlActivity.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) {
                // TODO: Logging
            }
        }

        fun cancel() {
            try {
                bluetoothSocket.close()
            } catch (e: IOException) {
                // TODO: Logging
            }
        }
    }
}