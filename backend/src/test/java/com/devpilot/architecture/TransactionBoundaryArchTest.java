package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * ARCH-21 (docs/08 §11.6, TX-1·TX-2). ARCH-10(트랜잭션 안 AI 호출 금지)은 {@code AiGateway}가 생기는 S3에 추가한다.
 */
@UnitTest
class TransactionBoundaryArchTest {

    private static final String[] TRANSACTIONAL_PACKAGES = {
        "..application..", "com.devpilot.common.idempotency..", "com.devpilot.integration.ai.log.."
    };

    @Test
    void shouldPlaceTransactionalOnlyInApplicationLayer() {
        noClasses()
                .that()
                .resideOutsideOfPackages(TRANSACTIONAL_PACKAGES)
                .should()
                .beAnnotatedWith(Transactional.class)
                .orShould()
                .beAnnotatedWith("jakarta.transaction.Transactional")
                .because("ARCH-21: TX-1")
                .check(ArchitectureClasses.main());
        methods()
                .that()
                .areAnnotatedWith(Transactional.class)
                .should()
                .bePublic()
                .andShould()
                .beDeclaredInClassesThat()
                .resideInAnyPackage(TRANSACTIONAL_PACKAGES)
                .because("ARCH-21: TX-1")
                .allowEmptyShould(true)
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldMakeQueryServicesReadOnly() {
        classes()
                .that()
                .haveSimpleNameEndingWith("QueryService")
                .should(beReadOnlyTransactional())
                .because("ARCH-21: TX-2")
                .check(ArchitectureClasses.main());
    }

    private static ArchCondition<JavaClass> beReadOnlyTransactional() {
        return new ArchCondition<>("be annotated with @Transactional(readOnly = true)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean readOnly =
                        javaClass.isAnnotatedWith(Transactional.class)
                                && javaClass.getAnnotationOfType(Transactional.class).readOnly();
                if (!readOnly) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    javaClass, javaClass.getName() + " is not read-only"));
                }
            }
        };
    }
}
