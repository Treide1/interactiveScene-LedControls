@file:Suppress("unused")

package utils

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.math.map
import org.openrndr.shape.Rectangle
import kotlin.math.*

/**
 * Given a 2-dimensional list [list2D] and a String item [item],
 * returns the indices i,j as list such that list2D[i, j] = item, or null if not found.
 */
fun getTupleIndex(item: String, list2D: List<List<String>>): List<Int>? {
    list2D.forEachIndexed { i, list ->
        list.forEachIndexed { j, s ->
            if (item == s) return listOf(i, j)
        }
    }
    return null
}


/**
 * Split this String into substrings of that length.
 * Collected in list. Empty string yields empty list.
 * Last string might be shorter than specified length.
 */
fun String.splitByLength(length: Int = 1): List<String> {
    var from = 0
    var to = length
    val result = mutableListOf<String>()

    while (from < this.length) {
        result += this.substring(from, to.coerceAtMost(this.length))
        from += length
        to += length
    }

    return result
}


/**
 * Hull curve on [edge0, edge1], rescaled to [0,1]
 * Values are 0 before domain, and 1 after domain.
 * Smoothly transitions from (0,0) to (1,1) with famous smoothstep function.
 *
 */
fun Double.smoothstep( edge0: Double, edge1: Double): Double {
    val t = this.map(edge0, edge1, 0.0, 1.0)
    return when {
        t < 0.0 -> 0.0
        t > 1.0 -> 1.0
        else -> 3*t.pow(2) - 2*t.pow(3)
    }
}


/**
 * Hull curve on [0,1] with 0 elsewhere.
 * Linear segments from (0,0) to (.5, 1) to (1, 0), forming a spike.
 */
fun hullSpike(t: Double): Double = when {
    t < 0.0 || t > 1.0  -> 0.0
    t < 0.5             -> t*2.0
    else                -> 2.0 - t*2.0
}


fun Double.toDegrees(): Double{
    return this.map(0.0, 2* PI, 0.0, 360.0)
}


/**
 * Get a sublist, where the indices 'from' and 'to' are allowed to be any integer.
 * The list indices wraps around below 0 and above its size, as if the input list in circular.
 *
 * Returned sublist includes element at 'from' and excludes element at 'to', even in backwards direction.
 */
fun <T> List<T>.circularSublist(from: Int, to: Int): List<T> {

    var a = from
    var b = to

    // If 'from' is after 'to',
    // then call this fun with reversed order.
    // Left inclusive, right exclusive call.
    // So [from: 0, to: -3] -> range: (0, -1, -2)
    // with from = to + 1 and to = from + 1
    // becomes [from: -2, to: 1] -> (-2, -1, 0)
    if (a > b) {
        val tmp = a
        a = b + 1
        b = tmp + 1
    }
    // Assume: a < b

    // Take the kotlin-specific negative version first.
    // Division and Modulo work on negative numbers as if negation was a weaker operator.
    // E.g. -3 mod 4 is treated like
    //  -3 % 4 = -(3 % 4) = -(3) = -3
    // We intentionally overshoot. Min overshoot: 1, max overshoot: total
    val total = this.size
    if (a < 0) {
        val lift = abs(a / total + 1) * total
        a += lift
        b += lift
    }
    // Assume: a at least 0 ^ a < b
    // Thus: [a, b) is subset of 0-inclusive naturals

    // If the nextTotal is less than b, than we cut the range [a,b) at nextTotal.
    // We concat the recursive call on [a, nextTotal) + [nextTotal,b)
    val diffToNextTotal = total - (a % total)
    val nextTotal = a + diffToNextTotal
    if (nextTotal < b) {
        return circularSublist(a, nextTotal) + circularSublist(nextTotal, b)
    }
    // Assume: nextTotal >= b ^ [a, b) is positive interval
    // Thus: [a, b) is subset of [0, nextTotal)

    // Recursion stops for returning the canonical sublist.
    val fall = a - (a%total)
    return subList(a - fall, b - fall)

}


/**
 * Shows a small coordinate system at this drawer's current translation, rotation and scale.
 * Useful for debugging TRS relations.
 */
fun Drawer.showCoordinateSystem(scl: Double) {

    val axis = Rectangle(-scl, 0.0, scl*2, 1.0)
    this.isolated {
        stroke = ColorRGBa.RED
        this.rectangle(axis)
        this.rotate(90.0)
        stroke = ColorRGBa.BLUE
        this.rectangle(axis)
    }

}


/**
 * sqrt(x), but as extension function. Allows chain calls.
 */
fun Double.sqrt(): Double {
    return if (this < 0.0 ) Double.NaN else sqrt(this)
}


