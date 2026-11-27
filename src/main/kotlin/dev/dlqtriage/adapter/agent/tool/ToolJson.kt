package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

internal fun ObjectMapper.parseToolInput(input: String): ObjectNode? =
    try {
        readTree(input)?.takeIf(JsonNode::isObject) as? ObjectNode
    } catch (_: JsonProcessingException) {
        null
    }

internal fun ObjectNode.requiredText(name: String): String? =
    get(name)?.takeIf(JsonNode::isTextual)?.textValue()?.takeIf(String::isNotBlank)

internal fun ObjectMapper.writeToolResult(value: Any): String = writeValueAsString(value)
