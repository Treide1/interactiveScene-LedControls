package fx

import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode
import org.openrndr.resourceText

class Darkify : Filter(filterShaderFromCode(resourceText("/darkify.glsl"), "darkify")) {
    var darkFac by parameters

    init {
        darkFac = 0.2
    }
}