import org.openrndr.application
import org.openrndr.color.ColorRGBa.Companion.BLACK
import org.openrndr.color.ColorRGBa.Companion.PINK
import org.openrndr.draw.isolated
import kotlin.math.abs

// Example application:
// This open an OPENRNDR application window.
// Your mouse controls the circle position.
fun main() = application {
    configure { }
    program {

        // "Interactive block":
        // This contains the draw execution.
        // Values in here are calculated every frame.
        extend {
            val cBackground = BLACK
            val cFill = PINK
            val cStroke = PINK.mix(BLACK, 0.5)

            drawer.isolated {
                clear(cBackground)
                stroke = cStroke
                strokeWeight = 10.0
                fill = cFill

                val pos = mouse.position / drawer.bounds.dimensions // This uses mouse position and data about the viewport
                val rad = abs(1 - (seconds % 2)) * 10 + 20 // This uses attribute 'seconds' provided by application
                circle(width*pos.x, height*pos.y, rad)
            }
        }
    }
}