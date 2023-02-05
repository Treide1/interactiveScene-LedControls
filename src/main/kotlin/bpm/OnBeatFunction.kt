package bpm

import modulusToRange

class OnBeatFunction(val interval: Double, val offset: Double, val function: () -> Unit) {

    var nextInvokeOn: Double = 0.0
    init {
        reset()
    }

    fun update(phase: Double) {
        if (phase > nextInvokeOn) {
            function()
            nextInvokeOn += interval
        }
    }

    fun reset() {
        nextInvokeOn = offset.modulusToRange(interval,0.0, interval)
    }

}