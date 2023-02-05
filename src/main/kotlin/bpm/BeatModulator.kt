@file:Suppress("unused")

package bpm

import org.openrndr.animatable.easing.Easing

class BeatModulator(val envelopeCount : Int = 4, block: BeatModulator.() -> Unit = {}) : ClockSubscriber {


    init {
        if (envelopeCount < 0) throw IllegalArgumentException("envelopeCount must be non-negative, found $envelopeCount")
    }

    var isTicking = true

    /**
     * Array of nullable envelopes.
     * Access provides via [set] (as method or operator).
     * No direct read access to focus on range arithmetic.
     */
    val envelopes = arrayOfNulls<BeatEnvelope?>(envelopeCount)

    /**
     * Modulation weights for weighted sum of envelopes.
     * Can be subject of transitions, i.e. their value changes over time.
     */
    val weights = Array(envelopeCount) { Weight(0.0) }

    /**
     * Execute the initialization block.
     * Position of init block is important ! Namely, after vars.
     */
    init {
        this.block()
    }

    /**
     * Ticks from program are handed to envelopes and weights.
     */
    override fun tick(seconds: Double, deltaTime: Double, frameCount: Int) {
        if (!isTicking) return
        envelopes.forEach { env -> env?.tick(seconds, deltaTime, frameCount) }
        weights.forEach { weight -> weight.tick(deltaTime) }
    }

    /**
     * Synchronize all BeatEnvelopes added to this BeatModulator.
     * @param target The BeatEnvelope to sync to. If null, just syncs to 0.0. Doesn't have to be in this modulator.
     * @param phaseOff Offsets the phase to sync to. Adds on top of target phase.
     */
    fun syncAll(target: BeatEnvelope? = null, phaseOff: Double = 0.0) {
        val targetPhase = target?.phase ?: 0.0
        val offsetPhase = targetPhase + phaseOff
        envelopes.forEach { env -> env?.phase = offsetPhase}
    }

    @Deprecated("No benefit other keeping reference to beatEnvelope and sampling directly.")
    fun sampleList(envID: Int, phaseStart: Double, phaseEnd: Double, size: Int): List<Double> {
        if (envID < 0 || envID > envelopeCount) throw IllegalArgumentException()
        return envelopes[envID]?.sampleList(phaseStart, phaseEnd, size) ?: List(size) { 0.0 }
    }

    /**
     * Sample all envelopes and calculate their weighted sum.
     * Samples are over the given phase from [phaseStart] to [phaseEnd] and have size [size].
     * @return List of weighted samples
     */
    fun sampleList(phaseStart: Double, phaseEnd: Double, size: Int): List<Double> {
        var weightedSumList = List(size) { 0.0 }
        envelopes.mapIndexed { i, env ->
            val w = weights[i].value
            weightedSumList = weightedSumList add env?.sampleList(phaseStart, phaseEnd, size)?.map { it*w }
        }
        return weightedSumList
    }

    /**
     * For the given HashMap [target], starts to transition the weights over the [duration].
     * Optional [easing] from [Easing] enum from standard-OPENRNDR animatable package.
     */
    fun pushTransition(target: Map<Int, Double>, duration: Double, easing: Easing = Easing.None) {
        for (i in 0 until envelopeCount) {
            if (target.containsKey(i)) {
                weights[i].pushTransition(null, target[i]!!, duration, easing)
            }
        }
    }

    /**
     * For the given List [target], starts to transition the weights over the [duration] in seconds.
     * Assign list element 0 to weight.get(0), 1 to weight.get(1) and so on.
     * Under-length lists ignore remaining. Over-length lists are truncated.
     * Optional [easing] from [Easing] enum from standard-OPENRNDR animatable package.
     */
    fun pushTransition(target: List<Double>, duration: Double, easing: Easing = Easing.None) {
        for (i in 0 until envelopeCount) {
            if (i < target.size) {
                weights[i].pushTransition(null, target[i], duration, easing)
            }
        }
    }

    /**
     * After all transitions are over, set the weights to the values from [target].
     */
    fun setWeightsAfterTransitions(target: Map<Int, Double>) {
        for (i in 0 until envelopeCount) {
            if (target.containsKey(i)) {
                weights[i].pushTransition(null, target[i]!!, 0.0, Easing.None)
            }
        }
    }

    /**
     * After all transitions are over, set the weights to the values from [target].
     */
    fun setWeightsAfterTransitions(vararg target: Pair<Int, Double>) {
        val map = mapOf(*target)
        setWeightsAfterTransitions(map)
    }

    /**
     * Cancels all transitions, but keeps their last value as weights.
     */
    fun cancelAllTransitions() {
        weights.forEach { it.cancelTransitions() }
    }

}
