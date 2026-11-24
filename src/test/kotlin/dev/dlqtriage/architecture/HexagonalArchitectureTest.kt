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
}
