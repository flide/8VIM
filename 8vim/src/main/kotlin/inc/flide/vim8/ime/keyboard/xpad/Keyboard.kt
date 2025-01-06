package inc.flide.vim8.ime.keyboard.xpad

import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.raise.nullable
import arrow.core.raise.option
import arrow.core.recover
import inc.flide.vim8.Vim8ImeService
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.ime.layout.models.CHARACTER_SET_SIZE
import inc.flide.vim8.ime.layout.models.Direction.Companion.baseQuadrant
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.ime.layout.models.KeyboardAction
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.MovementSequence
import inc.flide.vim8.ime.layout.models.MovementSequenceType
import inc.flide.vim8.ime.layout.models.characterSets
import inc.flide.vim8.ime.layout.models.findLayer
import inc.flide.vim8.ime.layout.models.toFingerPosition
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val XPAD_CIRCLE_RADIUS_FACTOR = 40f
private const val XPAD_CIRCLE_OFFSET_FACTOR = 26

class Keyboard(private val context: Context) {
    val keys = List(CHARACTER_SET_SIZE) { Key(it, this) }
    var trailColor: Color? = null
    var layerLevel: LayerLevel by mutableStateOf(LayerLevel.FIRST)
    private var lengthOfLineDemarcatingSectors = 0f
    val keyboardData: KeyboardData? get() = Vim8ImeService.keyboardData()
    val circle = Circle()
    val path = Path()
    var bounds: Rect = Rect.Zero
    private val isTabletLandscape: Boolean
        get() =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                context.resources.configuration.screenHeightDp >= 480

    fun reset() {
        layerLevel = LayerLevel.FIRST
    }

    fun findLayer(movementSequence: List<FingerPosition>) {
        if (keyboardData == null) return
        layerLevel = (LayerLevel.VisibleLayers.size downTo LayerLevel.SECOND.toInt())
            .map {
                option {
                    val layerLevel = LayerLevel.fromInt(it).bind()
                    val extraLayerMovementSequence = LayerLevel.MovementSequencesByLayer
                        .getOrNone(layerLevel).bind()
                    layerLevel to extraLayerMovementSequence
                }
            }.firstOrNone {
                it.isNone() || it
                    .filter { (_, extraLayerMovementSequence) ->
                        movementSequence.size >= extraLayerMovementSequence.size
                    }
                    .isSome { (layerLevel, extraLayerMovementSequence) ->
                        val startWith: MovementSequence =
                            movementSequence.subList(0, extraLayerMovementSequence.size)
                        LayerLevel.MovementSequences.contains(startWith) &&
                            layerLevel.toInt() <= keyboardData!!.totalLayers
                    }
            }
            .map { it.map { (layerLevel, _) -> layerLevel }.getOrElse { LayerLevel.FIRST } }
            .getOrElse {
                keyboardData!!
                    .findLayer(movementSequence + FingerPosition.INSIDE_CIRCLE)
            }
    }

    fun key(movementSequence: MovementSequence): Key? = nullable {
        val keyboardData = ensureNotNull(keyboardData)
        val keyboardAction = keyboardData.actionMap.getOrNone(movementSequence).bind()
        val characterSet = keyboardData.characterSets(layerLevel).bind()
        keys.firstOrNone { key ->
            characterSet[key.index] == keyboardAction
        }.bind()
    }

    fun action(
        movementSequence: MovementSequence,
        currentMovementSequenceType: MovementSequenceType
    ): Option<KeyboardAction> = option {
        val keyboardData = ensureNotNull(keyboardData)
        keyboardData.actionMap
            .getOrNone(movementSequence)
            .recover {
                keyboardData.actionMap
                    .getOrNone(listOf(FingerPosition.NO_TOUCH) + movementSequence)
                    .filter { currentMovementSequenceType == MovementSequenceType.NEW_MOVEMENT }
                    .bind()
            }.bind()
    }

    fun hasAction(movementSequence: MovementSequence): Boolean =
        keyboardData?.actionMap?.containsKey(movementSequence) ?: false

    fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        isSidebarOnLeft: Boolean,
        radiusSizeFactor: Int,
        xCentreOffset: Int,
        yCentreOffset: Int,
        characterHeight: Float
    ) {
        val spRadiusValue = radiusSizeFactor.toFloat()
        val radius = (spRadiusValue / XPAD_CIRCLE_RADIUS_FACTOR * keyboardHeight) / 2f
        val offsetX = xCentreOffset * XPAD_CIRCLE_OFFSET_FACTOR
        val offsetY = yCentreOffset * XPAD_CIRCLE_OFFSET_FACTOR

        val smallDim = min(
            keyboardWidth / 2 - offsetX,
            keyboardHeight / 2 - abs(offsetY)
        )
        lengthOfLineDemarcatingSectors = hypot(smallDim, smallDim) - radius - characterHeight

        val x = if (isTabletLandscape) {
            val base = lengthOfLineDemarcatingSectors + offsetX
            if (!isSidebarOnLeft) {
                keyboardWidth - base
            } else {
                base
            }
        } else {
            keyboardWidth / 2f + offsetX
        }

        val circleCenter = Offset(x, y = keyboardHeight / 2f + offsetY)
        circle.centre = circleCenter
        circle.radius = radius

        val letterPositions = List(4 * 2 * 4 * 2) { 0f }.toFloatArray()
        val matrix = Matrix()
        val eastEdge = circleCenter.x + radius + characterHeight / 2f
        for (i in 0 until 4) {
            val dx = i * lengthOfLineDemarcatingSectors / 4f
            letterPositions[4 * i] = eastEdge + dx
            letterPositions[4 * i + 1] = circleCenter.y - characterHeight / 2f
            letterPositions[4 * i + 2] = eastEdge + dx
            letterPositions[4 * i + 3] = circleCenter.y + characterHeight / 2f
        }
        path.rewind()

        path.moveTo(circle.centre.x + circle.radius, circle.centre.y)
        path.relativeLineTo(lengthOfLineDemarcatingSectors, 0f)
        path.moveTo(circle.centre.x - circle.radius, circle.centre.y)
        path.relativeLineTo(-lengthOfLineDemarcatingSectors, 0f)
        path.moveTo(circle.centre.x, circle.centre.y + circle.radius)
        path.relativeLineTo(0f, lengthOfLineDemarcatingSectors)
        path.moveTo(circle.centre.x, circle.centre.y - circle.radius)
        path.relativeLineTo(0f, -lengthOfLineDemarcatingSectors)
        matrix.reset()

        matrix.postRotate(45f, circleCenter.x, circleCenter.y)
        path.asAndroidPath().transform(matrix)
        bounds = path.getBounds()
        matrix.mapPoints(letterPositions, 0, letterPositions, 0, 8)
        matrix.reset()
        matrix.postRotate(90f, circleCenter.x, circleCenter.y)

        for (i in 1 until 4) {
            matrix.mapPoints(
                letterPositions,
                4 * 4 * i,
                letterPositions,
                4 * 4 * (i - 1),
                8
            )
        }
        matrix.reset()
        matrix.postTranslate(-characterHeight / 3f, -characterHeight / 2f)
        matrix.mapPoints(letterPositions)
        keys.withIndex().forEach { (i, key) ->
            key.position = Offset(letterPositions[i * 2], letterPositions[i * 2 + 1])
        }
    }

    data class Circle(var centre: Offset = Offset.Zero, var radius: Float = 0f) {
        private val prefs by appPreferenceModel()
        private var offset: Offset = Offset.Zero
        val virtualCentre: Offset
            get() = centre + offset
        val hasVirtualCentre: Boolean
            get() = prefs.keyboard.circle.dynamic.isEnabled.get() && offset != Offset.Zero

        fun initVirtual(point: Offset) {
            if (!prefs.keyboard.circle.dynamic.isEnabled.get()) return
            val distanceFactor = (sqrt((point - centre).getDistanceSquared()) / radius)
            val radius = this.radius * distanceFactor
            val x = (point.x - centre.x).toDouble()
            val y = (point.y - centre.y).toDouble()
            val angle = (atan2(y, x)).toFloat()
            offset = Offset(x = radius * cos(angle), y = radius * sin(angle))
        }

        fun reset() {
            offset = Offset.Zero
        }

        fun isPointInsideCircle(point: Offset): Boolean {
            val squaredDistanceBetweenPoints = (point - virtualCentre).getDistanceSquared()
            val radiusSquare = radius * radius
            return (squaredDistanceBetweenPoints - radiusSquare) < 0
        }

        fun getSectorOfPoint(point: Offset): FingerPosition {
            val angleDouble = getAngleInRadiansOfPointWithRespectToCentreOfCircle(point)
            val angleToSectorValue = angleDouble / (Math.PI / 2)
            val quadrantCyclic = angleToSectorValue.roundToInt()
            return baseQuadrant(quadrantCyclic).toFingerPosition()
        }

        private fun getAngleInRadiansOfPointWithRespectToCentreOfCircle(point: Offset): Float {
            // Get difference of coordinates
            val x = (point.x - virtualCentre.x).toDouble()
            val y = (virtualCentre.y - point.y).toDouble()

            return atan2(y, x)
                .let { if (it < 0) it + Math.PI * 2 else it }
                .toFloat()
        }
    }
}
