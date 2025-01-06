package inc.flide.vim8.ime.layout.models

import android.content.Context
import android.view.KeyEvent
import arrow.optics.optics
import inc.flide.vim8.lib.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
@optics
data class KeyboardAction(
    val keyboardActionType: KeyboardActionType,
    val text: String,
    val capsLockText: String = "",
    val keyEventCode: Int,
    val keyFlags: Int,
    val layer: LayerLevel
) {
    companion object {
        val UNSPECIFIED = KeyboardAction(
            keyboardActionType = KeyboardActionType.INPUT_KEY,
            text = "",
            keyEventCode = 0,
            keyFlags = 0,
            layer = LayerLevel.FIRST
        )
    }
}

fun KeyboardAction.name(context: Context): String {
    return if (keyboardActionType == KeyboardActionType.INPUT_TEXT) {
        val sb = StringBuilder()
        if (text.isNotEmpty()) sb.append("""Lower case: "$text"""")
        if (text.isNotEmpty() && capsLockText.isNotEmpty()) sb.append("/")
        if (capsLockText.isNotEmpty()) sb.append("""Upper case: "$capsLockText"""")
        sb.toString()
    } else {
        if (keyEventCode < 0) {
            CustomKeycode.fromInt(keyEventCode).name(context)
        } else {
            KeyEvent.keyCodeToString(keyEventCode)
        }
    }
}
