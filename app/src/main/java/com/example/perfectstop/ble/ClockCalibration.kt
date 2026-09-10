package com.example.perfectstop.ble

class ClockCalibration {
    var offsetMs = 0L
        private set
    private var bestDelay = Long.MAX_VALUE
    fun add(sent: Long, hostReceived: Long, hostSent: Long, received: Long) {
        val delay = (received - sent) - (hostSent - hostReceived)
        if (delay < 0 || delay >= bestDelay) return
        bestDelay = delay
        offsetMs = ((hostReceived - sent) + (hostSent - received)) / 2
    }
}
