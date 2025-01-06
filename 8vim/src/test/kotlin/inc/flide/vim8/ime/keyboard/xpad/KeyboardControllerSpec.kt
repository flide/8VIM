package inc.flide.vim8.ime.keyboard.xpad

import android.content.Context
import android.util.DisplayMetrics
import android.view.MotionEvent
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.util.unpackFloat2
import arrow.core.None
import arrow.core.some
import inc.flide.vim8.AppPrefs
import inc.flide.vim8.Vim8ImeService
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.arbitraries.Arbitraries.arbKeyboardAction
import inc.flide.vim8.arbitraries.Arbitraries.arbPoint
import inc.flide.vim8.datastore.CachedPreferenceModel
import inc.flide.vim8.datastore.model.PreferenceData
import inc.flide.vim8.ime.input.InputEventDispatcher
import inc.flide.vim8.ime.input.InputFeedbackController
import inc.flide.vim8.ime.keyboard.text.toKeyboardAction
import inc.flide.vim8.ime.keyboard.xpad.gestures.GlideGesture
import inc.flide.vim8.ime.layout.models.CustomKeycode
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.ime.layout.models.KeyboardAction
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.LayerLevel.Companion.MovementSequencesByLayer
import inc.flide.vim8.ime.layout.models.LayerLevel.Companion.VisibleLayers
import inc.flide.vim8.ime.theme.ThemeManager
import inc.flide.vim8.ime.theme.blendARGB
import inc.flide.vim8.keyboardManager
import inc.flide.vim8.themeManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.mockk.OfTypeMatcher
import io.mockk.clearConstructorMockk
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import kotlinx.coroutines.delay

