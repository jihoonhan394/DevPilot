package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;
import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.ClockConfig;
import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.domain.TryCatchBlock;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ARCH-05 · 06 · 07 · 08 · 15 · 17 · 18 · 19 (docs/08 §11.6). ARCH-10·21은 {@code
 * TransactionBoundaryArchTest}, ARCH-11~14·16은 대상이 생기는 단계에 추가한다.
 */
@UnitTest
class CodingRulesArchTest {

    /**
     * ARCH-05: {@code @Async}는 {@code ..infrastructure..*Task}에만,
     * {@code @TransactionalEventListener}와 함께.
     */
    @Test
    void shouldPlaceAsyncOnlyInInfrastructureTasksWithAfterCommitListener() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods()
                .that()
                .areAnnotatedWith(org.springframework.scheduling.annotation.Async.class)
                .should()
                .beDeclaredInClassesThat()
                .resideInAPackage("..infrastructure..")
                .andShould()
                .beDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Task")
                .andShould()
                .beAnnotatedWith(
                        org.springframework.transaction.event.TransactionalEventListener.class)
                .because("ARCH-05: docs/08 §4.3")
                .allowEmptyShould(true)
                .check(ArchitectureClasses.main());
    }

    /** ARCH-05: {@code @Scheduled}는 이름이 {@code Job}으로 끝나는 클래스에만. */
    @Test
    void shouldPlaceScheduledOnlyInJobs() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods()
                .that()
                .areAnnotatedWith(org.springframework.scheduling.annotation.Scheduled.class)
                .should()
                .beDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Job")
                .because("ARCH-05: docs/08 §4.3")
                .check(ArchitectureClasses.main());
    }

    private static final Set<Class<?>> TIME_TYPES =
            Set.of(
                    Instant.class,
                    LocalDate.class,
                    LocalDateTime.class,
                    LocalTime.class,
                    OffsetDateTime.class,
                    ZonedDateTime.class,
                    Year.class,
                    YearMonth.class);

    private static final Set<String> BROAD_EXCEPTIONS =
            Set.of(
                    Exception.class.getName(),
                    RuntimeException.class.getName(),
                    Throwable.class.getName());

    @Test
    void shouldNotUseFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION
                .because("ARCH-06: docs/08 §3.6")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotUseStandardStreamsJulJodaOrGenericExceptions() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(ArchitectureClasses.main());
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(ArchitectureClasses.main());
        NO_CLASSES_SHOULD_USE_JODATIME.check(ArchitectureClasses.main());
        NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotPrintStackTraceOrSleep() {
        noClasses()
                .should()
                .callMethod(Throwable.class, "printStackTrace")
                .because("ARCH-07: docs/08 §3.11")
                .check(ArchitectureClasses.main());
        noClasses()
                .that()
                .resideOutsideOfPackage("com.devpilot.integration.ai.deepseek..")
                .should()
                .callMethod(Thread.class, "sleep", long.class)
                .because("ARCH-07: docs/08 §3.9")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldNotReadWallClockExceptInClockConfig() {
        classes()
                .should(notCallWallClock())
                .because("ARCH-08: use the injected Clock (docs/08 §3.9)")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldBindConfigurationOnlyThroughDevPilotProperties() {
        classes()
                .that()
                .areAnnotatedWith(ConfigurationProperties.class)
                .should()
                .be(DevPilotProperties.class)
                .because("ARCH-15: docs/08 §3.7")
                .check(ArchitectureClasses.main());
        noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
                .because("ARCH-15: @Value is forbidden (docs/08 §3.7)")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldCatchBroadExceptionsOnlyInTasksAndJobs() {
        classes()
                .should(catchBroadExceptionsOnlyInTasksAndJobs())
                .because("ARCH-17: docs/08 §3.10")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldMarkEveryPackageNullMarked() {
        Set<String> packages = new TreeSet<>();
        for (JavaClass javaClass : ArchitectureClasses.main()) {
            if (!javaClass.getSimpleName().equals("package-info")) {
                packages.add(javaClass.getPackageName());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String name : packages) {
            JavaPackage javaPackage = ArchitectureClasses.main().getPackage(name);
            boolean marked =
                    javaPackage
                            .tryGetPackageInfo()
                            .map(info -> info.isAnnotatedWith(NullMarked.class))
                            .orElse(false);
            if (!marked) {
                missing.add(name);
            }
        }
        assertThat(missing).as("ARCH-18: package-info with @NullMarked (docs/08 §3.3)").isEmpty();
    }

    @Test
    void shouldUseHttpClientsOnlyInDeepSeekAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.devpilot.integration.ai.deepseek..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.net.http.HttpClient")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.net.HttpURLConnection")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.client.RestClient")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "org.springframework.web.reactive.function.client.WebClient")
                .orShould()
                .dependOnClassesThat()
                .resideInAPackage("okhttp3..")
                .orShould()
                .callMethod(java.net.URL.class, "openConnection")
                .orShould()
                .callMethod(java.net.URL.class, "openStream")
                .because("ARCH-19: the server does not fetch URLs (docs/07 §1.1 A10)")
                .check(ArchitectureClasses.main());
    }

    private static ArchCondition<JavaClass> notCallWallClock() {
        return new ArchCondition<>("not call now() without a Clock or the system clock") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean clockConfig = javaClass.isEquivalentTo(ClockConfig.class);
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    String owner = call.getTargetOwner().getName();
                    String name = call.getName();
                    List<JavaClass> parameters = call.getTarget().getRawParameterTypes();
                    boolean timeNow =
                            name.equals("now")
                                    && TIME_TYPES.stream()
                                            .anyMatch(type -> type.getName().equals(owner))
                                    && (parameters.isEmpty()
                                            || !parameters.get(0).isEquivalentTo(Clock.class));
                    boolean systemClock =
                            (owner.equals(Clock.class.getName())
                                            && (name.equals("systemUTC")
                                                    || name.equals("systemDefaultZone")))
                                    || (owner.equals(System.class.getName())
                                            && name.equals("currentTimeMillis"));
                    if (timeNow || (systemClock && !clockConfig)) {
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> catchBroadExceptionsOnlyInTasksAndJobs() {
        return new ArchCondition<>(
                "catch Exception/RuntimeException/Throwable only in *Task/*Job") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String name = javaClass.getSimpleName();
                if (name.endsWith("Task") || name.endsWith("Job")) {
                    return;
                }
                for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                    for (TryCatchBlock block : codeUnit.getTryCatchBlocks()) {
                        for (JavaClass caught : block.getCaughtThrowables()) {
                            if (BROAD_EXCEPTIONS.contains(caught.getName())) {
                                events.add(
                                        SimpleConditionEvent.violated(
                                                block,
                                                codeUnit.getFullName()
                                                        + " catches "
                                                        + caught.getName()));
                            }
                        }
                    }
                }
            }
        };
    }
}
