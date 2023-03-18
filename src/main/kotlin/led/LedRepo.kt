package led

class LedRepo (
    val maxDynamicsCount: Int
) {

    val ledMap = mutableMapOf<String, List<BacklightLED>>()

    val dynamicLeds = mutableListOf<BacklightLED>()

    fun addDynamicLED(backlightLED: BacklightLED) {
        dynamicLeds += backlightLED
        while (dynamicLeds.size > maxDynamicsCount) dynamicLeds.removeFirst()
    }

}