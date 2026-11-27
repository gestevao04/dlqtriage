package dev.dlqtriage.architecture

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(packages = ["dev.dlqtriage"])
class HexagonalArchitectureTest {
    @ArchTest
    val domainMustBeFrameworkIndependent: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("dev.dlqtriage.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "com.fasterxml.jackson..",
                "java.sql..",
                "jakarta.persistence..",
            )

    @ArchTest
    val applicationMustNotDependOnAdapters: ArchRule =
        noClasses()
            .that()
            .haveFullyQualifiedName("dev.dlqtriage.application.agent.InvestigateDlqMessageService")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.dlqtriage.adapter..")

    @ArchTest
    val agentToolsMustNotDependOnWritePorts: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("dev.dlqtriage.adapter.agent.tool..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "dev.dlqtriage.application.port.output.MessageRepository",
                "dev.dlqtriage.application.port.output.ClassificationRepository",
                "dev.dlqtriage.application.port.output.AgentRunRepository",
            )
}
