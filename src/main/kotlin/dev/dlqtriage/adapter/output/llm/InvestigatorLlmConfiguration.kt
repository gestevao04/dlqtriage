package dev.dlqtriage.adapter.output.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
class InvestigatorLlmConfiguration {
    @Bean
    fun investigatorObjectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    fun agentDecisionParser(objectMapper: ObjectMapper) = JacksonAgentDecisionParser(objectMapper)

    @Bean
    fun investigatorClock(): Clock = Clock.systemUTC()

    @Bean
    @Profile("local")
    fun springAiLlmPort(chatModel: OllamaChatModel) = SpringAiLlmAdapter(chatModel)

    @Bean
    @Profile("test")
    fun fakeLlmPort() = ProgrammableFakeLlmAdapter()

    @Bean
    @Profile("!local & !test")
    fun unavailableLlmPort() = UnavailableLlmAdapter()
}
