package inc.flide.vim8.ime.keyboard.xpad

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import arrow.core.None
import arrow.core.some
import inc.flide.vim8.Vim8ImeService
import inc.flide.vim8.arbitraries.Arbitraries
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.MovementSequenceType
import inc.flide.vim8.ime.layout.models.characterSets
import inc.flide.vim8.ime.layout.models.findLayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject

class KeyboardSpec : FunSpec() {
    init {
        lateinit var context: Context
        val keyboardData = mockk<KeyboardData>(relaxed = true)
        val action = Arbitraries.arbKeyboardAction.next()
        val config = Configuration()
        config.screenHeightDp = 480

        beforeSpec {
            mockkObject(Vim8ImeService)
            mockkStatic("inc.flide.vim8.ime.layout.models.KeyboardDataKt")

            context = mockk(relaxed = true) {
                every { resources } returns mockk {
                    every { configuration } returns config
                }
            }
            every { Vim8ImeService.keyboardData() } returns keyboardData
        }

        beforeTest {
            clearMocks(keyboardData)
        }

        context("find layer") {
            every { keyboardData.findLayer(any()) } returns LayerLevel.HIDDEN
            val movementSequences =
                LayerLevel.MovementSequencesByLayer
                    .filter { (layer, _) ->
                        layer == LayerLevel.HIDDEN ||
                            layer.toInt() >= LayerLevel.SECOND.toInt()
                    }
                    .map { (layer, sequence) -> (sequence + FingerPosition.NO_TOUCH) to layer }
            withData(
                nameFn = { "${it.first} -> ${it.second}" },
                movementSequences
            ) { (sequence, layer) ->
                every { keyboardData.totalLayers } returns 6
                val keyboard = Keyboard(context)
                keyboard.findLayer(sequence)
                keyboard.layerLevel shouldBe layer
            }
        }

        context("key") {
            test("No matching movement") {
                val keyboard = Keyboard(context)
                every { keyboardData.actionMap } returns emptyMap()
                keyboard.key(emptyList()) shouldBe null
            }

            test("No matching layer") {
                every { keyboardData.characterSets(any()) } returns None
                every { keyboardData.actionMap } returns mapOf(
                    emptyList<FingerPosition>() to action
                )
                val keyboard = Keyboard(context)
                keyboard.key(emptyList()) shouldBe null
            }
            test("finding the key") {
                every { keyboardData.characterSets(any()) } returns listOf(action).some()
                every { keyboardData.actionMap } returns mapOf(
                    emptyList<FingerPosition>() to action
                )
                val keyboard = Keyboard(context)
                val key = keyboard.key(emptyList())
                key?.index shouldBe 0
            }
        }

        context("action") {
            withData(
                nameFn = { "${it.first}" },
                ((emptyList<FingerPosition>() to MovementSequenceType.NO_MOVEMENT) to None),
                (
                    (listOf(FingerPosition.INSIDE_CIRCLE) to MovementSequenceType.NO_MOVEMENT)
                        to None
                    ),
                (
                    (listOf(FingerPosition.INSIDE_CIRCLE) to MovementSequenceType.NEW_MOVEMENT)
                        to action.some()
                    ),
                (
                    (listOf(FingerPosition.LONG_PRESS) to MovementSequenceType.NO_MOVEMENT)
                        to action.some()
                    )
            ) { (params, result) ->
                every { keyboardData.actionMap } returns mapOf(
                    listOf(
                        FingerPosition.NO_TOUCH,
                        FingerPosition.INSIDE_CIRCLE
                    ) to action,
                    listOf(FingerPosition.LONG_PRESS) to action
                )
                val (movementSequence, currentMovementSequenceType) = params
                val keyboard = Keyboard(context)
                keyboard.action(movementSequence, currentMovementSequenceType) shouldBe result
            }
        }

        context("layout") {
            withData(
                nameFn = { "${it.first} ${it.second}" },
                LayoutParam(isTabletLandscape = false, isSidebarOnLeft = false)
                    to Offset(5f, 10f),
                LayoutParam(
                    isTabletLandscape = true,
                    isSidebarOnLeft = false
                ) to Offset(6.4f, 10f),
                LayoutParam(isTabletLandscape = true, isSidebarOnLeft = true)
                    to Offset(3.6f, 10f)
            ) { (params, result) ->
                val keyboard = Keyboard(context)
                config.orientation =
                    if (params.isTabletLandscape) {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }

                keyboard.layout(
                    10f,
                    20f,
                    params.isSidebarOnLeft,
                    10,
                    0,
                    0,
                    1f
                )
                keyboard.circle.centre.x shouldBe result.x.plusOrMinus(0.1f)
                keyboard.circle.centre.y shouldBe result.y.plusOrMinus(0.1f)
            }
        }

        test("hasAction") {
            every { keyboardData.actionMap } returns mapOf(emptyList<FingerPosition>() to action)
            val keyboard = Keyboard(context)
            keyboard.hasAction(emptyList()) shouldBe true
        }

        afterTest {
            clearMocks(keyboardData)
        }

        afterSpec {
            unmockkObject(Vim8ImeService)
        }
    }

    private data class LayoutParam(val isTabletLandscape: Boolean, val isSidebarOnLeft: Boolean)
}
