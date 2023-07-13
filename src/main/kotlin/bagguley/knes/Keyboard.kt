package bagguley.knes

import java.awt.event.KeyEvent

val keyboard = object {
    fun apply(): Keyboard {
        return Keyboard()
    }
}
class Keyboard {
    val state: IntArray = intArrayOf(0x40,0x40,0x40,0x40,0x40,0x40,0x40,0x40)
    val state2: IntArray = intArrayOf(0x40,0x40,0x40,0x40,0x40,0x40,0x40,0x40)

    val KEY_A = 0
    val KEY_B = 1
    val KEY_SELECT = 2
    val KEY_START = 3
    val KEY_UP = 4
    val KEY_DOWN = 5
    val KEY_LEFT = 6
    val KEY_RIGHT = 7

    fun setKey(keyEvent: KeyEvent, value:Int ) {
        when(keyEvent.keyCode) {
            KeyEvent.VK_X -> state[KEY_A] = value
            KeyEvent.VK_Z -> state[KEY_B] = value
            KeyEvent.VK_CONTROL -> if (keyEvent.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) state[KEY_SELECT] = value
            KeyEvent.VK_ENTER -> state[KEY_START] = value
            KeyEvent.VK_UP -> state[KEY_UP] = value
            KeyEvent.VK_DOWN -> state[KEY_DOWN] = value
            KeyEvent.VK_LEFT -> state[KEY_LEFT] = value
            KeyEvent.VK_RIGHT -> state[KEY_RIGHT] = value

            KeyEvent.VK_NUMPAD7 -> state2[KEY_A] = value
            KeyEvent.VK_NUMPAD9 -> state2[KEY_B] = value
            KeyEvent.VK_NUMPAD3 -> state2[KEY_SELECT] = value
            KeyEvent.VK_NUMPAD1 -> state2[KEY_START] = value
            KeyEvent.VK_NUMPAD8 -> state2[KEY_UP] = value
            KeyEvent.VK_NUMPAD2 -> state2[KEY_DOWN] = value
            KeyEvent.VK_NUMPAD4 -> state2[KEY_LEFT] = value
            KeyEvent.VK_NUMPAD6 -> state2[KEY_RIGHT] = value
        }
    }

    fun keyDown(key: KeyEvent) {
        setKey(key, 0x41)
    }

    fun keyUp(key:KeyEvent ) {
        setKey(key, 0x40)
    }
}