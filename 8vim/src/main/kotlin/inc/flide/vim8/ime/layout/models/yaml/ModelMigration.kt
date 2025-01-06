package inc.flide.vim8.ime.layout.models.yaml

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

interface ModelMigration<T> {
    fun migrate(): T
}

interface Parser<T> {
    fun parse(mapper: ObjectMapper, jsonNode: JsonNode): T
}
