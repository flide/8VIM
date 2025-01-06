package inc.flide.vim8.app.settings.layout

import android.graphics.Matrix
import android.util.Log
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import arrow.core.None
import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.some
import inc.flide.vim8.R
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.lib.compose.ActionMapDatabase
import inc.flide.vim8.lib.compose.LocalKeyboardDatabaseController
import inc.flide.vim8.lib.compose.Screen
import inc.flide.vim8.lib.compose.stringRes
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private interface State
private data class Start(val offset: Offset) : State
private data class Move(val offset: Offset) : State
private object Repeat : State
private object Lift : State
private object Done : State

@Composable
fun UsageScreen(item: ActionMapDatabase.Item?) = with(LocalDensity.current) {
    Screen {
        val context = LocalContext.current
        val keyboardDatabaseController = LocalKeyboardDatabaseController.current

        previewFieldVisible = true

        title = item?.let {
            stringRes(
                R.string.settings__layouts__usage__title,
                "action" to keyboardDatabaseController.action(it)
            )
        } ?: error("Nope")
        previewFieldVisible = true

        content {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                val radius = (12f / 40f * constraints.maxWidth) / 2f
                val points = Path().fingerPath(item, radius, constraints.maxWidth)

                val pts = mutableListOf<Offset>()
                var targetIndexValue by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    targetIndexValue = (points.size - 1).coerceAtLeast(0)
                }
                val infiniteTransition =
                    rememberInfiniteTransition(label = "Path infinite transition")

                // Animating infinitely a float between 0f and the path length
//                val progress by infiniteTransition.animateFloat(
//                    initialValue = 0f,
//                    targetValue = pathMeasure.length,
//                    animationSpec = infiniteRepeatable(
//                        animation = tween(5000, easing = LinearEasing),
//                        repeatMode = RepeatMode.Restart
//                    ), label = "Path animation"
//                )

                val progress by animateIntAsState(
                    targetValue = targetIndexValue,
                    animationSpec = tween(5000, easing = EaseInOutSine),
//                    animationSpec = infiniteRepeatable(
//                        animation = tween(5000, easing = EaseInOutSine),
//                        repeatMode = RepeatMode.Restart
//                    ),
                    label = "Path animation"
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(constraints.maxWidth.toDp())
                ) {
                    drawLayout(density, radius)
                    val state = points.getOrNull(progress) ?: return@Canvas
                    when (state) {
                        is Start -> pts.add(state.offset)
                        is Move -> pts.add(state.offset)
                        is Lift -> pts.removeAt(0)
                        else -> {}
                    }
                    if (pts.size > 150) pts.removeAt(0)
                    for (i in pts.size - 1 downTo 0) {
                        val centre = pts[i]
                        val radius = 14f * (1 - (pts.size - i).toFloat() / 150f)

                        drawCircle(Color.Red, radius, centre, style = Fill)
                    }
                }
            }
        }
    }
}

private fun FingerPosition.toOffset(radius: Float): Option<Offset> = when (this) {
    FingerPosition.INSIDE_CIRCLE -> Offset.Zero.some()
    FingerPosition.TOP -> Offset(0f, -2 * radius).some()
    FingerPosition.LEFT -> Offset(-2 * radius, 0f).some()
    FingerPosition.BOTTOM -> Offset(0f, 2 * radius).some()
    FingerPosition.RIGHT -> Offset(2 * radius, 0f).some()
    else -> None
}

private fun Path.fingerPath(
    item: ActionMapDatabase.Item,
    radius: Float,
    maxWidth: Int
): List<State> {
    val pathMeasure = PathMeasure()
    val matrix = androidx.compose.ui.graphics.Matrix()
    val points = mutableListOf<State>()
    val offsets = item.movementSequence
        .flatMap { it.toOffset(radius).toList() }
    offsets.firstOrNone().onSome {
        moveTo(it.x, it.y)
    }
    offsets
        .withIndex()
        .drop(1)
        .forEach { (i, current) ->
            val prev = offsets[i - 1]
            Log.d("layout draw", "$prev -> $current")

            if ((prev.x == current.x && current.x == 0f) ||
                (prev.y == current.y && current.y == 0f)
            ) {
                lineTo(current.x, current.y)
            } else {
                val x = ((current.x - prev.x) / 2f).toDouble()
                val y = ((current.y - prev.y) / 2f).toDouble()
                val angle = atan2(y, x).toFloat()
                val (factorX, factorY) = when {
                    x < 0 && y < 0 -> 2 to -2
                    x > 0 && y > 0 -> 2 to -2
                    else -> -2 to 2
                }

                quadraticBezierTo(
                    factorX * radius * cos(angle),
                    factorY * radius * sin(angle),
                    current.x,
                    current.y
                )
            }
        }

    matrix.translate(maxWidth / 2f, maxWidth / 2f)
    transform(matrix)
    pathMeasure.setPath(this, false)
    if (pathMeasure.length > 0) {
        points.add(Start(pathMeasure.getPosition(0f)))
        var length = 5f
        while (length < pathMeasure.length) {
            points.add(Move(pathMeasure.getPosition(length)))
            length += 5f
        }
    }
    if (item.movementSequence.last() == FingerPosition.NO_TOUCH) {
        val trail = points.size.coerceAtMost(150)
        for (i in 1..trail) {
            points.add(Lift)
        }
    }
    return points
}

private fun DrawScope.drawLayout(density: Float, radius: Float) {
    val path = Path()
    val matrix = Matrix()
    val length = hypot(center.x, center.x) - radius
    path.moveTo(center.x + radius, center.y)
    path.relativeLineTo(length, 0f)
    path.moveTo(center.x - radius, center.y)
    path.relativeLineTo(-length, 0f)
    path.moveTo(center.x, center.y + radius)
    path.relativeLineTo(0f, length)
    path.moveTo(center.x, center.y - radius)
    path.relativeLineTo(0f, -length)
    matrix.reset()
    matrix.postRotate(45f, center.x, center.y)
    path.asAndroidPath().transform(matrix)
//                    matrix.reset()
//                    matrix.postRotate(90f, center.x, center.y)
    drawCircle(
        Color.Black,
        radius,
        style = Stroke(density * 2)
    )
    drawPath(path, Color.Black, style = Stroke(density * 2))
}
