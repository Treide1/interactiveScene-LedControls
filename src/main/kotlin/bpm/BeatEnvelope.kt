package bpm

import utils.modulusToRange

class BeatEnvelope(val beatCount: Double, val envelope: (x: Double) -> Double) {

    fun sample(phase: Double, delay: Double = 0.0) : Double{
        val x = phase.minus(delay).modulusToRange(beatCount, 0.0, beatCount)
        return envelope(x)
    }
}