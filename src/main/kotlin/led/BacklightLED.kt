package led

import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.Shape

data class BacklightLED(val shape: Shape, val center: Vector2, val color: ColorRGBa)
