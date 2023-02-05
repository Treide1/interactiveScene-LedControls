package colors

import org.openrndr.color.ColorRGBa

class ColorRepo(vararg colors: ColorRGBa) {

    val colorList = colors.toList()

    var index = 0
        set(value) {
            field = value
            color = colorList[value]
        }
    var color = colorList.first()
        private set

    val pickRandom = { _:Int -> colorList.indices.random() }
    val pickNext = { index:Int -> (index + 1) % colorList.size }
    val pickSame = { index: Int -> index }

    val list = listOf(pickRandom, pickNext, pickSame)
    var pickIndex = 2

    var pickFunc = list[pickIndex]

    fun setNextPickFunc() {
        pickIndex = (pickIndex+1) % list.size
        pickFunc = list[pickIndex]
    }

    fun pick() {
        index = pickFunc(index)
    }


}