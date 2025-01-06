package inc.flide.vim8.ime.keyboard.xpad

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import arrow.core.getOrElse
import arrow.core.getOrNone
import inc.flide.vim8.Vim8ImeService
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.ime.keyboard.text.toKeyboardAction
import inc.flide.vim8.ime.keyboard.xpad.gestures.GlideGesture
import inc.flide.vim8.ime.layout.models.CustomKeycode
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.MovementSequence
import inc.flide.vim8.ime.layout.models.MovementSequenceType
import inc.flide.vim8.ime.theme.ThemeManager
import inc.flide.vim8.ime.theme.blendARGB
import inc.flide.vim8.keyboardManager
import inc.flide.vim8.themeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val FULL_ROTATION_STEPS = 7
internal val ROTATION_MOVEMENT_SEQUENCES = setOf(
    listOf(
        FingerPosition.BOTTOM,
        FingerPosition.LEFT,
        FingerPosition.TOP,
        FingerPosition.RIGHT,
        FingerPosition.BOTTOM,
        FingerPosition.LEFT
    ),
    listOf(
        FingerPosition.BOTTOM,
        FingerPosition.RIGHT,
        FingerPosition.TOP,
        FingerPosition.LEFT,
        FingerPosition.BOTTOM,
        FingerPosition.RIGHT
    ),
    listOf(
        FingerPosition.LEFT,
        FingerPosition.TOP,
        FingerPosition.RIGHT,
        FingerPosition.BOTTOM,
        FingerPosition.LEFT,
        FingerPosition.TOP
    ),
    listOf(
        FingerPosition.LEFT,
        FingerPosition.BOTTOM,
        FingerPosition.RIGHT,
        FingerPosition.TOP,
        FingerPosition.LEFT,
        FingerPosition.BOTTOM
    ),
    listOf(
        FingerPosition.TOP,
        FingerPosition.LEFT,
        FingerPosition.BOTTOM,
        FingerPosition.RIGHT,
        FingerPosition.TOP,
        FingerPosition.LEFT
    ),
    listOf(
        FingerPosition.TOP,
        FingerPosition.RIGHT,
        FingerPosition.BOTTOM,
        FingerPosition.LEFT,
        FingerPosition.TOP,
        FingerPosition.RIGHT
    ),
    listOf(
        FingerPosition.RIGHT,
        FingerPosition.TOP,
        FingerPosition.LEFT,
        FingerPosition.BOTTOM,
        FingerPosition.RIGHT,
        FingerPosition.TOP
    ),
    listOf(
        FingerPosition.RIGHT,
        FingerPosition.BOTTOM,
        FingerPosition.LEFT,
        FingerPosition.TOP,
        FingerPosition.RIGHT,
        FingerPosition.BOTTOM
    )
)

class KeyboardController(context: Context) : GlideGesture.Listener {
    private val prefs by appPreferenceModel()
    private val keyboardManager by context.keyboardManager()
    private val themeManager by context.themeManager()

    private val drawStyle = Stroke(context.resources.displayMetrics.density * 2)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var currentMovementSequenceType = MovementSequenceType.NO_MOVEMENT
    private var currentFingerPosition: FingerPosition = FingerPosition.NO_TOUCH
    private var currentKey: Key? = null
    private val movementSequence: MutableList<FingerPosition> = arrayListOf()
    private val trailColor: Color
        get() = (
            themeManager.currentTheme.value?.trailColor
                ?: ThemeManager.RandomTrailColor()
            ).color()
    private val backgroundColor: Color
        get() = themeManager
            .currentTheme
            .value?.scheme?.surface ?: Color.White

    private val inputEventDispatcher get() = keyboardManager.inputEventDispatcher
    private val activeState get() = keyboardManager.activeState
    private val glideGesture = GlideGesture.Detector(this)
    private val showTrail: Boolean get() = prefs.keyboard.trail.isVisible.get()
    private val isDynamicCircleOverlayEnabled: Boolean
        get() = prefs.keyboard.circle.dynamic.isOverlayEnabled.get()
    lateinit var keyboard: Keyboard

    var isReducesCircleSize by mutableStateOf(false)
        private set
    var hasTrail by mutableStateOf(false)
    var hasVirtualCentre by mutableStateOf(false)
        private set
    val trailPoints = mutableStateListOf<GlideGesture.Point>()

