package bpm

import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.Drawer

/**
 * Interface for objects that want to subscribe to a [Clock].
 * Implement [tick] to receive the program's runtime in seconds
 */
interface ClockSubscriber {
    fun tick(seconds: Double, deltaTime: Double, frameCount: Int)
}

/**
 * Clock receiving data about time and frames from the program it was extended on.
 * Add or remove [ClockSubscriber]s, single or in bulk.
 * Added subscribers will receive time/frame advancements via [ClockSubscriber.tick].
 * @constructor Created as Extension. Empty subscriber set.
 */
class Clock : Extension {
    override var enabled = true

    private val subscribers = mutableSetOf<ClockSubscriber>()

    /**
     * Add many ClockSubscribers to this Clock.
     * Added elements will receive ticks from Clock.
     */
    fun add(vararg subs: ClockSubscriber) {
        subscribers.addAll(subs)
    }

    /**
     * Remove many ClockSubscribers from this Clock.
     * Removed elements will no longer receive ticks from Clock.
     */
    fun remove(vararg subs: ClockSubscriber) {
        subscribers.removeAll(subs.toSet()) // Cast to set to improve performance
    }

    /**
     * If [previous] was added to this Clock, then it will be removed and [new] will be added.
     * Thus, [previous] will no longer receive ticks and [new] will begin to receive ticks.
     */
    fun replace(previous: ClockSubscriber, new: ClockSubscriber) {
        if (subscribers.contains(previous)) {
            subscribers.remove(previous)
            subscribers.add(new)
        }
    }

    /**
     * For each given ClockSubscriber, toggles if it receives ticks from this Clock.
     * If it received ticks before, now it doesn't.
     * If it didn't receive ticks before, now it does.
     */
    fun toggle(vararg clockSubscribers: ClockSubscriber) {
        clockSubscribers.forEach { sub ->
            if (!subscribers.contains(sub)) subscribers.add(sub)
            else subscribers.remove(sub)
        }
    }

    /**
     * Overrides [beforeDraw] from [Extension].
     * All added BeatEnvelopes will be ticked with values from running [program].
     */
    override fun beforeDraw(drawer: Drawer, program: Program) {
        subscribers.forEach { it.tick(program.seconds, program.deltaTime, program.frameCount) }
    }

}