package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** TEST-01 · 02 · 04 · 05 (docs/09 §3.4). TEST-03(@Disabled 상한)은 @Disabled가 생기면 추가한다. */
@UnitTest
class TestCodeRulesArchTest {

    @Test
    void shouldNotSleepInTests() {
        noClasses()
                .should()
                .callMethod(Thread.class, "sleep", long.class)
                .because("TEST-01: use Awaitility or MutableClock")
                .check(ArchitectureClasses.tests());
    }

    @Test
    void shouldTagEveryTestClassExactlyOnce() {
        classes()
                .that()
                .haveSimpleNameEndingWith("Test")
                .and()
                .areTopLevelClasses()
                .should(haveExactlyOneTestTag())
                .because("TEST-02: untagged tests never run (docs/09 §3.2)")
                .check(ArchitectureClasses.tests());
    }

    @Test
    void shouldOrderTestMethodsOnlyInArchitectureTests() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.devpilot.architecture..")
                .should()
                .beAnnotatedWith(TestMethodOrder.class)
                .because("TEST-04")
                .check(ArchitectureClasses.tests());
    }

    @Test
    void shouldNotUseRetryingTests() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.junitpioneer..")
                .because("TEST-05")
                .check(ArchitectureClasses.tests());
    }

    private static ArchCondition<JavaClass> haveExactlyOneTestTag() {
        return new ArchCondition<>("be tagged unit or integration (exactly one)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                int tags = 0;
                if (javaClass.isAnnotatedWith(UnitTest.class)) {
                    tags++;
                }
                if (javaClass.isAnnotatedWith(IntegrationTest.class)) {
                    tags++;
                }
                if (javaClass.isAnnotatedWith(Tag.class)) {
                    String value = javaClass.getAnnotationOfType(Tag.class).value();
                    if (value.equals("unit") || value.equals("integration")) {
                        tags++;
                    }
                }
                if (tags != 1) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " has " + tags + " test tags"));
                }
            }
        };
    }
}
