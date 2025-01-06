package inc.flide.vim8.ime.layout.models

import android.content.Context
import arrow.core.getOrElse
import arrow.core.lastOrNone
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import inc.flide.vim8.R
import inc.flide.vim8.lib.android.stringRes

typealias MovementSequence = List<FingerPosition>

class MovementSequenceSerializer : JsonSerializer<MovementSequence>() {
    override fun serialize(
        value: MovementSequence,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        gen.writeFieldName(value.joinToString(","))
    }
}

class MovementSequenceDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): MovementSequence =
        key.split(",").map { FingerPosition.valueOf(it) }
}

enum class FingerPosition {
    NO_TOUCH,
    INSIDE_CIRCLE,
    TOP,
    LEFT,
    BOTTOM,
    RIGHT,
    LONG_PRESS,
    LONG_PRESS_END;

    companion object {

        fun computeMovementSequence(
            layer: LayerLevel,
            quadrant: Quadrant,
            position: CharacterPosition
        ): MovementSequence {
            return when (layer) {
                LayerLevel.HIDDEN -> emptyList()
                else -> {
                    val movementSequencesForDefaultLayer =
                        movementSequencesForLayer(layer, quadrant, position)
                    if (movementSequencesForDefaultLayer.isEmpty()) {
                        emptyList()
                    } else {
                        movementSequencesForDefaultLayer + INSIDE_CIRCLE
                    }
                }
            }
        }

        fun computeQuickMovementSequence(
            layer: LayerLevel,
            quadrant: Quadrant,
            position: CharacterPosition
        ): MovementSequence {
            return when (layer) {
                LayerLevel.HIDDEN -> emptyList()
                else -> {
                    val movementSequenceForExtraLayer =
                        movementSequenceForExtraLayer(layer, quadrant, position)
                    listOf(INSIDE_CIRCLE) + movementSequenceForExtraLayer + listOf(INSIDE_CIRCLE)
                }
            }
        }

        private fun movementSequenceForExtraLayer(
            layer: LayerLevel,
            quadrant: Quadrant,
            position: CharacterPosition
        ): MovementSequence {
            val oppositeQuadrant = quadrant.opposite(position)
            val maxMovements = position.ordinal + 1
            val baseMovementSequence: MovementSequence =
                (0..maxMovements).fold(emptyList()) { acc, _ ->
                    val lastPosition = acc.lastOrNone().getOrElse { INSIDE_CIRCLE }
                    val nextPosition = getNextPosition(quadrant, lastPosition)
                    acc + nextPosition
                }
            return (LayerLevel.SECOND.toInt()..layer.toInt())
                .fold(baseMovementSequence) { acc, _ ->
                    val lastPosition = acc.lastOrNone().getOrElse { INSIDE_CIRCLE }
                    val nextPosition = getNextPosition(oppositeQuadrant, lastPosition)
                    acc + nextPosition
                }
        }

        private fun movementSequencesForLayer(
            layer: LayerLevel,
            quadrant: Quadrant,
            position: CharacterPosition
        ): MovementSequence {
            return when (layer) {
                LayerLevel.FUNCTIONS, LayerLevel.HIDDEN -> emptyList()
                else -> {
                    val maxMovements = position.ordinal + 1
                    val movementSequence =
                        LayerLevel.MovementSequencesByLayer[layer].orEmpty() + INSIDE_CIRCLE
                    (0..maxMovements).fold(movementSequence) { acc, _ ->
                        val lastPosition = acc.last()
                        val nextPosition = getNextPosition(quadrant, lastPosition)
                        acc + nextPosition
                    }
                }
            }
        }

        private fun getNextPosition(
            quadrant: Quadrant,
            lastPosition: FingerPosition
        ): FingerPosition {
            val currentSector: FingerPosition = quadrant.sector.toFingerPosition()
            val oppositeSector: FingerPosition = quadrant.sector.opposite().toFingerPosition()
            val currentPart: FingerPosition = quadrant.part.toFingerPosition()
            val oppositePart: FingerPosition = quadrant.part.opposite().toFingerPosition()
            return if (lastPosition === INSIDE_CIRCLE || lastPosition === oppositePart) {
                currentSector
            } else if (lastPosition === oppositeSector) {
                oppositePart
            } else if (lastPosition === currentPart) {
                oppositeSector
            } else {
                currentPart
            }
        }
    }

    fun name(context: Context): String {
        val stringId = when (this) {
            NO_TOUCH -> R.string.finger_position__no_touch
            INSIDE_CIRCLE -> R.string.finger_position__inside_circle
            TOP -> R.string.finger_position__top
            LEFT -> R.string.finger_position__left
            BOTTOM -> R.string.finger_position__bottom
            RIGHT -> R.string.finger_position__right
            LONG_PRESS -> R.string.finger_position__long_press
            LONG_PRESS_END -> R.string.finger_position__long_press_end
        }
        return context.stringRes(stringId)
    }
}
