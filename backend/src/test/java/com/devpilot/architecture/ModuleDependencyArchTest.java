package com.devpilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ARCH-01 모듈 의존 matrix, ARCH-02 다른 모듈 접근 범위, ARCH-03 integration.ai 공개 범위 (docs/08 §11.6, docs/03
 * §2.2). 표를 그대로 옮긴 상수이며 docs/03 §2.2를 바꾸는 PR은 이 상수를 같은 PR에서 바꾼다. ARCH-03: 다른 모듈은 {@code
 * integration.ai.api..}와 {@code AiGateway}, {@code SecretMasker}, {@code MaskingResult}, {@code
 * AiBudgetGuard}만 쓴다.
 */
@UnitTest
class ModuleDependencyArchTest {

    static final Map<String, Set<String>> ALLOWED_DEPENDENCIES =
            Map.ofEntries(
                    Map.entry("common", Set.of()),
                    Map.entry("integration.ai", Set.of("common")),
                    Map.entry("user", Set.of("common", "integration.ai")),
                    Map.entry("learning", Set.of("common", "integration.ai")),
                    Map.entry("skill", Set.of("common", "learning")),
                    Map.entry("goal", Set.of("common", "skill")),
                    Map.entry(
                            "plan",
                            Set.of("common", "goal", "skill", "learning", "integration.ai")),
                    Map.entry(
                            "review",
                            Set.of(
                                    "common",
                                    "skill",
                                    "learning",
                                    "goal",
                                    "plan",
                                    "integration.ai")),
                    Map.entry(
                            "training",
                            Set.of("common", "skill", "learning", "review", "integration.ai")),
                    Map.entry(
                            "today",
                            Set.of(
                                    "common",
                                    "plan",
                                    "skill",
                                    "review",
                                    "learning",
                                    "training",
                                    "goal",
                                    "project",
                                    "integration.ai")),
                    Map.entry(
                            "coach",
                            Set.of(
                                    "common",
                                    "skill",
                                    "learning",
                                    "review",
                                    "project",
                                    "integration.ai")),
                    Map.entry(
                            "evidence",
                            Set.of(
                                    "common",
                                    "skill",
                                    "learning",
                                    "coach",
                                    "training",
                                    "review",
                                    "plan",
                                    "integration.ai")),
                    Map.entry(
                            "radar",
                            Set.of(
                                    "common",
                                    "skill",
                                    "evidence",
                                    "goal",
                                    "plan",
                                    "integration.ai")),
                    Map.entry(
                            "onboarding",
                            Set.of(
                                    "common",
                                    "user",
                                    "goal",
                                    "plan",
                                    "skill",
                                    "review",
                                    "training",
                                    "project")),
                    Map.entry(
                            "dashboard",
                            Set.of(
                                    "common",
                                    "user",
                                    "plan",
                                    "skill",
                                    "review",
                                    "today",
                                    "learning",
                                    "coach",
                                    "evidence",
                                    "integration.ai")),
                    Map.entry("project", Set.of("common", "integration.ai")),
                    Map.entry(
                            "rubberduck",
                            Set.of(
                                    "common",
                                    "integration.ai",
                                    "skill",
                                    "learning",
                                    "review",
                                    "training",
                                    "today",
                                    "project")),
                    Map.entry(
                            "content",
                            Set.of("common", "skill", "plan", "review", "training", "today")),
                    Map.entry(
                            "account",
                            Set.of(
                                    "common",
                                    "user",
                                    "goal",
                                    "plan",
                                    "skill",
                                    "learning",
                                    "review",
                                    "today",
                                    "training",
                                    "coach",
                                    "evidence",
                                    "radar",
                                    "project",
                                    "rubberduck")));