class KeyboardControllerSpec : FunSpec({
    lateinit var context: Context
    lateinit var event: MotionEvent
    lateinit var xpadKeyboard: Keyboard
    lateinit var eventDispatcher: InputEventDispatcher
    lateinit var keyboardCircle: Keyboard.Circle
    lateinit var isDynamicCircleOverlayEnabled: PreferenceData<Boolean>
    lateinit var showTrail: PreferenceData<Boolean>
    val colorScheme = lightColorScheme()

    beforeSpec {
        mockkConstructor(GlideGesture.Detector::class)
        mockkStatic(::appPreferenceModel)
        mockkStatic(Context::keyboardManager)
        mockkStatic(Context::themeManager)
        mockkObject(Vim8ImeService)

        context = mockk<Context> {
            every { resources } returns mockk {
                val dm = DisplayMetrics()
                dm.density = 1f
                every { displayMetrics } returns dm
            }
            every { keyboardManager() } returns lazy {
                mockk {
                    every { inputEventDispatcher } answers { eventDispatcher }
                    every { activeState } returns mockk {
                        every { isFnOn } returns true
                    }
                }
            }
            every { themeManager() } returns lazy {
                mockk {
                    every { currentTheme } returns mockk {
                        every { value } returns ThemeManager.ThemeInfo(
                            colorScheme,
                            ThemeManager.FixedTrailColor(Color.Black)
                        )
                    }
                }
            }
        }

        val inputFeedbackController = mockk<InputFeedbackController>(relaxed = true)
        every { Vim8ImeService.inputFeedbackController() } returns inputFeedbackController

        every { appPreferenceModel() } returns CachedPreferenceModel(
            mockk<AppPrefs> {
                every { keyboard } returns mockk {
                    every { trail } returns mockk {
                        every { isVisible } answers { showTrail }
                    }
                    every { circle } returns mockk {
                        every { dynamic } returns mockk {
                            every { isEnabled } returns mockk(relaxed = true)
                            every { isOverlayEnabled } answers { isDynamicCircleOverlayEnabled }
                        }
                    }
                }
            }
        )
    }

    beforeTest {
        event = mockk {
            every { x } returns 0f
            every { y } returns 0f
        }

        xpadKeyboard = mockk(relaxed = true) {
            every { circle } answers { keyboardCircle }
            every { trailColor } returns Color.Black
            every { hasAction(any()) } returns false
            every { action(any(), any()) } returns None
            every { key(any()) } returns null
        }

        isDynamicCircleOverlayEnabled = mockk<PreferenceData<Boolean>>(relaxed = true)

        showTrail = mockk<PreferenceData<Boolean>> {
            every { get() } returns false
        }

        keyboardCircle = mockk<Keyboard.Circle>(relaxed = true) {
            every { isPointInsideCircle(any()) } answers {
                unpackFloat2(firstArg<Long>()).toInt() == FingerPosition.INSIDE_CIRCLE.ordinal
            }

            every { getSectorOfPoint(any()) } answers {
                FingerPosition.entries[unpackFloat2(firstArg<Long>()).toInt()]
            }
        }

        eventDispatcher = mockk<InputEventDispatcher>(relaxed = true)
    }

    context("drawSectors") {
        withData(nameFn = { "Has dynamic circle $it" }, listOf(false, true)) { hasDynamicCircle ->
            withData(nameFn = { "Has overlay $it" }, listOf(false, true)) { hasOverlay ->
                val drawScope = mockk<DrawScope>(relaxed = true)
                every { keyboardCircle.hasVirtualCentre } returns hasDynamicCircle
                every { isDynamicCircleOverlayEnabled.get() } returns hasOverlay
                val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }
                controller.drawSectors(drawScope, hasDynamicCircle && hasOverlay, Color.Black)
                verifySequence {
                    drawScope.drawCircle(
                        Color.Black,
                        any<Float>(),
                        any<Offset>(),
                        style = any<DrawStyle>()
                    )
                    drawScope.drawPath(any<Path>(), Color.Black, style = any<DrawStyle>())
                    if (hasDynamicCircle && hasOverlay) {
                        drawScope.drawCircle(
                            Color.Black.blendARGB(colorScheme.background, 0.65f)
                                .copy(alpha = 0.75f),
                            any<Float>(),
                            any<Offset>(),
                            style = any<DrawStyle>()
                        )
                    }
                }
            }
        }
    }

    test("drawTrail") {
        val points = Arb.list(arbPoint, 1..10).next()
        val drawScope = mockk<DrawScope>(relaxed = true)
        val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }
        controller.drawTrail(drawScope, points)
        verifySequence {
            for (point in points) {
                drawScope.drawCircle(
                    Color.Black,
                    point.radius,
                    point.center
                )
            }
        }
    }

    context("onTouchEventInternal has trail") {
        withData(nameFn = { "showTrail $it" }, listOf(false, true)) { hasShowTrail ->
            withData(nameFn = { "glide detector $it" }, listOf(false, true)) { glide ->
                every { showTrail.get() } returns hasShowTrail
                every {
                    constructedWith<GlideGesture.Detector>(
                        OfTypeMatcher<GlideGesture.Listener>(
                            GlideGesture.Listener::class
                        )
                    ).onTouchEvent(any())
                } returns glide
                every { event.actionMasked } returns MotionEvent.ACTION_DOWN
                val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }
                controller.onTouchEventInternal(event)
                controller.hasTrail shouldBe (hasShowTrail && glide)
            }
        }
    }

    context("onTouchEventInternal") {
        test("keyPress") {
            val movements = listOf(
                listOf(
                    FingerPosition.INSIDE_CIRCLE,
                    FingerPosition.BOTTOM,
                    FingerPosition.LEFT,
                    FingerPosition.INSIDE_CIRCLE
                ) to arbKeyboardAction.next(),
                listOf(
                    FingerPosition.INSIDE_CIRCLE,
                    FingerPosition.BOTTOM,
                    FingerPosition.LEFT,
                    FingerPosition.TOP,
                    FingerPosition.INSIDE_CIRCLE
                ) to arbKeyboardAction.next()
            )

            val key = mockk<Key>(relaxed = true)

            for ((movement, action) in movements) {
                every { xpadKeyboard.hasAction(movement) } returns true
                every { xpadKeyboard.action(movement, any()) } returns action.some()
                every { xpadKeyboard.key(movement) } returns key
            }

            every { event.actionMasked } answers { MotionEvent.ACTION_DOWN } andThenAnswer {
                MotionEvent.ACTION_MOVE
            }

            val sequence = movements
                .fold(emptyList<FingerPosition>()) { acc, current ->
                    if (acc.isEmpty()) {
                        current.first
                    } else {
                        if (acc.last() == FingerPosition.INSIDE_CIRCLE &&
                            current.first.first() == FingerPosition.INSIDE_CIRCLE
                        ) {
                            acc + current.first.subList(1, current.first.size)
                        } else {
                            acc + current.first
                        }
                    }
                }

            every { event.y } returnsMany sequence.map {
                it.ordinal.toFloat()
            }

            val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }

            sequence.forEach { _ -> controller.onTouchEventInternal(event) }

            verifyOrder {
                for ((_, action) in movements) {
                    key setProperty "isSelected" value true
                    eventDispatcher.sendDownUp(action, false)
                }
            }
        }

        test("long press") {
            val movements = listOf(
                listOf(
                    FingerPosition.INSIDE_CIRCLE,
                    FingerPosition.LONG_PRESS
                ) to arbKeyboardAction.next(),
                listOf(
                    FingerPosition.INSIDE_CIRCLE,
                    FingerPosition.LONG_PRESS_END
                ) to arbKeyboardAction.next()
            )

            for ((movement, action) in movements) {
                every { xpadKeyboard.action(movement, any()) } returns action.some()
            }

            every { event.actionMasked } answers { MotionEvent.ACTION_DOWN } andThenAnswer {
                MotionEvent.ACTION_MOVE
            }

            val sequence = listOf(FingerPosition.INSIDE_CIRCLE, FingerPosition.BOTTOM)

            every { event.y } returnsMany sequence.map { it.ordinal.toFloat() }

            val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }

            controller.onTouchEventInternal(event)
            delay(500)
            controller.onTouchEventInternal(event)

            verifyOrder {
                for ((m, action) in movements) {
                    eventDispatcher
                        .sendDownUp(action, m.last() == FingerPosition.LONG_PRESS)
                }
            }
        }

        test("key up") {
            val action = mockk<KeyboardAction>(relaxed = true)
            every {
                xpadKeyboard.action(
                    listOf(
                        FingerPosition.INSIDE_CIRCLE,
                        FingerPosition.NO_TOUCH
                    ),
                    any()
                )
            } returns action.some()
            every { event.actionMasked } answers { MotionEvent.ACTION_UP }

            every { event.y } returns FingerPosition.INSIDE_CIRCLE.ordinal.toFloat()

            val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }

            controller.onTouchEventInternal(event)
            eventDispatcher.sendDownUp(action, false)
        }

        test("key cancel") {
            every { event.actionMasked } answers { MotionEvent.ACTION_CANCEL }

            every { event.y } returns FingerPosition.INSIDE_CIRCLE.ordinal.toFloat()

            val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }

            controller.onTouchEventInternal(event)
            verify { xpadKeyboard.reset() }
        }
    }

    context("onTouchEventInternal full rotation") {
        withData(nameFn = { "Layer: $it" }, VisibleLayers) { layer ->
            withData(
                nameFn = { "Sequence :$it" },
                ROTATION_MOVEMENT_SEQUENCES
            ) { rotationSequence ->
                val layerMovement =
                    MovementSequencesByLayer.getOrDefault(layer, emptyList())

                val layerLevels = listOf(
                    LayerLevel.FIRST,
                    LayerLevel.FIRST,
                    LayerLevel.FIRST,
                    LayerLevel.FIRST
                ) + layerMovement.indices
                    .drop(1)
                    .map { if (it == layerMovement.size - 1) layer else LayerLevel.FIRST }

                every { xpadKeyboard.layerLevel } returnsMany layerLevels

                every { event.actionMasked } answers { MotionEvent.ACTION_DOWN } andThenAnswer {
                    MotionEvent.ACTION_MOVE
                }

                val sequence =
                    layerMovement + listOf(FingerPosition.INSIDE_CIRCLE) + rotationSequence

                every { event.y } returnsMany sequence.map {
                    it.ordinal.toFloat()
                }

                val controller = KeyboardController(context).also { it.keyboard = xpadKeyboard }

                sequence.forEach { _ -> controller.onTouchEventInternal(event) }
                verify {
                    eventDispatcher.sendDownUp(CustomKeycode.SHIFT_TOGGLE.toKeyboardAction())
                }
            }
        }
    }

    afterSpec {
        clearStaticMockk(Context::class)
        clearConstructorMockk(GlideGesture.Detector::class)
    }
})
