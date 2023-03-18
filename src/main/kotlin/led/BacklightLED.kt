package led

import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.Shape

data class BacklightLED(var shape: Shape, var pos: Vector2, var rotation: Double, var color: ColorRGBa)
