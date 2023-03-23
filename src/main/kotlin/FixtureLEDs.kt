import bpm.BpmRepo
import colors.ColorRepo
import fx.Darkify
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.color.ColorRGBa.Companion.TRANSPARENT
import org.openrndr.color.ColorXSVa
import org.openrndr.draw.*
import org.openrndr.extra.color.palettes.rangeTo
import org.openrndr.extra.fx.blend.Overlay
import org.openrndr.extra.fx.blur.ApproximateGaussianBlur
import org.openrndr.extra.fx.blur.FrameBlur
import org.openrndr.extra.fx.blur.GaussianBloom
import org.openrndr.extra.noise.fastFloor
import org.openrndr.extra.shadestyles.radialGradient
import org.openrndr.extra.videoprofiles.H265Profile
import org.openrndr.extra.videoprofiles.gif
import org.openrndr.extra.videoprofiles.h265
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.ffmpeg.VideoWriter
import org.openrndr.ffmpeg.VideoWriterProfile
import org.openrndr.math.Vector2
import org.openrndr.math.smoothstep
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Segment
import org.openrndr.shape.ShapeContour
import utils.*
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.reflect.KProperty

fun main() = application {
    configure {
        width = 640; height = 480
        fullscreen = Fullscreen.CURRENT_DISPLAY_MODE
        display = displays.last()
    }
    program {
        val recorder = ScreenRecorder().apply {
            outputToVideo = false
            gif()
        }
        extend(recorder)
        mouse.cursorVisible = true

        // Init BPM
        val bpmRepo = BpmRepo(130.0, this)
        val colorRepo = ColorRepo(
        *( List(32)
            { index ->
                index * 360.0/32
            }.map { xue ->
                ColorXSVa(xue, 1.0, 1.0).toRGBa()
            }
            .toTypedArray()
                )
    )

        // Init pipeline
        val rt = renderTarget(width, height) {
            colorBuffer(ColorFormat.RGBa, ColorType.FLOAT32)
            depthBuffer()
        }
        val drawBuffer = rt.colorBuffer(0)
        val joinBuffer = drawBuffer.createEquivalent()

        // Init Fx
        val blurFx = ApproximateGaussianBlur().apply {
            window = 16 // by GuiSlider("blurFx/window/", 16, 0, 30)
            sigma = 3.0
            spread = 1.0
            gain = 1.4
        }
        val bloomFx = GaussianBloom().apply {
            window = 16 // by GuiSlider("bloomFx/window/, value = 8, min = 2.0, max = 10.0) { slider -> slider.roundToInt() }
        }
        val darkifyFx = Darkify().apply {
            darkFac = 0.8
        }
        val frameBlurFx = FrameBlur().apply {
            blend = 0.25
        }

        // Init blend
        val blend = Overlay()

        // Setup positions and patterns
        // Pillars
        val pillarWidth by 2.0 / 3.0 * width
        val pillarStrokeWidth by 20.0
        val pillarCount = 8
        val pillarDist by pillarWidth / (pillarCount - 1)

        fun Drawer.pillar(index: Int) {
            stroke = colorRepo.colorList[14+index]
            strokeWeight = pillarStrokeWidth

            val x = (width - pillarWidth)/2.0 + index*pillarDist
            val y0 = vh(0.7)
            val y1 = vh(0.35)
            lineSegment(x, y0, x, y1)
        }

        // Spots
        val spotRadius by pillarStrokeWidth*1.5

        fun Drawer.spot(index: Int) {
            fill = null
            stroke = colorRepo.colorList[index.plusMod(22, 32)]
            strokeWeight = pillarStrokeWidth*0.8

            val x = (width - pillarWidth)/2.0 + index*pillarDist
            val y = vh(0.2)
            circle(x, y, spotRadius)
        }

        // Chaser
        val chaserFlight = vwh(0.6, 0.6)
        val chaserOrg1 = vwh(0.8, 0.7)
        val chaserOrg2 = vwh(0.7, 0.8)

        fun Drawer.chaser(index: Int, group: Int) {
            val flipX = group / 2 == 1
            val flipY = group % 2 == 1
            fun Vector2.toViewport(): Vector2 {
                val v = this.mix(chaserFlight, index / 4.0)
                val x = if (flipX) width - v.x else v.x
                val y = if( flipY) height - v.y else v.y
                return Vector2(x, y)
            }

            fill = null
            stroke = colorRepo.colorList[18]
            strokeWeight = pillarStrokeWidth*0.8

            val org1 = chaserOrg1.toViewport()
            val org2 = chaserOrg2.toViewport()
            val control = vwh(1.0, 1.0).toViewport()

            contour(
                listOf(
                    Segment(org1, control, org2)
                ).let { segs ->
                    ShapeContour.fromSegments(segs, closed = false)
                }
            )
        }

        // Lissajous
        val lissajousBounds = Rectangle(vw(0.1), vh(0.2), vw(0.8), vh(0.6))
        val lissajousShape = run {
            val segCount = 128
            val domain = (0.0..1.0)
            val a = 1.0
            val b = 2.0
            val d = TAU/8.0
            val points = List(segCount) { index ->
                val phase = index.map(0, segCount, domain.start, domain.endInclusive)
                val u = sin(a*phase*TAU)*0.5 + 0.5
                val v = sin(b*phase*TAU + d)*0.5 + 0.5
                lissajousBounds.vector2FromUv(u, v)
            }
            ShapeContour.fromPoints(points, closed = true)
        }

        fun Drawer.lissajous(phase: Double) {
            fill = null
            shadeStyle = radialGradient(
                colorRepo.colorList[0], colorRepo.colorList[15],
                length = 0.5, offset = Vector2(0.5, 0.5), exponent = 1.0
            )
            stroke = colorRepo.color
            strokeWeight = pillarStrokeWidth*0.5
            val start = (phase + 0.0 + sin(phase*2) * 0.5) / 8.0
            val end =   (phase + 1.0 + cos(phase*2) * 0.5) / 8.0
            val sub = lissajousShape.sub(start, end)
            contour(sub)
        }

        // Tentacle
        val tentacleY = vh(0.8)
        val tentacleJoint = Vector2(vw(0.0), tentacleY)
        val tentacleTipYRange = (vh(0.65)..vh(0.95))
        val tentacleTipX = vw(0.55)
        val tentacleWidthRange = (50.0..10.0)
        val tentacleSegCount = 30
        val tentacleColorRange = (colorRepo.colorList[28]..colorRepo.colorList[31])

        fun Drawer.tentacle(phase: Double) {
            val x0 = tentacleJoint
            val x1 = (phase/4.0).let {
                smoothstep(-1.0, 1.0, sin(it*TAU))
            }.let { pos ->
                val x = tentacleTipX
                val y = tentacleTipYRange.lerp(pos)
                Vector2(x, y)
            }

            val c1 = Vector2(
                x0.mix(x1, +1/3.0).x,
                x0.mix(x1, -1/3.0).y
            )
            val c2 = Vector2(
                x0.mix(x1, +2/3.0).x,
                x0.mix(x1, -4/3.0).y
            )
            val shape = listOf(
                Segment(x0, c1, c2, x1)
            ).let { segs ->
                ShapeContour.fromSegments(segs, closed = false)
            }

            val lineSegs = shape.sampleEquidistant(tentacleSegCount+1).segments
            lineSegs.forEachIndexed { index, lineSeg ->
                fill = null
                stroke = tentacleColorRange.index(index * 1.0 / tentacleSegCount)
                strokeWeight = tentacleWidthRange.lerp(index * 1.0 / tentacleSegCount)

                val center = drawer.bounds.center

                lineSeg.run {
                    listOf(this,
                        copy(
                            start.mirror(center),
                            control.map { it.mirror(center) }.toTypedArray(),
                            end.mirror(center) ) ) }
                    .forEach { seg ->
                        segment(seg) } } }

        // Init runtime data structures
        // -> None

        extend {
            // Program state
            val inputScheme = listOf(
                "w", "a", "s", "d", "q", "e"
            )
            val keys = keyboard.pressedKeys.toList().filter { input -> input in inputScheme }

            // Draw procedure
            val phase = bpmRepo.phase
            val kickCounter = phase.toIntervalCount(1.0) % pillarCount
            val flickerCounter = phase.toIntervalCount(0.25) % 2
            val chaserCounter = phase.toIntervalCount(0.25) % 8
            val lissajousPhase = phase % 8.0
            val tentaclePhase = phase % 4.0

            onRenderTarget(rt, clearColor = TRANSPARENT) {

                // S -> Pillars
                if ("s" in keys) pillar(kickCounter)
                // W -> Spots
                if ("w" in keys) (0 until pillarCount).filter { it % 2 == flickerCounter }.forEach {
                    spot(it)
                }
                // A, D -> Chaser Upper/Lower
                if ("a" in keys) chaserCounter.let { if (it<4) listOf(0, 2).forEach { group -> chaser(it, group) } }
                if ("d" in keys) chaserCounter.let { if (it<4) listOf(1, 3).forEach { group -> chaser(it, group) } }

                // Q -> Lissajous curve
                if ("q" in keys) lissajous(lissajousPhase)

                // E -> Tentacle
                if ("e" in keys) tentacle(tentaclePhase)
            }

            // PostFx
            blurFx.apply(drawBuffer, drawBuffer)

            // Accumulate
            darkifyFx.apply(joinBuffer)
            blend.apply(arrayOf(drawBuffer, joinBuffer), joinBuffer)
            frameBlurFx.apply(joinBuffer)
            bloomFx.apply(joinBuffer)

            // Display
            drawer.image(joinBuffer)

            // Debugging
            //drawer.image(drawBuffer)
            drawer.displayLinesOfText(
                listOf("Controls:") +
                inputScheme.map { possibleInput ->
                    if (possibleInput in keys) possibleInput.toUpper() else ""
                }
            )
        }

        // Assign Input
        keyboard.keyDown.listen {
            when(it.key) {
                KEY_ESCAPE -> application.exit()
                KEY_SPACEBAR -> bpmRepo.reset()
            }
            if (it.name == ".") {
                recorder.outputToVideo++
                mouse.cursorVisible++
                println(if (recorder.outputToVideo) "Recording" else "Paused")
            }
        }
    }
}

