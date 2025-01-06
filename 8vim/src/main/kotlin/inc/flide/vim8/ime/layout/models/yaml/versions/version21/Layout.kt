package inc.flide.vim8.ime.layout.models.yaml.versions.version21

import arrow.optics.optics
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import inc.flide.vim8.ime.layout.models.yaml.ModelMigration
import inc.flide.vim8.ime.layout.models.yaml.Parser
import inc.flide.vim8.ime.layout.models.yaml.versions.common.LayoutInfo
import inc.flide.vim8.lib.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
@optics
data class Layout(
    @JsonProperty(required = true) val layers: Layers = Layers(),
    val info: LayoutInfo = LayoutInfo(),
    @JsonProperty(required = true) val version: String = "2.1"
) : ModelMigration<Layout> {
    override fun migrate(): Layout = this

    companion object
}

object LayoutParser : Parser<Layout> {
    override fun parse(mapper: ObjectMapper, jsonNode: JsonNode): Layout = mapper
        .convertValue(jsonNode)
}
