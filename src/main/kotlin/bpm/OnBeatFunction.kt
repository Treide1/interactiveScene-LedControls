package bpm

import utils.modulusToRange

class OnBeatFunction(val interval: Double, val offset: Double, val function: () -> Unit) {

    var isSkippingFunction = false

    var nextInvokeOn: Double = 0.0
    init {
        reset()
    }

    fun update(phase: Double) {
        if (phase > nextInvokeOn) {
            if (!isSkippingFunction) function()
            nextInvokeOn += interval
        }
    }

    fun reset() {
        nextInvokeOn = offset.modulusToRange(interval,0.0, interval)
    }

}