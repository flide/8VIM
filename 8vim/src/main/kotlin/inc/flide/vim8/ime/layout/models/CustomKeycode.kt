package inc.flide.vim8.ime.layout.models

import android.content.Context
import android.view.KeyEvent
import inc.flide.vim8.R
import inc.flide.vim8.lib.android.stringRes

enum class CustomKeycode(@JvmField val keyCode: Int) {
    MOVE_CURRENT_END_POINT_LEFT(-2),
    MOVE_CURRENT_END_POINT_RIGHT(-3),
    MOVE_CURRENT_END_POINT_UP(-4),
    MOVE_CURRENT_END_POINT_DOWN(-5),
    SELECTION_START(-6),
    SELECT_ALL(-7),
    TOGGLE_SELECTION_ANCHOR(-8),
    SHIFT_TOGGLE(-9),
    SWITCH_TO_MAIN_KEYPAD(-10),
    SWITCH_TO_NUMBER_KEYPAD(-11),
    SWITCH_TO_SYMBOLS_KEYPAD(-12),
    SWITCH_TO_SELECTION_KEYPAD(-13),
    SWITCH_TO_EMOTICON_KEYBOARD(-14),
    HIDE_KEYBOARD(-15),
    NO_OPERATION(-16),
    SWITCH_TO_CLIPPAD_KEYBOARD(-17),
    CTRL_TOGGLE(-18),
    FN_TOGGLE(-19);

    fun toKeyEvent(): Int = when (this) {
        MOVE_CURRENT_END_POINT_LEFT, SELECTION_START -> KeyEvent.KEYCODE_DPAD_LEFT
        MOVE_CURRENT_END_POINT_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        MOVE_CURRENT_END_POINT_UP -> KeyEvent.KEYCODE_DPAD_UP
        MOVE_CURRENT_END_POINT_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        else -> 0
    }

    companion object {
        val KEY_CODE_TO_STRING_CODE_MAP: Map<Int, CustomKeycode> =
            entries.associateBy({ it.keyCode }, { it })

        fun fromInt(int: Int) = entries.firstOrNull { it.keyCode == int } ?: NO_OPERATION
    }

    fun name(context: Context): String {
        return when (this) {
            MOVE_CURRENT_END_POINT_LEFT -> context.stringRes(
                R.string.custom_key_code__move_current_end_point_left
            )
            MOVE_CURRENT_END_POINT_RIGHT -> context.stringRes(
                R.string.custom_key_code__move_current_end_point_right
            )
            MOVE_CURRENT_END_POINT_UP -> context.stringRes(
                R.string.custom_key_code__move_current_end_point_up
            )
            MOVE_CURRENT_END_POINT_DOWN -> context.stringRes(
                R.string.custom_key_code__move_current_end_point_down
            )
            SELECTION_START -> context.stringRes(R.string.custom_key_code__selection_start)
            SELECT_ALL -> context.stringRes(R.string.custom_key_code__select_all)
            TOGGLE_SELECTION_ANCHOR -> context.stringRes(
                R.string.custom_key_code__toggle_selection_anchor
            )
            SHIFT_TOGGLE -> context.stringRes(R.string.custom_key_code__shift_toggle)
            SWITCH_TO_MAIN_KEYPAD -> context.stringRes(
                R.string.custom_key_code__switch_to_main_keypad
            )
            SWITCH_TO_NUMBER_KEYPAD -> context.stringRes(
                R.string.custom_key_code__switch_to_number_keypad
            )
            SWITCH_TO_SYMBOLS_KEYPAD -> context.stringRes(
                R.string.custom_key_code__switch_to_symbols_keypad
            )
            SWITCH_TO_SELECTION_KEYPAD -> context.stringRes(
                R.string.custom_key_code__switch_to_selection_keypad
            )
            SWITCH_TO_EMOTICON_KEYBOARD -> context.stringRes(
                R.string.custom_key_code__switch_to_emoticon_keyboard
            )
            HIDE_KEYBOARD -> context.stringRes(R.string.custom_key_code__hide_keyboard)
            NO_OPERATION -> context.stringRes(R.string.custom_key_code__no_operation)
            SWITCH_TO_CLIPPAD_KEYBOARD -> context.stringRes(
                R.string.custom_key_code__switch_to_clippad_keyboard
            )
            CTRL_TOGGLE -> context.stringRes(R.string.custom_key_code__ctrl_toggle)
            FN_TOGGLE -> context.stringRes(R.string.custom_key_code__fn_toggle)
        }
    }
}
