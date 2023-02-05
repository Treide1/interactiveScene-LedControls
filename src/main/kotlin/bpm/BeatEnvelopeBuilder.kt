package bpm

import org.openrndr.animatable.easing.Easer
import org.openrndr.animatable.easing.Easing
import org.openrndr.animatable.easing.Linear

// TODO:
//  This class and its methods need documentation.
class BeatEnvelopeBuilder internal constructor(){

    private val segments = mutableListOf<EnvelopeSegment>()

    var lastT = 0.0
    var lastX = 0.0

    fun segment(fromT: Double, toT: Double, fromX: Double, toX: Double): EnvelopeSegment {
        if (fromT >= toT) throw IllegalArgumentException(
            "Time has to progress forward. Thus, segment(...) requires 'fromT' being less than 'toT'. " +
                    "Found fromT=$fromT, toT=$toT instead."
        )

        val seg =  EnvelopeSegment(fromT, toT, fromX, toX)
        if (seg.hasTimelineOverlap()) throw IllegalArgumentException("TODO")

        segments += seg
        lastT = seg.toT
        lastX = seg.toX
        return seg

    }

    fun segmentJoin(toT: Double, toX: Double): EnvelopeSegment = segment(lastT, toT, lastX, toX)

    infix fun EnvelopeSegment.via(interpolationFunc: (Double) -> Double) {
        this.easer = object : Easer {
            override fun ease(t: Double, b: Double, c: Double, d: Double): Double {
                val normalizedT = t/d
                return b + interpolationFunc(normalizedT) * c
            }

            // unused in this implementation
            override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = 0.0
        }
    }

    infix fun EnvelopeSegment.via(easing: Easing) {
        this.easer = easing.easer
    }

    infix fun EnvelopeSegment.via(easer: Easer) {
        this.easer = easer
    }

    private fun build(bpm: Double, beatsPerLoop: Int) : BeatEnvelope {
        // 1. We complete segments to be a complete timeline.
        // Any gaps are filled with segments with zeroEaser.

        // Sort with complexity O(n log n)
        segments.sortBy { it.fromT }
        var t = 0.0
        var i = 0
        while (i < segments.size ) {
            val nextSeg = segments[i]
            val nextT = nextSeg.fromT
            // If there is a "gap" between last t and fromT, fill it with segment with zeroEaser.
            // Then set t to correct fromT. Increment i to skip zeroEaser segment.
            if (t < nextT) {
                segments += EnvelopeSegment(t, nextT, 0.0, 0.0).also { it via zeroEaser }
                t = nextT
                i++
            }
            // Increment i for drawn segment 'nextSeg'.
            i++
        }
        // Convert to immutable list to feed to closure.
        val timeline = segments.toList()

        // Define evaluation by timeline iteration.
        // The first segment sufficing is used for evaluation.
        val evaluation = { t: Double ->
            var result = 0.0
            for (seg in timeline) {
                if (seg.fromT <= t && t <= seg.toT) {
                    // Call the easing function with according t, b, c, d
                    // t - is now the relative t over the duration d
                    // b - starting value in x
                    // c - delta in x (over fromT to toT)
                    // d - delta in t (duration)
                    result = seg.easer.ease(
                        t - seg.fromT,
                        seg.fromX,
                        seg.deltaX,
                        seg.deltaT
                    )
                    break
                }
            }
            result
        }
        return BeatEnvelope(bpm, beatsPerLoop, evaluation)
    }

    class EnvelopeSegment(val fromT: Double, val toT: Double, val fromX: Double, val toX: Double, var easer: Easer = Linear()) {
        val deltaT = toT - fromT
        val deltaX = toX - fromX
    }

    /**
     * Returns boolean whether this segment overlaps with any other segment so far.
     *
     * Note: segments are treated left-inclusive right-exclusive i.e. as half-open time interval.
     * Thus, [..., someT) and [someT, ...) do not overlap.
     */
    private fun EnvelopeSegment.hasTimelineOverlap(): Boolean {
        return !segments.all { other ->
            // Ignore self intersection
            if (this == other) return true

            // If other's right is less-or-equal to this' left
            // or if this' right is less-or-equal to other's left,
            // then timelines don't overlap.
            other.toT <= this.fromT || this.toT <= other.fromT
        }
    }

    companion object {
        private val zeroEaser = object : Easer {
            override fun ease(t: Double, b: Double, c: Double, d: Double): Double = 0.0
            override fun velocity(t: Double, b: Double, c: Double, d: Double): Double = 0.0
        }

        fun BeatEnvelope.buildBySegments(block: BeatEnvelopeBuilder.() -> Unit): BeatEnvelope {
            val builder = BeatEnvelopeBuilder()
            builder.block()
            return builder.build(this.bpm, this.beatsPerLoop)
        }
    }
}




