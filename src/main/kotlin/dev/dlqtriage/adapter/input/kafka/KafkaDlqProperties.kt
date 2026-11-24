package dev.dlqtriage.adapter.input.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dlq.kafka")
data class KafkaDlqProperties(
    var topic: String = "",
)
