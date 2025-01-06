package inc.flide.vim8.lib.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import arrow.core.Option
import arrow.core.fold
import arrow.core.getOrNone
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.datastore.model.observeAsState
import inc.flide.vim8.ime.layout.loadKeyboardData
import inc.flide.vim8.ime.layout.models.KeyboardAction
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.MovementSequence
import inc.flide.vim8.ime.layout.models.name
import inc.flide.vim8.layoutLoader

val LocalKeyboardDatabaseController =
    staticCompositionLocalOf<KeyboardDatabaseController> {
        error("LocalKeyboardDatabaseController not initialized")
    }

class ActionMapDatabase internal constructor(actionMap: Map<MovementSequence, KeyboardAction>) {
    companion object {
        fun empty(): ActionMapDatabase = ActionMapDatabase(emptyMap())
    }

    private val layerMapping: Map<LayerLevel, List<Item>>
    private val items: Map<String, Item>

    init {
        val extraMovementSet = mutableSetOf<MovementSequence>()
        items = actionMap.toList().withIndex()
            .fold(emptyMap()) { acc, (index, value) ->
                val (movement, action) = value
                val extraMovement = LayerLevel.MovementSequencesByLayer[action.layer]!!
                val subMovement =
                    if (LayerLevel.MovementSequences.contains(
                            movement.subList(
                                0,
                                extraMovement.size
                            )
                        )
                    ) {
                        movement.subList(extraMovement.size, movement.size)
                    } else {
                        movement
                    }
                val toAdd = if (extraMovementSet.contains(subMovement)) {
                    emptyMap()
                } else {
                    extraMovementSet.add(subMovement)
                    mapOf(index.toString() to Item(index.toString(), subMovement, action))
                }
                acc + toAdd
            }
        layerMapping = items.fold(emptyMap()) { acc, (_, item) ->
            val current = acc.getOrElse(item.keyboardAction.layer) { emptyList() }
            acc + (item.keyboardAction.layer to (current + item))
        }
    }

    fun byLayer(filter: Option<LayerLevel>): List<Item> =
        layerMapping.fold(emptyList()) { acc, (layer, item) ->
            if (filter.isNone() || filter.isSome { it == layer }) {
                acc + item
            } else {
                acc
            }
        }

    fun byId(id: String): Item? = items[id]

    data class Item(
        val index: String,
        val movementSequence: MovementSequence,
        val keyboardAction: KeyboardAction
    )
}

class KeyboardDatabaseController(private val context: Context) {
    var isEmbedded by mutableStateOf(false)
        private set
    private var keyboardData by mutableStateOf<KeyboardData?>(null)
    private var database = ActionMapDatabase.empty()

    private val optionsMapping = mutableMapOf<String, LayerLevel>()
    private var layersOptions = emptyList<String>()

    internal fun keyboardData(keyboardData: KeyboardData?, isEmbedded: Boolean) {
        if (keyboardData == null) return
        this.keyboardData = keyboardData
        this.isEmbedded = isEmbedded
        database = ActionMapDatabase(keyboardData.actionMap)
        optionsMapping.clear()
        layersOptions = (0 until keyboardData.totalLayers).fold(listOf("All layers")) { acc, i ->
            val layer = LayerLevel.entries[i]
            val text = layer.name(context)
            optionsMapping[text] = layer
            acc + text
        }
    }

    fun layoutName() = keyboardData.toString()
    fun layers() = layersOptions
    fun layer(i: Int) = layersOptions.getOrNull(i).orEmpty()
    fun layer(s: String) = optionsMapping.getOrNone(s)
    fun layer(layerLevel: LayerLevel) = layerLevel.name(context)
    fun layer(item: ActionMapDatabase.Item) = layer(item.keyboardAction.layer)

    fun byLayer(filter: Option<LayerLevel>) = database.byLayer(filter)
    fun byId(id: String) = database.byId(id)

    fun action(item: ActionMapDatabase.Item) = item.keyboardAction.name(context)
    fun movementSequence(item: ActionMapDatabase.Item) = item.movementSequence.name(context)
    fun info() = keyboardData?.info
}

@Composable
fun rememberKeyboardDatabaseController(): KeyboardDatabaseController {
    val context = LocalContext.current
    val prefs by appPreferenceModel()
    val layoutLoader by context.layoutLoader()
    var keyboardData by remember { mutableStateOf<KeyboardData?>(null) }
    val currentLayout by prefs.layout.current.observeAsState()
    keyboardData = currentLayout.loadKeyboardData(layoutLoader, context).getOrNull()
    return remember { KeyboardDatabaseController(context) }.apply {
        keyboardData(
            keyboardData,
            currentLayout.isEmbedded()
        )
    }
}

private fun MovementSequence.name(context: Context) = this.joinToString(", ") { it.name(context) }
