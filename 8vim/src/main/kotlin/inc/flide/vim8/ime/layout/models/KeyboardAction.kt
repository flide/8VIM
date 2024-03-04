package inc.flide.vim8.ime.layout.models

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