/**
 * Calculates the linear interpolation (lerp) of x for the given control points.
 * @param x Input value to be lerped.
 * @param controlPointMap hashMap of control points, where (x,y) being a point means 'map.get(x) = y'.
 * @return Lerp value for x in the "line segment" of the two corresponding control points. Should x be out of range, then 0.0 is returned.
 */
fun lerpBetweenControlPoints(x: Double, controlPointMap: HashMap<Double, Double>): Double {

    // Partition points based on x value being smaller than x (first) or greater than x (second).
    // Then find control points just before, and just after x.
    // 'Just before' means greatest lower bound of x, 'just after' means least upper bound of x.
    // Returns 0.0, if x is out of range.
    val partition = controlPointMap.keys.partition { key -> key <= x }
    if (partition.first.isEmpty() || partition.second.isEmpty()) return 0.0

    val lowerControlX = partition.first.max() // Greatest lower bound of x
    val lowerControlY = controlPointMap[lowerControlX]!!
    val upperControlX = partition.second.min() // Least upper bound of x
    val upperControlY = controlPointMap[upperControlX]!!
    return x.map(lowerControlX, upperControlX, lowerControlY, upperControlY)
}

/** Width measured in Viewport width percentage of this program. Returns absolute pixel width.*/
fun Program.vw(p: Double): Double = this.width * p

/** Height measured in Viewport height percentage of this program. Returns absolute pixel height.*/
fun Program.vh(p: Double): Double = this.height * p

/**
 * Same as Double-only map, but treats all Int as Double.
 * Just a QOL function to type less.
 */
fun Int.map(beforeLeft: Int, beforeRight: Int, afterLeft: Double, afterRight: Double) : Double {
    return this.toDouble().map(beforeLeft.toDouble(), beforeRight.toDouble(), afterLeft, afterRight)
}

/**
 * Given this Double, return all copies that identical to this given the mod value within start to end range.
 *
 * @receiver Double value the modular copies are calculated of
 * @param start Range start. Boundary included.
 * @param end Range end. Boundary included.
 * @param mod Value of which [this] is taken the modulo of
 * @return All the values v such that: v = this (mod [mod]) and v in [start, end]. In case of start > end, list is reversed.
 */
fun Double.modularCopies(start: Double, end: Double, mod: Double): List<Double> {

    // Guard the case of mod being 0 or less. Exceptions are evil, just return empty list.
    if (mod <= 0) return listOf()

    // Calculate the result from low to high.
    // In case of swap, reverse the list at the end
    val low = min(start, end)
    val high = max(start, end)
    val isSwapped = (low == end) // If low == end, then start >= end. Thus, swap at the end yields correct list.

    // Calculate the factor lowFac s.t.
    // (this + mod*lowFac) = first value,
    // (this + mod*highFac) = last value
    val distToLow = this - low
    val lowFac = -(distToLow / mod).toInt() // Calc: How often does mod fit into distToLow ? Then, negate.

    val distToHigh = high - this
    val highFac = (distToHigh / mod).toInt() // Calc: How often does mod fit into distToLow ? Already positive.

    // For each factor from lowFac to highFac, calculate the value
    // Note: The value this does not need to be in range.
    // But if it is in range with fac=0, it is unchanged ! (No floating point error for +0.0)
    val result = (lowFac..highFac).map { fac ->
        this + mod * fac
    }

    // Return the result, swapped or unswapped depending on (start, end).
    return if (!isSwapped) result else result.reversed()
}

/**
 * Let the drawer display lines of text, starting from the upper right corner (firstX, firstY) with
 * a line break down after each line.
 *
 * Line breaks move the in y direction with a total of margin.
 */
fun Drawer.displayLinesOfText(linesOfText: List<String>, firstX: Double = 20.0, firstY: Double = 20.0, margin: Double = 25.0) {
    val x = firstX
    var y = firstY+margin

    linesOfText.forEach { line ->
        this.text(line, x, y)
        y += margin
    }
}

/**
 * Linear interpolation from A (= [this]) to [B] with percentage [perc].
 *
 * @receiver lerpSource
 * @param B lerpTarget
 * @param perc percentage to lerp from A to B
 */
fun Double.lerp(B: Double, perc: Double): Double {
    return this*(1-perc) + B*perc
}

/**
 * WIP: Idea for dynamic recalculation if primary value changes, so that secondary value requires update.
 * That might trigger a ternary value to update and so on.
 */
fun <T> calculation(function: () -> T): Calculation<T> {
    return Calculation(function)
}

/**
 * WIP
 */
class Calculation<T>(val function: () -> T) {

    var value = function()
        private set

    fun update() {
        value = function()
    }
}

/**
 * Returns the angle of this [Vector2] in degrees.
 */
fun Vector2.getAngle(): Double {
    return atan2(this.x, this.y).asDegrees
}
