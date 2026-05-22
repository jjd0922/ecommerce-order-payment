package com.orderpayment.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdapterDependencyRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.orderpayment");

    @Test
    @DisplayName("api adapter는 infrastructure adapter에 의존하지 않는다")
    void apiAdapters_shouldNotDependOnInfrastructureAdapters() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
    }

    @Test
    @DisplayName("api controller는 application input port를 통해 유스케이스를 호출한다")
    void apiControllers_shouldDependOnApplicationInputPorts() {
        classes()
                .that().resideInAnyPackage("..api.admin..", "..api.order..", "..api.payment..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage("..application..port.in..")
                .check(classes);
    }

    @Test
    @DisplayName("infrastructure adapter는 api adapter에 의존하지 않는다")
    void infrastructureAdapters_shouldNotDependOnApiAdapters() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(classes);
    }

    @Test
    @DisplayName("persistence adapter는 application output port를 구현한다")
    void persistenceAdapters_shouldImplementApplicationOutputPorts() {
        classes()
                .that().resideInAPackage("..infrastructure..")
                .and().haveSimpleNameEndingWith("PersistenceAdapter")
                .should().implement(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "application output port",
                        javaClass -> javaClass.getPackageName().contains(".application.")
                                && javaClass.getPackageName().contains(".port.out")
                ))
                .check(classes);
    }
}
