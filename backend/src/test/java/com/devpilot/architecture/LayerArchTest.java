package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARCH-04(계층 방향 일부), ARCH-09(컨트롤러 위치·이름, entity 노출 금지), ARCH-14(entity·repository·JDBC 위치),
 * ARCH-20(명명 일부) — docs/08 §11.6.
 */
@UnitTest
class LayerArchTest {

    @Test
    void shouldNotLetPresentationDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..presentation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("ARCH-04: controller -> repository is forbidden (docs/03 §2.3)")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotDependOnPresentationFromOtherLayers() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..presentation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..presentation..")
                .because("ARCH-04: nothing depends on presentation (docs/03 §2.3)")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldKeepRestControllersInPresentationWithControllerSuffix() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .resideInAPackage("..presentation..")
                .andShould()
                .haveSimpleNameEndingWith("Controller")
                .because("ARCH-09: docs/08 §6")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNameConfigurationClassesWithConfigSuffix() {
        classes()
                .that()
                .areAnnotatedWith(Configuration.class)
                .should()
                .haveSimpleNameEndingWith("Config")
                .because("ARCH-20: docs/08 §7")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotUseVagueClassNameSuffixes() {
        noClasses()
                .should()
                .haveSimpleNameEndingWith("Util")
                .orShould()
                .haveSimpleNameEndingWith("Utils")
                .orShould()
                .haveSimpleNameEndingWith("Helper")
                .orShould()
                .haveSimpleNameEndingWith("Manager")
                .orShould()
                .haveSimpleNameEndingWith("Impl")
                .because("ARCH-20: docs/08 §7")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldKeepEntitiesInDomainPackages() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .or()
                .areAnnotatedWith(Embeddable.class)
                .should()
                .resideInAnyPackage("..domain..", "com.devpilot.integration.ai..")
                .because("ARCH-14: docs/08 §5")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldKeepRepositoriesInInfrastructureWithRepositorySuffix() {
        classes()
                .that()
                .areAssignableTo(Repository.class)
                .and()
                .areInterfaces()
                .should()
                .resideInAnyPackage("..infrastructure..", "com.devpilot.integration.ai..")
                .andShould()
                .haveSimpleNameEndingWith("Repository")
                .because("ARCH-14, ARCH-20: docs/08 §5·§7")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldUseJdbcClientOnlyInInfrastructureAndIdempotency() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..infrastructure..", "com.devpilot.common.idempotency..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(JdbcClient.class)
                .because("ARCH-14: docs/08 §5")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotExposeEntitiesFromControllersOrResponses() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .or()
                .haveSimpleNameEndingWith("Response")
                .should(notExposeEntities())
                .because("ARCH-09: docs/08 §6")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNameApplicationServices() {
        classes()
                .that()
                .areAnnotatedWith(Service.class)
                .should()
                .resideInAnyPackage("..application..", "com.devpilot.common.idempotency..")
                .because("ARCH-20: docs/08 §7")
                .check(ArchitectureClasses.main());
        classes()
                .that()
                .areAnnotatedWith(Service.class)
                .should()
                .haveSimpleNameEndingWith("Service")
                .orShould()
                .haveSimpleName("SkillStateUpdater")
                .because("ARCH-20: *Service, *QueryService or a docs/03 §3 name")
                .check(ArchitectureClasses.main());
    }

    private static ArchCondition<JavaClass> notExposeEntities() {
        return new ArchCondition<>("not expose @Entity types") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.isAnnotatedWith(RestController.class)) {
                    for (JavaMethod method : javaClass.getMethods()) {
                        if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                            continue;
                        }
                        method.getReturnType().getAllInvolvedRawTypes().stream()
                                .filter(type -> type.isAnnotatedWith(Entity.class))
                                .forEach(
                                        type ->
                                                events.add(
                                                        SimpleConditionEvent.violated(
                                                                method,
                                                                method.getFullName()
                                                                        + " returns "
                                                                        + type.getName())));
                    }
                    return;
                }
                for (JavaField field : javaClass.getFields()) {
                    field.getType().getAllInvolvedRawTypes().stream()
                            .filter(type -> type.isAnnotatedWith(Entity.class))
                            .forEach(
                                    type ->
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            field,
                                                            field.getFullName()
                                                                    + " exposes "
                                                                    + type.getName())));
                }
            }
        };
    }
}
