package com.example.clappylock

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder

class ClockService : Service() {

    var paused = true
    var shouldExit = false
    var currentTime = 0
    lateinit var handler: Handler

    inner class ClockBinder : Binder() {
        fun initClock(tickRate: Int) {
            runClock(tickRate)
        }

        fun toggleClockPause(): Boolean {
            paused = !paused
            return paused
        }

        fun setHandler(clockHandler: Handler) {
            handler = clockHandler
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return ClockBinder()
    }

    fun runClock(tickRate: Int) {
        Thread {
            while (!shouldExit) {
                if (!paused) {
                    if (::handler.isInitialized)
                        handler.sendEmptyMessage(currentTime)
                    currentTime += tickRate
                    Thread.sleep(1000)
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldExit = true
        paused = false
    }
}