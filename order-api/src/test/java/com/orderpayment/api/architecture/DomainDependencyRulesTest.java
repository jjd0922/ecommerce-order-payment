package com.orderpayment.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainDependencyRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.orderpayment");

    @Test
    @DisplayName("domain 계층은 외부 계층과 프레임워크에 의존하지 않는다")
    void domain_shouldNotDependOnApplicationInfrastructureApiOrFrameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure..",
                        "..api..",
                        "org.springframework..",
                        "jakarta..",
                        "javax.."
                )
                .check(classes);
    }
}
