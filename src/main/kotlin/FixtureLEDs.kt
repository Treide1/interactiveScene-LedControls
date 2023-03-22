import bpm.BpmRepo
import colors.ColorRepo
import fx.Darkify
import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.ColorRGBa.Companion.TRANSPARENT
import org.openrndr.color.ColorXSVa
import org.openrndr.draw.*
import org.openrndr.extra.fx.blend.*
import org.openrndr.extra.fx.blur.*
import org.openrndr.extra.noise.fastFloor
import utils.vh
import kotlin.reflect.KProperty

fun main() = application {
    configure {
        width = 640
        height = 480
        display = displays.last()
    }
    program {

        // Init BPM
        val Bpm = BpmRepo(128.0, this)
        val Color = ColorRepo(
            *((0..3)
                .map { index ->
                    index * 90.0
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
            window = 15 // by GuiSlider("blurFx/window/", 16, 0, 30)
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
            blend = 0.1
        }

        // Init blend
        val blend = Overlay()

        // Setup positions and patterns
        val pillarWidth by 2.0 / 3.0 * width
        val pillarCount = 4
        val pillarDist by pillarWidth / (pillarCount - 1)

        fun Drawer.pillar(index: Int) {
            fill = Color.color
            stroke = Color.color
            strokeWeight = 20.0

            val x = (width - pillarWidth)/2.0 + index*pillarDist
            val y0 = vh(0.7)
            val y1 = vh(0.4)
            lineSegment(x, y0, x, y1)
        }

        // Init runtime data structures

        extend {
            // Draw procedure
            val phase = Bpm.phase
            val kickCounter = phase.toIntervalCount(1.0) % pillarCount

            onRenderTarget(rt, clearColor = TRANSPARENT) {
                pillar(kickCounter)
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
        }

        // Assign Input
    }
}

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

private fun Double.toIntervalCount(interval: Double): Int = (this/interval).fastFloor()