    @Test
    void shouldOnlyDependOnAllowedModules() {
        classes()
                .that()
                .resideInAPackage(ArchitectureClasses.ROOT + "..")
                .should(onlyDependOnAllowedModules())
                .because("ARCH-01: docs/03 §2.2 module dependency table")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldAccessOtherModulesOnlyThroughApplicationOrSharedDomainTypes() {
        classes()
                .that()
                .resideInAPackage(ArchitectureClasses.ROOT + "..")
                .should(accessOtherModulesOnlyThroughAllowedTypes())
                .because(
                        "ARCH-02: other modules only through application classes or domain"
                                + " enums/records that are not entities (docs/03 §2.2)")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldAccessIntegrationAiOnlyThroughApi() {
        classes()
                .that()
                .resideInAPackage(ArchitectureClasses.ROOT + "..")
                .and()
                .resideOutsideOfPackage(ArchitectureClasses.ROOT + ".integration.ai..")
                .should(onlyDependOnIntegrationAiApi())
                .because("ARCH-03: docs/03 §3.3")
                .check(ArchitectureClasses.main());
    }

    @Test
    void shouldHaveNoCycleInDependencyTable() {
        Set<String> visited = new HashSet<>();
        for (String module : ALLOWED_DEPENDENCIES.keySet()) {
            assertThat(findCycle(module, new ArrayDeque<>(), visited)).as(module).isEmpty();
        }
    }

    @Test
    void shouldOnlyReferenceKnownModulesInDependencyTable() {
        ALLOWED_DEPENDENCIES
                .values()
                .forEach(targets -> assertThat(ALLOWED_DEPENDENCIES.keySet()).containsAll(targets));
    }

    private static Optional<List<String>> findCycle(
            String module, Deque<String> path, Set<String> visited) {
        if (path.contains(module)) {
            return Optional.of(List.copyOf(path));
        }
        if (!visited.add(module)) {
            return Optional.empty();
        }
        path.push(module);
        for (String target : ALLOWED_DEPENDENCIES.get(module)) {
            Optional<List<String>> cycle = findCycle(target, path, visited);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.pop();
        return Optional.empty();
    }

    static Optional<String> moduleOf(String packageName) {
        String prefix = ArchitectureClasses.ROOT + ".";
        if (!packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = packageName.substring(prefix.length());
        if (rest.startsWith("integration.ai")) {
            return Optional.of("integration.ai");
        }
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }

    private static ArchCondition<JavaClass> onlyDependOnAllowedModules() {
        return new ArchCondition<>("only depend on modules allowed by docs/03 §2.2") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<String> source = moduleOf(javaClass.getPackageName());
                if (source.isEmpty() || !ALLOWED_DEPENDENCIES.containsKey(source.get())) {
                    return;
                }
                Set<String> allowed = ALLOWED_DEPENDENCIES.get(source.get());
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    Optional<String> target =
                            moduleOf(dependency.getTargetClass().getPackageName());
                    if (target.isPresent()
                            && ALLOWED_DEPENDENCIES.containsKey(target.get())
                            && !target.get().equals(source.get())
                            && !allowed.contains(target.get())) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        dependency,
                                        source.get()
                                                + " -> "
                                                + target.get()
                                                + ": "
                                                + dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> accessOtherModulesOnlyThroughAllowedTypes() {
        return new ArchCondition<>("access other modules only through allowed types") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<String> source = moduleOf(javaClass.getPackageName());
                if (source.isEmpty() || "common".equals(source.get())) {
                    return;
                }
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    Optional<String> targetModule = moduleOf(target.getPackageName());
                    if (targetModule.isEmpty()
                            || targetModule.get().equals(source.get())
                            || "common".equals(targetModule.get())
                            || "integration.ai".equals(targetModule.get())) {
                        continue;
                    }
                    if (!isAllowedCrossModuleTarget(target, targetModule.get())) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        dependency, "ARCH-02: " + dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static boolean isAllowedCrossModuleTarget(JavaClass target, String module) {
        String base = ArchitectureClasses.ROOT + "." + module + ".";
        String packageName = target.getPackageName() + ".";
        if (packageName.startsWith(base + "application.")) {
            return target.getModifiers().contains(JavaModifier.PUBLIC);
        }
        if (packageName.startsWith(base + "domain.")) {
            return (target.isEnum() || target.isRecord())
                    && !target.isAnnotatedWith("jakarta.persistence.Entity");
        }
        return false;
    }

    /** ARCH-03: {@code integration.ai.api..} 밖에서 다른 모듈이 쓸 수 있는 클래스. */
    private static final Set<String> INTEGRATION_AI_PUBLIC =
            Set.of(
                    ArchitectureClasses.ROOT + ".integration.ai.AiGateway",
                    ArchitectureClasses.ROOT + ".integration.ai.masking.SecretMasker",
                    ArchitectureClasses.ROOT + ".integration.ai.masking.MaskingResult",
                    ArchitectureClasses.ROOT + ".integration.ai.budget.AiBudgetGuard");

    private static ArchCondition<JavaClass> onlyDependOnIntegrationAiApi() {
        return new ArchCondition<>(
                "only depend on integration.ai.api and the public AI entry points") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String api = ArchitectureClasses.ROOT + ".integration.ai.api";
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    if (targetPackage.startsWith(ArchitectureClasses.ROOT + ".integration.ai")
                            && !INTEGRATION_AI_PUBLIC.contains(
                                    dependency.getTargetClass().getName())
                            && !(targetPackage.equals(api)
                                    || targetPackage.startsWith(api + "."))) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        dependency, "ARCH-03: " + dependency.getDescription()));
                    }
                }
            }
        };
    }
}
