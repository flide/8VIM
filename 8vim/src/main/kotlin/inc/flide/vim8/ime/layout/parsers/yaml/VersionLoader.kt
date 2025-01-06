package inc.flide.vim8.ime.layout.parsers.yaml

import arrow.core.Either
import arrow.core.getOrNone
import arrow.core.left
import arrow.core.raise.either
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import inc.flide.vim8.ime.layout.models.error.InvalidLayoutError
import inc.flide.vim8.ime.layout.models.error.LayoutError
import inc.flide.vim8.ime.layout.models.error.UnknownVersion
import inc.flide.vim8.ime.layout.models.error.validationMessages
import inc.flide.vim8.ime.layout.models.yaml.versions.version2.LayoutParser as Layout2
import inc.flide.vim8.ime.layout.models.yaml.versions.version21.LayoutParser as Layout21
import java.text.MessageFormat

val versions = mapOf("2" to Layout2::parse, "2.1" to Layout21::parse)

inline fun <reified T : Any> JsonNode.loadYaml(
    schema: JsonSchema,
    mapper: ObjectMapper
): Either<LayoutError, T> = schema.validate(this)
    .fold(InvalidLayoutError(emptySet())) { acc, error ->
        val newError =
            if (error.message.startsWith('$')) {
                error
            } else {
                ValidationMessage.Builder()
                    .type(error.type)
                    .code(error.code)
                    .path(error.path)
                    .details(error.details)
                    .arguments(error.message)
                    .format(MessageFormat("{0}: {1}"))
                    .build()
            }
        InvalidLayoutError.validationMessages.modify(acc) { it + newError }
    }.let {
        if (it.validationMessages.isNotEmpty()) {
            it.left()
        } else {
            val version = get("version")?.asText() ?: "2"
            val jsonNode = this
            either {
                val parser = versions
                    .getOrNone(version)
                    .toEither { UnknownVersion(version) }
                    .bind()
                var current = parser(mapper, jsonNode)

                while (current.javaClass.name != T::class.qualifiedName) {
                    current = current.migrate()
                }
                current as T
            }
        }
    }
