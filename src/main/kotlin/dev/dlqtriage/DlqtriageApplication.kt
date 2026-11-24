package dev.dlqtriage

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class DlqtriageApplication

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    SpringApplication(DlqtriageApplication::class.java).run(*args)
}
