package inc.flide.vim8.ime.layout.parsers.yaml

import arrow.core.Either
import arrow.core.fold
import arrow.core.left
import arrow.core.raise.catch
import arrow.core.toMap
import arrow.integrations.jackson.module.registerArrowModule
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.addDeserializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersionDetector
import inc.flide.vim8.ime.layout.models.CHARACTER_SET_SIZE
import inc.flide.vim8.ime.layout.models.CharacterPosition
import inc.flide.vim8.ime.layout.models.FingerPosition
import inc.flide.vim8.ime.layout.models.KeyboardAction
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.MovementSequence
import inc.flide.vim8.ime.layout.models.Quadrant
import inc.flide.vim8.ime.layout.models.addAllToActionMap
import inc.flide.vim8.ime.layout.models.characterIndexInString
import inc.flide.vim8.ime.layout.models.error.ExceptionWrapperError
import inc.flide.vim8.ime.layout.models.error.LayoutError
import inc.flide.vim8.ime.layout.models.setCharacterSets
import inc.flide.vim8.ime.layout.models.yaml.versions.common.Action
import inc.flide.vim8.ime.layout.models.yaml.versions.common.Flags
import inc.flide.vim8.ime.layout.models.yaml.versions.common.Layer
import inc.flide.vim8.ime.layout.models.yaml.versions.common.isEmpty
import inc.flide.vim8.ime.layout.models.yaml.versions.common.keyCode
import inc.flide.vim8.ime.layout.models.yaml.versions.common.toLayerLevel
import inc.flide.vim8.ime.layout.models.yaml.versions.version21.Layout
import inc.flide.vim8.ime.layout.parsers.LayoutParser
import java.io.IOException
import java.io.InputStream

private val schemaMappings = mapOf(
    "https://8vim.github.io/schemas/schema.json" to "resource:/schemas/schema.json",
    "https://8vim.github.io/schemas/common.json" to "resource:/schemas/common.json",
    "https://8vim.github.io/schemas/versions/2.json" to "resource:/schemas/versions/2.json",
    "https://8vim.github.io/schemas/versions/2.1.json" to "resource:/schemas/versions/2.1.json"
)

class YamlParser : LayoutParser {
    private val module = SimpleModule(Flags.FlagsDeserializer::class.qualifiedName)
        .addDeserializer(
            Flags::class,
            Flags.FlagsDeserializer()
        )
    private val mapper: ObjectMapper =
        YAMLMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addModule(module)
            .build()
            .registerKotlinModule()
            .registerArrowModule()
    private var schema: JsonSchema

    init {
        LayoutParser::class.java.getResourceAsStream("/schemas/schema.json")
            .use { schemaInputStream ->
                val schemaJson = mapper.readTree(schemaInputStream)
                val factory = JsonSchemaFactory.builder(
                    JsonSchemaFactory.getInstance(SpecVersionDetector.detect(schemaJson))
                ).addUriMappings(schemaMappings)
                    .objectMapper(mapper)
                    .build()
                schema = factory.getSchema(schemaJson)
            }
    }

    override fun readKeyboardData(inputStream: InputStream?): Either<LayoutError, KeyboardData> {
        return catch({
            mapper.readTree(inputStream).loadYaml<Layout>(schema, mapper)
                .map { layout ->
                    val keyboardData = KeyboardData(
                        info = layout.info,
                        actionMap = getActionMap(layout.layers.hidden, LayerLevel.HIDDEN) +
                            getActionMap(layout.layers.functions, LayerLevel.FUNCTIONS)
                    )

                    val layersToAdd = layout.layers.defaultLayer
                        .map { LayerLevel.FIRST to it }
                        .toMap() + layout.layers.extraLayers
                        .mapKeys { it.key.toLayerLevel() }

                    layersToAdd
                        .fold(keyboardData) { acc, (layerId, layer) ->
                            addLayer(acc, layerId, layer)
                        }
                }
        }) { exception: IOException ->
            ExceptionWrapperError(exception).left()
        }
    }

    private fun addLayer(
        keyboardData: KeyboardData,
        layer: LayerLevel,
        layerData: Layer
    ): KeyboardData {
        val characterSets = arrayOfNulls<KeyboardAction?>(CHARACTER_SET_SIZE)
        return layerData.sectors.fold(keyboardData) { acc, (sector, value) ->
            value.parts.fold(acc) { acc1, (part, actions) ->
                acc1.addAllToActionMap(
                    getActionMap(
                        layer = layer,
                        quadrant = Quadrant(sector, part),
                        actions = actions,
                        characterSets = characterSets
                    )
                )
            }
        }
            .setCharacterSets(characterSets.toList(), layer)
    }

    private fun getActionMap(
        actions: List<Action>,
        layer: LayerLevel
    ): Map<MovementSequence, KeyboardAction> {
        return actions
            .filterNot { it.movementSequence.isEmpty() }
            .associateBy({ it.movementSequence }, {
                KeyboardAction(
                    it.actionType,
                    it.lowerCase,
                    it.upperCase,
                    it.keyCode(),
                    it.flags.value,
                    layer
                )
            })
    }

    private fun getActionMap(
        layer: LayerLevel,
        quadrant: Quadrant,
        actions: List<Action?>,
        characterSets: Array<KeyboardAction?>
    ): Map<MovementSequence, KeyboardAction> {
        return actions
            .take(4)
            .withIndex()
            .filterNot { it.value.isEmpty() }
            .fold(emptyMap()) { acc, (i, action) ->
                val characterPosition = CharacterPosition.entries[i]
                var movementSequence: MovementSequence = action!!.movementSequence
                if (movementSequence.isEmpty()) {
                    movementSequence = FingerPosition.computeMovementSequence(
                        layer,
                        quadrant,
                        characterPosition
                    )
                }
                val characterSetIndex: Int =
                    quadrant.characterIndexInString(characterPosition)
                val updatedAction = if (action.lowerCase.isNotEmpty()) {
                    if (action.upperCase.isEmpty()) {
                        action.copy(upperCase = action.lowerCase.uppercase())
                    } else {
                        action
                    }
                } else {
                    action
                }

                val keyboardAction = KeyboardAction(
                    updatedAction.actionType,
                    updatedAction.lowerCase,
                    updatedAction.upperCase,
                    updatedAction.keyCode(),
                    updatedAction.flags.value,
                    layer
                )
                characterSets[characterSetIndex] = keyboardAction
                val baseActionMap = movementSequence to keyboardAction
                val actionMap = when {
                    layer != LayerLevel.FIRST && action.movementSequence.isEmpty() -> mapOf(
                        baseActionMap,
                        FingerPosition.computeQuickMovementSequence(
                            layer,
                            quadrant,
                            characterPosition
                        ) to keyboardAction
                    )

                    else -> mapOf(baseActionMap)
                }

                acc + actionMap
            }
    }
}