private fun ClosedFloatingPointRange<Double>.lerp(t: Double): Double = start.lerp(endInclusive, t)

operator fun Boolean.inc() : Boolean = !this

private fun String.toUpper() = this.uppercase(Locale.getDefault())
private fun Double.plusMod(add: Double, mod: Double): Double = (this+add)%mod

private fun Rectangle.vector2FromUv(u: Double, v: Double): Vector2 {
    return corner + Vector2(u*width, v*height)
}

/**
 * Viewport width-height as Vector2 from UV coords
 */
fun Program.vwh(u: Double, v: Double) = Vector2(u * width, v * height)

private fun Filter.apply(colorBuffer: ColorBuffer) {
    this.apply(colorBuffer, colorBuffer)
}

private operator fun <T> T.getValue(nothing: Nothing?, property: KProperty<*>): T {
        return this
}

private fun Program.onRenderTarget(rt: RenderTarget, clearColor: ColorRGBa? = null, function: Drawer.() -> Unit) {
    this.drawer.isolatedWithTarget(rt) {
        if (clearColor!=null) clear(clearColor)
        function()
    }
}

private fun Double.mirror(surface: Double) = 2 * surface - this

private fun Vector2.mirror(other: Vector2) = Vector2(x.mirror(other.x), y.mirror(other.y))

private fun Double.toIntervalCount(interval: Double): Int = (this/interval).fastFloor()
