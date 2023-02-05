@file:Suppress("unused")

package bpm

import org.openrndr.math.map
import kotlin.math.PI
import kotlin.math.sin

/**
 * Keeping track of time in terms of beats being played.
 * Can be sampled continuously to get the current position in beat-time.
 * Add custom envelope to the bar. It gets repeated counting beats per bar. With BPM.
 * @param bpm Beats per minute. Default is 120.0.
 * @param beatsPerLoop Amount of beats that belong in one bar. Default is 4 for the common 4/4 time signature.
 * @param envelope Lambda for a [0, repeatAfter] -> [0, 1] envelope function.
 */
class BeatEnvelope(
    val bpm: Double = 120.0,
    val beatsPerLoop: Int = 4,
    val envelope: (Double) -> Double = { 0.0 }
) : ClockSubscriber {

    // Throw an exception if beatsPerLoop is not strictly positive.
    init {
        if (beatsPerLoop < 1) throw IllegalArgumentException("What ? Have you ever seen a bar with $beatsPerLoop beats ?\n" +
                "Make it at least 1.")
    }

    /**
     * Time within the bar. Adjusted before frame is drawn, taken modulo "beat count" from [beatsPerLoop].first.
     */
    var phase = 0.0

    /**
     * Implements [ClockSubscriber.tick], keeps track of phase in the sense of "beat time".
     * @param seconds (unused)
     * @param deltaTime real-world time difference between last drawn frame and this frame
     * @param frameCount (unused)
     */
    override fun tick(seconds: Double, deltaTime: Double, frameCount: Int) {
        phase += deltaTime *  (bpm / 60.0)
        phase %= beatsPerLoop
    }

    /**
     * Sample this BeatEnvelope at its current value.
     *
     * By default, uses current value. But also allows sampling from a past or future value.
     * Sample a value from the future by using positive phaseOff value.
     * Sample a value from the past with negative value.
     * @param phaseOff shift of sampling time in beats
     */
    fun sample(phaseOff: Double = 0.0) : Double {
        val samplingPhase = (phase + phaseOff + beatsPerLoop) % beatsPerLoop // phase at which we sample
        return envelope(samplingPhase)
    }

    /**
     * Sample this BeatEnvelope for many, equidistant phase values.
     *
     * @param phaseStart Phase of first sample. This bound is included in equidistant range.
     * @param phaseEnd Phase of last sample. This bound is included in equidistant range.
     * @param size Amount of samples. Also, size of list.
     */
    fun sampleList(phaseStart: Double = 0.0, phaseEnd: Double = 1.0, size: Int): List<Double> {
        return (0 until size).map { index ->
            val phase = index.toDouble().map(0.0, size-1.0, phaseStart, phaseEnd)
            sample(phase)
        }
    }

    /**
     * Use equidistant phases to sample this BeatEnvelope.
     * Use the index of the phase spread, the respective phase and the respective sample.
     * Apply those to your custom block.
     *
     * @param phaseStart Phase of first sample. This bound is included in equidistant range.
     * @param phaseEnd Phase of last sample. This bound is included in equidistant range.
     * @param size Amount of samples. Also, size of list.
     * @param block
     */
    fun forEachSamplePhaseIndex(phaseStart: Double, phaseEnd: Double, size: Int, block: BeatEnvelope.(Double, Double, Int) -> Unit) {
        val phaseList = interpolationList(phaseStart, phaseEnd, size)
        phaseList.forEachIndexed { index, phase ->
            this.block(sample(phase), phase, index)
        }
    }

    /**
     * Returns a copy of this BeatEnvelope with specified values.
     * You can copy the previous phase easily by using [withOriginalPhase]=true. Otherwise, resets phase by default.
     */
    fun copy(
        bpm: Double = this.bpm,
        beatsPerLoop: Int = this.beatsPerLoop,
        envelope: (Double) -> Double = this.envelope,
        withOriginalPhase: Boolean = false
    ): BeatEnvelope {
        val beatEnvelope = BeatEnvelope(bpm, beatsPerLoop, envelope)
        if (withOriginalPhase) beatEnvelope.phase = this.phase
        return beatEnvelope
    }

}

fun constructSine(bpm: Double, beatsPerLoop: Int, period: Double, amp: Double = 1.0): BeatEnvelope {
    return BeatEnvelope(bpm, beatsPerLoop) { phase ->
        // Take phase module period. Map domain to [0, TWO_PI]. Put into sine func.
        // Map domain to [0,1]. Scale by amp with center 0.5.
        sin((phase%period) * 2.0* PI /period) * .5 * amp + .5
    }
}


/**
 * Create List of equidistant entries within range [[phaseStart], [phaseEnd]], bounds inclusive.
 * @param phaseStart Lower range bound.
 * @param phaseEnd Upper range bound.
 * @param size Size of interpolation points between [phaseStart] and [phaseEnd], bounds included. Therefore, size of list.
 */
fun interpolationList(phaseStart: Double = 0.0, phaseEnd: Double = 1.0, size: Int): List<Double> {
    return List(size) { index -> index.toDouble().map(0.0, size-1.0, phaseStart, phaseEnd)}
}

/**
 * Get the result of element-wise list addition. [other] being null is ignored.
 */
infix fun List<Double>.add(other: List<Double>?): List<Double> {
    if (other == null) return this
    return this.zip(other) { a, b -> a + b }
}

/**
 * Get the result of element-wise list multiplication. [other] being null is ignored.
 */
infix fun List<Double>.mult(other: List<Double>?): List<Double> {
    if (other == null) return this
    return this.zip(other) { a, b -> a * b }
}

/**
 * Get the result of element-wise list addition. [other] being null is ignored.
 */
infix fun MutableList<Double>.addEqual(other: List<Double>?) {
    if (other == null) return
    if (this.size != other.size) return
    this.zip(other) { a, b -> a + b }.forEachIndexed { i, value ->
        this[i] = value
    }
}

/**
 * Get the result of element-wise list multiplication. [other] being null is ignored.
 */
infix fun MutableList<Double>.multEqual(other: List<Double>?) {
    if (other == null) return
    if (this.size != other.size) return
    this.zip(other) { a, b -> a + b }.forEachIndexed { i, value ->
        this[i] = value
    }
}

