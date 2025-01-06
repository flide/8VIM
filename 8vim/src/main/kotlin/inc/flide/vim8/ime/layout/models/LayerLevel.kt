package inc.flide.vim8.ime.layout.models

import android.content.Context
import arrow.core.Option
import arrow.core.firstOrNone
import inc.flide.vim8.R
import inc.flide.vim8.lib.android.stringRes

enum class LayerLevel(val value: Int) {
    FUNCTIONS(-1),
    HIDDEN(0),
    FIRST(1),
    SECOND(2),
    THIRD(3),
    FOURTH(4),
    FIFTH(5),
    SIXTH(6);

    fun toInt(): Int = value

    companion object {
        fun fromInt(int: Int): Option<LayerLevel> = entries.firstOrNone { it.value == int }

        val VisibleLayers = entries.drop(2)

        val MovementSequencesByLayer: Map<LayerLevel, MovementSequence> =
            entries.fold(mapOf()) { acc, layer ->
                val movementSequence = when (layer) {
                    SECOND -> listOf(
                        FingerPosition.BOTTOM,
                        FingerPosition.INSIDE_CIRCLE,
                        FingerPosition.BOTTOM
                    )

                    THIRD -> acc[SECOND].orEmpty() + FingerPosition.LEFT
                    FOURTH -> acc[THIRD].orEmpty() + FingerPosition.TOP
                    FIFTH -> acc[FOURTH].orEmpty() + FingerPosition.RIGHT
                    SIXTH -> acc[FIFTH].orEmpty() + FingerPosition.BOTTOM
                    else -> emptyList()
                }
                acc + (layer to movementSequence)
            }

        val MovementSequences =
            MovementSequencesByLayer
                .filter { (layer, _) -> layer.ordinal > FIRST.ordinal }.values.toSet()
    }

    fun name(context: Context): String {
        val stringId = when (this) {
            FUNCTIONS -> R.string.layer_level__functions
            HIDDEN -> R.string.layer_level__hidden
            FIRST -> R.string.layer_level__first
            SECOND -> R.string.layer_level__second
            THIRD -> R.string.layer_level__third
            FOURTH -> R.string.layer_level__fourth
            FIFTH -> R.string.layer_level__fifth
            SIXTH -> R.string.layer_level__sixth
        }
        return context.stringRes(stringId)
    }
}
