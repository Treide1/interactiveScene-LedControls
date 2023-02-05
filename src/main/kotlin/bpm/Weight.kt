package bpm

import org.openrndr.animatable.easing.Easing

class Weight(private var w: Double = 0.0){

    data class Transition(val b: Double, val c: Double, val d: Double, val easing: Easing)

    private val transitions = mutableListOf<Transition>()

    /**
     * Push a transition to the queue.
     * This weight will transition from [start] to [end] over [duration] (with optional [easing]).
     * @param start starting value (or null to infer seamless value)
     * @param end end value
     * @param duration duration of transition in seconds
     * @param easing from [Easing] enum, default on None (linear)
     */
    fun pushTransition(
        start: Double? = null, // start value, null to infer seamless value
        end: Double, // end value
        duration: Double, // duration
        easing: Easing = Easing.None // easing function
    ) {
        val _start = start ?: if (hasTransitions()) transitions.last().run { b + c } else w
        transitions += Transition(_start, end - _start, duration, easing)
    }

    var t = 0.0 // time, relative to first transition in queue

    fun tick(deltaTime: Double) {
        if (hasTransitions()) t += deltaTime // Only tick if there are transitions to complete
        else t = 0.0 // Else, reset time
    }

    /**
     * Evaluate this weight. Right now.
     */
    val value: Double
        get() {
            // Pop transitions that are out of duration
            while (hasTransitions() && t > transitions.first().d) {
                val completed = transitions.removeFirst()
                w = completed.run {b + c} // Set weight to end value
                t -= completed.d // Reduce time by duration
            }
            // Eval first transition that is not completed
            if (hasTransitions()) {
                val uncompleted = transitions.first()
                w = uncompleted.run { easing.easer.ease(t, b, c, d) }
            }

            return w
        }

    /**
     * Clear all transitions and set weight to 0.0.
     */
    fun set(newW: Double) {
        transitions.clear()
        w = newW
    }

    /**
     * Cancel all transitions.
     */
    fun cancelTransitions() {
        w = value
        transitions.clear()
    }

    /**
     * Returns if this weight has uncompleted transitions.
     */
    fun hasTransitions(): Boolean = (transitions.isNotEmpty())

}