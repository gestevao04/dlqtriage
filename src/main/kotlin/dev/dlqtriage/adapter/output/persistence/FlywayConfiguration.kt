package dev.dlqtriage.adapter.output.persistence

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["spring.flyway.enabled"], havingValue = "true", matchIfMissing = true)
class FlywayConfiguration {
    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource): Flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
}