    init {
        prefs.keyboard.circle.dynamic.isEnabled.observe { _ ->
            hasVirtualCentre = keyboard.circle.hasVirtualCentre && isDynamicCircleOverlayEnabled
        }
        prefs.keyboard.circle.dynamic.isOverlayEnabled.observe { _ ->
            hasVirtualCentre = keyboard.circle.hasVirtualCentre && isDynamicCircleOverlayEnabled
        }
    }

    fun drawSectors(drawScope: DrawScope, hasVirtualCentre: Boolean, color: Color) {
        drawScope.drawCircle(
            color,
            keyboard.circle.radius,
            keyboard.circle.centre,
            style = drawStyle
        )

        drawScope.drawPath(keyboard.path, color, style = drawStyle)

        if (hasVirtualCentre) {
            drawScope.drawCircle(
                color.blendARGB(backgroundColor, 0.65f).copy(alpha = 0.75f),
                keyboard.circle.radius,
                keyboard.circle.virtualCentre,
                style = Fill
            )
        }
    }

    fun drawTrail(drawScope: DrawScope, trailPoints: List<GlideGesture.Point>) {
        for (point in trailPoints) {
            drawScope.drawCircle(keyboard.trailColor!!, point.radius, point.center)
        }
    }

    fun onTouchEventInternal(event: MotionEvent) {
        val position = Offset(event.x, event.y)
        val currentPosition =
            if (keyboard.circle.isPointInsideCircle(position)) {
                FingerPosition.INSIDE_CIRCLE
            } else {
                keyboard.circle.getSectorOfPoint(position)
            }

        hasTrail = if (showTrail) {
            glideGesture.onTouchEvent(event)
        } else {
            false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                keyboard.trailColor = trailColor
                resetKey()
                movementSequence.clear()
                currentMovementSequenceType = MovementSequenceType.NEW_MOVEMENT
                keyboard.reset()
                movementSequence.add(currentPosition)
                currentFingerPosition = currentPosition
                if (currentPosition == FingerPosition.INSIDE_CIRCLE) {
                    keyboard.circle.initVirtual(position)
                }
                initiateLongPressDetection()
                isReducesCircleSize = true
            }

            MotionEvent.ACTION_MOVE -> {
                val lastKnownFingerPosition = currentFingerPosition
                currentFingerPosition = currentPosition
                val isFingerPositionChanged = lastKnownFingerPosition !== currentFingerPosition
                if (isFingerPositionChanged) {
                    interruptLongPress()
                    movementSequence.add(currentFingerPosition)
                    keyboard.findLayer(movementSequence)

                    if (isFullRotation) {
                        var start = 2
                        var size = FULL_ROTATION_STEPS - 1
                        if (keyboard.layerLevel !== LayerLevel.FIRST) {
                            LayerLevel.MovementSequencesByLayer.getOrNone(
                                keyboard.layerLevel
                            ).onSome {
                                start += it.size
                                size += it.size
                            }
                        }
                        movementSequence.subList(start, size).clear()
                        inputEventDispatcher.sendDownUp(
                            CustomKeycode.SHIFT_TOGGLE.toKeyboardAction()
                        )
                    }

                    if (currentFingerPosition == FingerPosition.INSIDE_CIRCLE &&
                        keyboard.hasAction(movementSequence)
                    ) {
                        processKeyPress(movementSequence)
                        resetKey()
                        movementSequence.clear()
                        keyboard.layerLevel = LayerLevel.FIRST
                        currentMovementSequenceType = MovementSequenceType.CONTINUED_MOVEMENT
                        movementSequence.add(currentFingerPosition)
                    } else {
                        Vim8ImeService.inputFeedbackController()?.sectorCross()
                        if (currentFingerPosition == FingerPosition.INSIDE_CIRCLE) {
                            processLayerMovements()
                        } else {
                            detectKeySelection()
                        }
                    }
                } else {
                    initiateLongPressDetection()
                }
            }

            MotionEvent.ACTION_UP -> {
                interruptLongPress()
                resetKey()
                isReducesCircleSize = false
                keyboard.circle.reset()
                currentFingerPosition = FingerPosition.NO_TOUCH
                movementSequence.add(currentFingerPosition)
                processKeyPress(movementSequence)

                movementSequence.clear()
                currentMovementSequenceType = MovementSequenceType.NO_MOVEMENT
                keyboard.reset()
            }

            MotionEvent.ACTION_CANCEL -> {
                job?.cancel()
                job = null
                resetKey()
                isReducesCircleSize = false
                keyboard.circle.reset()
                currentMovementSequenceType = MovementSequenceType.NO_MOVEMENT
                keyboard.reset()
            }
        }
        hasVirtualCentre = keyboard.circle.hasVirtualCentre && isDynamicCircleOverlayEnabled
    }

    private fun detectKeySelection() {
        resetKey()
        val modifiedMovementSequence =
            movementSequence + FingerPosition.INSIDE_CIRCLE
        keyboard.key(modifiedMovementSequence)?.let {
            it.isSelected = true
            currentKey = it
        }
    }

    private fun processLayerMovements() {
        var layerSize = 0
        val extraLayerMovementSequences =
            if (keyboard.layerLevel !== LayerLevel.FIRST) {
                LayerLevel.MovementSequencesByLayer.getOrNone(keyboard.layerLevel)
                    .onSome {
                        layerSize = it.size + 1
                    }.getOrElse { listOf() }
            } else {
                listOf()
            }

        val extraLayerCondition =
            keyboard.layerLevel !== LayerLevel.FIRST &&
                movementSequence.size >= layerSize
        if (extraLayerCondition) {
            movementSequence.clear()
            resetKey()
            currentMovementSequenceType = MovementSequenceType.NEW_MOVEMENT
            movementSequence.addAll(extraLayerMovementSequences)
            movementSequence.add(currentFingerPosition)
            if (!isReducesCircleSize) isReducesCircleSize = true
        }
    }

    private fun initiateLongPressDetection() {
        if (job != null) return

        job = scope.launch {
            delay(500L)
            while (isActive) {
                processLongPressStart(movementSequence + FingerPosition.LONG_PRESS)
                delay(50L)
            }
        }
    }

    private fun processKeyPress(movementSequence: MovementSequence) {
        processMovementSequence(movementSequence)
            .onSome {
                inputEventDispatcher.sendDownUp(it)
            }
    }

    private fun processLongPressStart(movementSequence: MovementSequence) {
        processMovementSequence(movementSequence)
            .onSome {
                inputEventDispatcher.sendDownUp(it, true)
            }
    }

    private fun processLongPressEnd(movementSequence: MovementSequence) {
        processMovementSequence(movementSequence)
            .onSome {
                inputEventDispatcher.sendDownUp(it)
            }
    }

    private fun processMovementSequence(movementSequence: MovementSequence) = keyboard
        .action(movementSequence, currentMovementSequenceType)
        .filter {
            it.layer != LayerLevel.FUNCTIONS || activeState.isFnOn
        }

    private fun interruptLongPress() = runBlocking {
        if (job == null) return@runBlocking
        job?.cancel()
        job = null
        processLongPressEnd(movementSequence + FingerPosition.LONG_PRESS_END)
    }

    private fun resetKey() {
        currentKey?.isSelected = false
        currentKey = null
    }

    private val isFullRotation: Boolean
        get() {
            var size = FULL_ROTATION_STEPS
            var start = 1
            var layerCondition = movementSequence[0] == FingerPosition.INSIDE_CIRCLE
            if (keyboard.layerLevel !== LayerLevel.FIRST) {
                LayerLevel.MovementSequencesByLayer.getOrNone(keyboard.layerLevel).onSome {
                    size += it.size
                    start += it.size
                    layerCondition = LayerLevel.MovementSequences.contains(
                        movementSequence.subList(0, it.size)
                    )
                }
            }
            return if (movementSequence.size == size && layerCondition) {
                ROTATION_MOVEMENT_SEQUENCES.contains(
                    movementSequence.subList(start, size)
                )
            } else {
                false
            }
        }

    override fun onTrailAddPoints(points: List<GlideGesture.Point>) {
        trailPoints.clear()
        trailPoints.addAll(points)
    }

    override fun onTrailEnd() {
        trailPoints.clear()
        hasTrail = false
    }
}
