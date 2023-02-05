@file:Suppress("unused")

package bpm

import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.Drawer

class BpmRepo(val bpm: Double, program: Program) : Extension {

    val bps = bpm/60.0
    val secPerBeat = 1.0/bps

    // TODO: add targetPhase and provide value blending over time
    var phase = 0.0

    override var enabled = true
    override fun beforeDraw(drawer: Drawer, program: Program) {
        phase += program.deltaTime * bps
        onBeatFunctionList.forEach { it.update(phase) }
    }

    init {
        program.extend(this)
    }

    val envelopeList = mutableListOf<BeatEnvelope>()
    val onBeatFunctionList = mutableListOf<OnBeatFunction>()

    fun addEnvelopeFromSegments(beatCount: Double, fromSegments: BeatEnvelopeBuilder.() -> Unit): BeatEnvelope {
        val envBuilder = BeatEnvelopeBuilder(beatCount)
        envBuilder.fromSegments()
        val env = envBuilder.build()
        envelopeList.add(env)
        return env
    }

    // TODO: make offset work
    fun addOnBeatFunction(interval: Double, offset: Double = 0.0, function: () -> Unit): OnBeatFunction {
        val obf = OnBeatFunction(interval, offset, function)
        onBeatFunctionList.add(obf)
        return obf
    }

    fun reset() {
        phase = 0.0
        onBeatFunctionList.forEach { it.reset() }
    }

}