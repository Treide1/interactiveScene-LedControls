package led

class LedRepo (
    val maxSize: Int
) {

    val ledList = mutableListOf<BacklightLED>()

    fun addLED(backlightLED: BacklightLED) {
        ledList += backlightLED
        while (ledList.size > maxSize) ledList.removeFirst()
    }

}