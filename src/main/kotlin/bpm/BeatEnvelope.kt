package bpm

import modulusToRange

class BeatEnvelope(val beatCount: Double, val envelope: (x: Double) -> Double) {

    fun sample(phase: Double, phaseOff: Double = 0.0) : Double{
        val x = phase.plus(phaseOff).modulusToRange(beatCount, 0.0, beatCount)
        return envelope(x)
    }
}