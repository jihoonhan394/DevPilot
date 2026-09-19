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

/** ARCH-10(트랜잭션 안 AI 호출 금지, T-2)과 ARCH-21 (docs/08 §11.6, TX-1·TX-2·TX-3). */
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

    /**
     * ARCH-10: {@code @Transactional} 메서드(클래스)에서 {@code AiGateway}·{@code AiProvider} 호출 금지 (T-2).
     */
    @Test
    void shouldNotCallAiInsideTransactionalMethods() {
        methods()
                .that()
                .areAnnotatedWith(Transactional.class)
                .or()
                .areAnnotatedWith("jakarta.transaction.Transactional")
                .or()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Transactional.class)
                .should(notCallAi())
                .because("ARCH-10: T-2 — AI calls stay outside transactions")
                .allowEmptyShould(true)
                .check(ArchitectureClasses.main());
    }

    /**
     * ARCH-10: {@code AiGateway}/{@code AiProvider} 필드를 가진 클래스에는 클래스 레벨 {@code @Transactional}이 없다.
     */
    @Test
    void shouldNotAnnotateAiCallersWithClassLevelTransactional() {
        classes()
                .that(haveAiField())
                .should()
                .notBeAnnotatedWith(Transactional.class)
                .andShould()
                .notBeAnnotatedWith("jakarta.transaction.Transactional")
                .because("ARCH-10: TX-3")
                .allowEmptyShould(true)
                .check(ArchitectureClasses.main());
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> haveAiField() {
        return new com.tngtech.archunit.base.DescribedPredicate<>(
                "have an AiGateway or AiProvider field") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getFields().stream()
                        .anyMatch(field -> isAiType(field.getRawType().getName()));
            }
        };
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> notCallAi() {
        return new ArchCondition<>("not call AiGateway or AiProvider") {
            @Override
            public void check(
                    com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                method.getMethodCallsFromSelf().stream()
                        .filter(call -> isAiType(call.getTargetOwner().getName()))
                        .forEach(
                                call ->
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName()
                                                                + " calls "
                                                                + call.getTarget().getFullName())));
            }
        };
    }

    private static boolean isAiType(String name) {
        return name.equals("com.devpilot.integration.ai.AiGateway")
                || name.equals("com.devpilot.integration.ai.api.AiProvider");
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
