import bpm.BpmRepo
import colors.ColorRepo
import led.BacklightLED
import led.LedRepo
import org.openrndr.*
import org.openrndr.animatable.easing.Easing
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.shadeStyle
import org.openrndr.extra.noise.simplex
import org.openrndr.math.Vector2
import org.openrndr.math.map
import org.openrndr.panel.elements.round
import org.openrndr.shape.Triangle
import utils.vh
import utils.vw
import kotlin.math.ceil

fun main() = application {
    configure {
        fullscreen = Fullscreen.CURRENT_DISPLAY_MODE
    }
    program {
        // Special config
        mouse.cursorVisible = false

        // Init repos
        val colorRepo = ColorRepo(
            ColorRGBa.fromHex("#dd00ff"),
            ColorRGBa.fromHex("#4000ff"),
            ColorRGBa.fromHex("#ff0090"),
            ColorRGBa.fromHex("#ffb300")
        )
        val ledRepo = LedRepo(25)
        val bpmRepo = BpmRepo(122.0, this)  // "Daily Routines" by Oliver Schories

        // TODO: val noiseRepo = NoiseRepo()
        //   val noiseFunc = { seed: Int, x: Int -> simplex(seed, x*0.01) * 0.5 + 0.5 }

        // Configuration
        val controlPointEnv = bpmRepo.addEnvelopeFromSegments(2.0) {
            join(.50, .40)
            join(1.0, .25) via Easing.CubicIn
            currentX = .70                              // Can be set any time, if you want to
            join(1.5, .25)
            join(2.0, .00)                     // Exceeding the beatCount value will do nothing
        }

        fun addTriangle(pos: Vector2) {
            ledRepo.addLED(getTriangleLED(pos, 30.0, 90.0, colorRepo.color))
            colorRepo.pick()
        }

        var holdSuspense = false

        bpmRepo.addOnBeatFunction(.10) {


            val phase = bpmRepo.phase
            val sample = controlPointEnv.sample(phase)

            val v =  if(!holdSuspense) Vector2(
                phase.mod(4.0).map(0.0, 4.0, 0.2, 0.8) * width,
                sample.map(0.0, 1.0, 0.8, 0.2) * height
            ) else Vector2(vw(0.2), vh(0.8))
            addTriangle(v)
        }

        var cornerCounter = 0
        bpmRepo.addOnBeatFunction(1.0, 0.5) {
            cornerCounter++
            val x = if (cornerCounter.plus(1)%4 > 1) vw(.1) else vw(.9)
            val y = if (cornerCounter.plus(2)%4 > 1) vh(.1) else vh(.9)
            addTriangle(Vector2(x,y))
        }


        keyboard.keyDown.listen {
            when (it.key) {
                KEY_ESCAPE -> application.exit()
                KEY_SPACEBAR -> bpmRepo.reset()
            }
            when (it.name) {
                "p" -> colorRepo.setNextPickFunc()
                "h" -> holdSuspense = true
            }
        }

        keyboard.keyUp.listen {
            when (it.name) {
                "h" -> holdSuspense = false
            }
        }

        mouse.buttonDown.listen {
            when (it.button) {
                MouseButton.LEFT -> addTriangle(mouse.position)
                else -> return@listen
            }
        }

        // DRAW
        extend {
            val ms = ledRepo.maxSize
            val cs = ledRepo.ledList.size
            val sizeFac = 1.0 / (ms - 1)
            val relPhase = bpmRepo.phase % 1.0
            val phaseFac = relPhase.map(0.0,1.0,0.8,0.2)

            //drawer.clear(ColorRGBa.GRAY)

            ledRepo.ledList.forEachIndexed { i, led ->
                val ni = (ms - cs + i)
                val lum = ni * sizeFac * phaseFac
                drawer.drawBacklightLED(led, 0.6, lum)
            }
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////////

private var counter = 0
fun Program.getRandomScreenPos(noiseFunc: (seed:Int, x: Int) -> Double) : Vector2 {
    counter++
    return Vector2(noiseFunc(42, counter) * width, noiseFunc(43, counter) * height)
}

/////////////////////////////////////////////////////////////////////////////////////

fun getTriangleLED(pos: Vector2, size: Double, rot: Double, color: ColorRGBa = ColorRGBa.PINK) : BacklightLED {
    val off = Vector2(size, 0.0)
    val posList = List(3) { pos + off.rotate(360.0/3*it + rot) }
    val shape = Triangle(posList[0], posList[1], posList[2]).shape
    return BacklightLED(shape, pos, color)
}

/////////////////////////////////////////////////////////////////////////////////////

fun Drawer.drawBacklightLED(backlightLED: BacklightLED, opacity: Double, luminosity: Double) {
    val (shape, center, color) = backlightLED

    pushStyle()
    pushTransforms()

    this.shadeStyle = shadeStyle {
        fragmentTransform = """
            vec2 pos = c_screenPosition.xy;
            pos.x = pos.x - ${center.x.round(3)};
            pos.y = pos.y - ${center.y.round(3)};
            x_fill.rgba *= vec4(1.0/(1.0+length(pos)*0.02)*1.2);
            """.trimIndent()
    }
    val c = color.toHSVa().copy(v = luminosity, alpha = opacity).toRGBa()
    this.fill = c
    this.rectangle(0.0, 0.0, width.toDouble(), height.toDouble())

    popStyle()
    popTransforms()

    this.fill = c
    this.shape(shape)
}

/////////////////////////////////////////////////////////////////////////////////////////////////////

// Add to utils in playground (or make submodule)

/**
 * Calculates the mathematical correct modulus of [this] Double, constrained to given range.
 * The range is defined as the bounds-inclusive interval ([atLeast], [atMost]).
 * Throws [IllegalArgumentException] in bad argument cases.
 */
fun Double.modulusToRange(mod: Double, atLeast: Double = 0.0, atMost:Double = Double.MAX_VALUE): Double {
    if (mod <= 0) throw IllegalArgumentException("Modulus for non-positive number ($mod) is not allowed !")
    if (atLeast > atMost) throw IllegalArgumentException("Lower bound $atLeast must not exceed upper bound $atMost !")
    var result = this

    if (this > atMost) {
        val dist = this - atMost
        val nextMult = ceil(dist/mod) *mod
        result -= nextMult
    } else if (this < atLeast) {
        val dist = atLeast - this
        val nextMult = ceil(dist/mod) *mod
        result += nextMult
    }

    if (result in atLeast..atMost) return result
    else throw IllegalArgumentException("Calculated $this mod $mod = $result, out of bounds for ($atLeast, $atMost) !")
}