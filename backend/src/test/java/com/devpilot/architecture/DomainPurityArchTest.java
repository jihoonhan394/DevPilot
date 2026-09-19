package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/**
 * ARCH-11, ARCH-12, ARCH-13, ARCH-16 (docs/08 §11.6). 규칙 클래스 목록은 S1에 있는 것만 넣고, 규칙 클래스가 생기는 단계마다
 * {@link #RULE_CLASSES}에 추가한다.
 */
@UnitTest
class DomainPurityArchTest {

    /** ARCH-12 규칙 클래스 (S1·S2). 중첩 입력·출력 record는 이름 접두사로 함께 잡힌다. */
    static final List<String> RULE_CLASSES =
            List.of(
                    "com.devpilot.skill.domain.PlanningLevelPolicy",
                    "com.devpilot.plan.domain.PlanTemplatePlacement",
                    "com.devpilot.plan.domain.StudyBudgetCalculator",
                    "com.devpilot.plan.domain.DeadlineRiskEvaluator",
                    "com.devpilot.plan.domain.ReplanSuggestionPolicy",

    private static final Set<String> FLOATING_TYPES =
            Set.of("double", "float", "java.lang.Double", "java.lang.Float");

    @Test
    void shouldKeepRuleClassesFreeOfFrameworks() {
        noClasses()
                .that(areRuleClasses())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "..application..",
                        "..infrastructure..")
                .because("ARCH-12: rule classes are pure Java (docs/03 §2.3)")
                .check(ArchitectureClasses.main());
        noClasses()
                .that(areRuleClasses())
                .should()
                .beAnnotatedWith(Component.class)
                .orShould()
                .beAnnotatedWith(Service.class)
                .orShould()
                .beAnnotatedWith(Repository.class)
                .because("ARCH-12")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotUseFloatingPointInRuleClasses() {
        classes()
                .that(areRuleClasses())
                .should(notUseFloatingPoint())
                .because("ARCH-13: docs/06 §1 N-1")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldInitializeSelfAssessmentOnlyFromSkillApplication() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.devpilot.skill.application..")
                .and()
                .doNotHaveFullyQualifiedName("com.devpilot.skill.domain.UserSkillState")
                .should()
                .callMethodWhere(
                        new DescribedPredicate<JavaAccess<?>>("UserSkillState level change") {
                            @Override
                            public boolean test(JavaAccess<?> access) {
                                return access.getTargetOwner()
                                                .getName()
                                                .equals("com.devpilot.skill.domain.UserSkillState")
                                        && Set.of(
                                                        "applyLevelChange",
                                                        "applyDiagnosticLevel",
                                                        "deactivateSelfAssessment",
                                                        "initializeSelfAssessment")
                                                .contains(access.getName());
                            }
                        })
                .because("ARCH-11: docs/09 I-12")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldKeepStateChangeRecordsImmutable() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .haveFullyQualifiedName("com.devpilot.skill.domain.SkillStateChange")
                .and()
                .arePublic()
                .should()
                .haveNameStartingWith("set")
                .because("ARCH-16: docs/09 I-13")
                .check(ArchitectureClasses.main());
    }

    private static DescribedPredicate<JavaClass> areRuleClasses() {
        return new DescribedPredicate<>("are rule classes or their nested records") {
            @Override
            public boolean test(JavaClass javaClass) {
                return RULE_CLASSES.stream()
                        .anyMatch(
                                name ->
                                        javaClass.getName().equals(name)
                                                || javaClass.getName().startsWith(name + "$"));
            }
        };
    }

    private static ArchCondition<JavaClass> notUseFloatingPoint() {
        return new ArchCondition<>("not use double or float") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (FLOATING_TYPES.contains(field.getRawType().getName())) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName()));
                    }
                }
                for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                    if (codeUnit.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                        continue;
                    }
                    if (FLOATING_TYPES.contains(codeUnit.getRawReturnType().getName())
                            || codeUnit.getRawParameterTypes().stream()
                                    .anyMatch(type -> FLOATING_TYPES.contains(type.getName()))) {
                        events.add(SimpleConditionEvent.violated(codeUnit, codeUnit.getFullName()));
                    }
                    codeUnit.getCallsFromSelf()
                            .forEach(
                                    call -> {
                                        boolean floating =
                                                call.getTarget().getRawParameterTypes().stream()
                                                                .anyMatch(
                                                                        type ->
                                                                                FLOATING_TYPES
                                                                                        .contains(
                                                                                                type
                                                                                                        .getName()))
                                                        || FLOATING_TYPES.contains(
                                                                call.getTarget()
                                                                        .getRawReturnType()
                                                                        .getName());
                                        if (floating) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            call, call.getDescription()));
                                        }
                                    });
                }
            }
        };
    }
}
