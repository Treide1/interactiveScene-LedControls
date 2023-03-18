import bpm.BpmRepo
import colors.ColorRepo
import led.BacklightLED
import led.LedRepo
import org.openrndr.*
import org.openrndr.animatable.easing.Easing
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.fx.blur.Bloom
import org.openrndr.extra.fx.blur.BoxBlur
import org.openrndr.math.Vector2
import org.openrndr.math.map
import org.openrndr.shape.Triangle
import utils.*

/**
 * TODO: add explanation here
 *
 * Vocabulary:
 * rel - relative,
 * abs - absolute,
 * fac - factor,
 * repo - repository (object that contains related data like ColorRepo and can be asked for certain values),
 * env - envelope (curve over [0, 1] -> R, is used as a mapping function)
 */
fun main() = application {
    configure {
        display = displays[1]
        fullscreen = Fullscreen.CURRENT_DISPLAY_MODE
    }
    program {
        // Special config
        mouse.cursorVisible = false

        // Render targets
        val offscreen = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }
        val preImage = offscreen.colorBuffer(0)
        val blur = BoxBlur()
        val bloom = Bloom()
        val blurred = colorBuffer(width, height)
        val bloomed = colorBuffer(width, height)
        val clearImage = colorBuffer(width, height)

        // Init repos
        val colorRepo = ColorRepo(
            ColorRGBa.fromHex("#dd00ff"),
            ColorRGBa.fromHex("#4000ff"),
            ColorRGBa.fromHex("#ff0090"),
            ColorRGBa.fromHex("#ffb300")
        )
        val ledRepo = LedRepo(40)
        val bpmRepo = BpmRepo(122.0, this)  // "Daily Routines" by Oliver Schories

        // SETUP //
        var holdSuspense = false

        val triangleShape = run {
            val size = 30.0
            val rot = 90.0

            val off = Vector2(size, 0.0)
            val posList = List(3) { off.rotate(360.0/3*it + rot) }
            Triangle(posList[0], posList[1], posList[2]).shape
        }

        fun createTriangleLED(pos: Vector2): BacklightLED {
            colorRepo.pick()
            val color = colorRepo.color
            return BacklightLED(triangleShape, pos, 0.0, color)
        }

        fun Drawer.drawBacklightLED(backlightLED: BacklightLED, luminanceFac: Double) {
            val (shape, pos, rot,  color) = backlightLED

            pushStyle()
            pushTransforms()

            shadeStyle = null
            fill =  color.shade(luminanceFac)
            stroke = ColorRGBa.BLACK
            translate(pos)
            rotate(rot)
            shape(shape)

            popStyle()
            popTransforms()
        }

        // MAIN CONTENT //
        val mainCurveEnv = bpmRepo.addEnvelopeFromSegments(2.0) {
            val att = .60 // attack dist
            val sus = .30 // sustain dist

            join(.50, att)
            join(.75, sus)
            join(1.0, sus)
            currentX = 1.0
            join(1.5, 1.0-att)
            join(1.75, 1.0-sus)
            join(2.0, 1.0-sus)
        }
        val luminanceEnv = bpmRepo.addEnvelopeFromSegments(8.0) {
            join(.05, 0.9) via Easing.CubicIn
            join(1.0, 1.0)
            join(2.0, 0.0) via Easing.CubicOut
            join(8.0, 0.0)
        }

        val mainTrigCount = 40
        val mainTrigsPerBeat = 10
        ledRepo.ledMap["main"] = List(mainTrigCount) { index ->
            val phase = index.toDouble() / mainTrigsPerBeat
            val sample = mainCurveEnv.sample(phase)

            val x = (phase%4.0) .map(0.0, 4.0, 0.2, 0.8) * width
            val y = sample      .map(0.0, 1.0, 0.8, 0.2) * height
            createTriangleLED(Vector2(x, y))
        }

        // LISSAJOUS //

        // CORNERS //
        ledRepo.ledMap["corner"] = List(4) { index ->
            val x = if (index.plusMod(1, 4) > 1) vw(.1) else vw(.9)
            val y = if (index.plusMod(2, 4) > 1) vh(.1) else vh(.9)
            createTriangleLED(Vector2(x, y))
        }
        val cornerLuminanceEnv = bpmRepo.addEnvelopeFromSegments(4.0) {
            join(0.125, 1.0) via Easing.SineInOut
            join(1.0, 0.0) via Easing.CubicIn
            join(4.0, 0.0)
        }

        // DRAW //
        extend {
            //drawer.clear(ColorRGBa.GRAY) // Debugging only, gray background

            clearImage.copyTo(preImage)

            val phase = bpmRepo.phase // get once to avoid timing issues

            ledRepo.ledMap["corner"]!!.forEachIndexed { index, led ->
                val lum = cornerLuminanceEnv.sample(phase, index.toDouble())
                drawer.isolatedWithTarget(offscreen) { drawBacklightLED(led, lum) }
            }
            ledRepo.ledMap["main"]!!.forEachIndexed { index, led ->
                val relIndex = index / mainTrigCount.toDouble()
                val lum = luminanceEnv.sample(phase, relIndex)
                led.rotation = luminanceEnv.sample(phase, relIndex-0.1).map(0.0, 1.0, 0.0, 60.0)
                led.color = colorRepo.run { pick(); color }
                drawer.isolatedWithTarget(offscreen) { drawBacklightLED(led, lum) }
            }
            blur.window = 30
            // bloom.gain = 0.5
            bloom.blendFactor = 0.99


            blur.apply(preImage, blurred)
            bloom.apply(preImage, bloomed)
            drawer.image(bloomed)
            drawer.image(blurred)

        }

        // CONTROLS //
        keyboard.keyDown.listen {
            when (it.key) {
                KEY_ESCAPE -> application.exit()
                KEY_SPACEBAR -> bpmRepo.reset()
            }
            when (it.name) {
                "p" -> colorRepo.setNextPickFunc()
                "h" -> holdSuspense = true
                // "c" -> cornerInstancingObf::isSkippingFunction.flip()
                // "m" -> mainEnvelopeInstancingObf::isSkippingFunction.flip()
                // "l" -> lissajousInstancingObf::isSkippingFunction.flip()
            }
        }

        keyboard.keyUp.listen {
            when (it.name) {
                "h" -> holdSuspense = false
            }
        }

        mouse.buttonDown.listen {
            when (it.button) {
                // MouseButton.LEFT -> addTriangle(mouse.position)
                else -> return@listen
            }
        }
    }
}