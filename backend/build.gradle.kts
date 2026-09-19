// DevPilot backend build
// 기준: docs/18-project-setup-and-local-dev.md §4.3
//
// 주요 task
//   ./gradlew check            spotlessCheck + checkstyle + pmdMain + spotbugsMain + test + integrationTest
//                              + jacoco verification + openApiCheck
//   ./gradlew test             tag "unit" (Docker 불필요)
//   ./gradlew integrationTest  tag "integration" (Docker 필요: 서버 Docker 터널 infra/scripts/dev-docker-tunnel.ps1 또는 로컬 Docker)
//   ./gradlew bootRun          profile local, 저장소 루트 .env import (application-local.yml)
//   ./gradlew bootJar          build/libs/devpilot-api.jar (Dockerfile이 이 이름을 복사)
//   ./gradlew openApiUpdate    docs/api/openapi.yaml 스냅샷 갱신
//   ./gradlew aiEval -PevalSuite=coach-review -PevalRepeat=3   실제 모델 eval (비용 발생)

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    checkstyle
    pmd
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

group = "com.devpilot"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // DEC-02: Java 25. SP-4 실패 시 21로 내리고 ADR을 갱신한다
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ---------------------------------------------------------------------------
// Source sets
// ---------------------------------------------------------------------------
// evalTest: 실제 AI 모델을 호출하는 eval. check에 포함하지 않는다 (aiEval task로만 실행).
// docs/09 §3.2: test 출력에 의존하지 않고 main에만 의존한다
val evalTest: SourceSet =
    sourceSets.create("evalTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
configurations[evalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[evalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

// docs/07 §11.6: 모든 configuration lock. 의존성 변경 PR은 ./gradlew dependencies --write-locks 결과를 포함한다
dependencyLocking {
    lockAllConfigurations()
}

// Mockito를 javaagent로 붙인다 (JDK 21+ dynamic agent loading 경고 방지)
// (Gradle 9: 'by creating'/'by registering' delegate 문법은 deprecated → create/register 사용)
val mockitoAgent: Configuration =
    configurations.create("mockitoAgent") {
        isTransitive = false
    }

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
dependencies {
    // --- Spring Initializr (Boot 4.1.1) 생성 좌표. 이름을 추측으로 바꾸지 않는다 ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- 추가 의존성 (docs/18 §4.2) ---
    implementation(libs.springdoc.webmvc.ui) // OpenAPI 생성. Swagger UI는 local에서만 활성
    implementation(libs.jspecify) // null 계약 annotation (Boot BOM 관리 버전)
    // AI 공급자 SDK 없음: DeepSeek은 Spring RestClient로 직접 호출한다 (integration.ai.deepseek, docs/17 §2).
    // 요청·응답 매핑 테스트(DeepSeekAiProviderRequestTest)는 spring-test의 MockRestServiceServer를 쓴다 → 추가 의존성 없음

    testImplementation(libs.archunit.junit5)

    mockitoAgent(libs.mockito.core)
}

// ---------------------------------------------------------------------------
// Compile / packaging
// ---------------------------------------------------------------------------
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// docs/08 §3.1: main 컴파일 경고 1건 = 실패
tasks.compileJava {
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
}

// 저장소 루트 content/ (seed YAML, docs/19-content-spec.md) → classpath:content/
tasks.processResources {
    from(file("../content")) {
        into("content")
        include("**/*.yaml", "**/*.yml")
    }
}

// Dockerfile이 build/libs/devpilot-api.jar 를 복사한다. plain jar는 만들지 않는다
tasks.bootJar {
    archiveFileName = "devpilot-api.jar"
}

tasks.jar {
    enabled = false
}

// application-local.yml 의 spring.config.import(optional:file:../.env) 기준 경로 = backend/
tasks.bootRun {
    workingDir = projectDir
    args("--spring.profiles.active=local")
}

// ---------------------------------------------------------------------------
// Formatting & static analysis
// ---------------------------------------------------------------------------
spotless {
    java {
        target("src/*/java/**/*.java")
        // DEC-09: google-java-format AOSP 스타일(4칸), 100열
        googleJavaFormat(libs.versions.google.java.format.get()).aosp().reflowLongStrings()
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
}

checkstyle {
    // 명명 규칙 전용 설정 (포맷 규칙은 Spotless가 담당). 규칙 내용: docs/08-coding-conventions.md §11.3
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

// test 소스는 com.sun.net.httpserver(TestJwksServer)를 허용하는 설정을 쓴다 (docs/08 §11.3)
tasks.named<Checkstyle>("checkstyleTest") {
    configFile = file("config/checkstyle/checkstyle-test.xml")
}

pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = listOf()
    isConsoleOutput = true
    isIgnoreFailures = false
}

spotbugs {
    toolVersion = libs.versions.spotbugs.tool.get()
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = file("config/spotbugs/exclude.xml")
}

// docs/08 §11.1: pmdTest 비활성
tasks.named("pmdTest") {
    enabled = false
}
tasks.named("pmdEvalTest") {
    enabled = false
}

tasks.withType<SpotBugsTask>().configureEach {
    // 운영 코드만 분석한다 (test/evalTest 제외). HTML(로컬) + SARIF(CI 업로드), docs/08 §11.5
    enabled = name == "spotbugsMain"
    reports.create("html") {
        required = true
    }
    reports.create("sarif") {
        required = true
    }
}

// ---------------------------------------------------------------------------
// Tests — docs/09-test-and-quality.md §3.2
// ---------------------------------------------------------------------------
//   @Tag("unit")         규칙·ArchUnit·web slice      → test (Docker 불필요)
//   @Tag("integration")  Testcontainers postgres:16, E2E, OpenApiSnapshotTest → integrationTest (Docker 필요)
//   Testcontainers 이미지는 개발·운영 DB와 같은 major(16)로 고정한다 (docs/10 §2.4, docs/18 §1.1)
//   evalTest source set  실제 모델                       → aiEval (check 제외)
val openApiGenerated = layout.buildDirectory.file("openapi/openapi.yaml")
val openApiSnapshot = file("../docs/api/openapi.yaml")

tasks.test {
    useJUnitPlatform {
        includeTags("unit")
    }
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    systemProperty("user.timezone", "UTC")
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Testcontainers·E2E 테스트 (tag integration). Docker 필요 (DOCKER_HOST 터널 또는 로컬 Docker)"
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform {
            includeTags("integration")
        }
        shouldRunAfter(tasks.test)
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
        systemProperty("user.timezone", "UTC")
        // OpenApiSnapshotTest(test profile, springdoc.api-docs.enabled=true)가 이 경로에 YAML을 쓴다
        systemProperty("devpilot.openapi.output", openApiGenerated.get().asFile.absolutePath)
        outputs.file(openApiGenerated).optional()
    }

// 옵션 의미·기본값: evals/README.md, docs/17-ai-integration.md §12.3
//   -PevalSuite=<coach-review|hint-generate|challenge-evaluate|all> (필수)
//   -PevalCase=<case id glob>  -PevalRepeat=<n, 기본 3>  -PevalModel=<model, 기본 devpilot.ai.model>
//   -PevalMaxCostUsd=<USD, 기본 0.5 = 결정 E 1회 상한 (prod와 같은 DeepSeek 잔액)>  -PevalUpdateBaseline=<true|false>
// 환경변수: DEEPSEEK_API_KEY (필수). provider는 deepseek으로 고정한다
tasks.register<Test>("aiEval") {
    description = "실제 AI 모델 eval (비용 발생). -PevalSuite 필수, evals/README.md 참고"
    group = "verification"
    testClassesDirs = evalTest.output.classesDirs
    classpath = evalTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }

    val suite = providers.gradleProperty("evalSuite").orElse("")
    val evalCase = providers.gradleProperty("evalCase").orElse("")
    val repeat = providers.gradleProperty("evalRepeat").orElse("3")
    val model = providers.gradleProperty("evalModel").orElse("")
    val maxCostUsd = providers.gradleProperty("evalMaxCostUsd").orElse("0.5")
    val updateBaseline = providers.gradleProperty("evalUpdateBaseline").orElse("false")
    val apiKey = providers.environmentVariable("DEEPSEEK_API_KEY")

    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    systemProperty("user.timezone", "UTC")
    systemProperty("devpilot.eval.suite", suite.get())
    systemProperty("devpilot.eval.case", evalCase.get())
    systemProperty("devpilot.eval.repeat", repeat.get())
    systemProperty("devpilot.eval.model", model.get())
    systemProperty("devpilot.eval.max-cost-usd", maxCostUsd.get())
    systemProperty("devpilot.eval.update-baseline", updateBaseline.get())
    systemProperty("devpilot.eval.cases-dir", file("../evals/cases").absolutePath)
    systemProperty("devpilot.eval.baselines-dir", file("../evals/baselines").absolutePath)
    systemProperty("devpilot.eval.report-dir", layout.buildDirectory.dir("reports/ai-eval").get().asFile.absolutePath)
    environment("DEVPILOT_AI_PROVIDER", "deepseek")

    doFirst {
        if (suite.get().isBlank()) {
            throw GradleException("aiEval 에는 -PevalSuite=<coach-review|hint-generate|challenge-evaluate|all> 가 필요하다")
        }
        if (apiKey.orNull.isNullOrBlank()) {
            throw GradleException("aiEval 에는 DEEPSEEK_API_KEY 환경변수가 필요하다")
        }
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// docs/09 §15: test + integrationTest exec 데이터를 합쳐 계산
tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
    violationRules {
        rule {
            // 패키지별 LINE 0.80. 대상 패키지 목록은 docs/09 §15 표 (해당 패키지가 생기는 Sprint에 추가)
            // 아직 없는 패키지는 JaCoCo가 무시하므로 목록을 미리 적어 둔다.
            // 와일드카드(`com.devpilot.*.domain`)는 '.'도 매칭해 user/goal/onboarding 등 JPA entity 위주
            // 패키지까지 게이트에 넣으므로 쓰지 않는다 — docs/09 §15가 열거한 패키지만 명시한다.
            element = "PACKAGE"
            includes =
                listOf(
                    "com.devpilot.plan.domain",
                    "com.devpilot.skill.domain",
                    "com.devpilot.review.domain",
                    "com.devpilot.today.domain",
                    "com.devpilot.training.domain",
                    "com.devpilot.learning.domain",
                    "com.devpilot.coach.domain",
                    "com.devpilot.evidence.domain",
                    "com.devpilot.radar.domain",
                    "com.devpilot.common.time",
                    "com.devpilot.common.math",
                    "com.devpilot.integration.ai.guard",
                    "com.devpilot.integration.ai.masking",
                    "com.devpilot.integration.ai.budget",
                )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// OpenAPI snapshot (DEC-12: Flutter 모델은 이 스냅샷 기준 수기 freezed)
// ---------------------------------------------------------------------------
val openApiCheck =
    tasks.register("openApiCheck") {
        group = "verification"
        description = "생성된 OpenAPI와 docs/api/openapi.yaml 스냅샷을 비교한다"
        dependsOn(integrationTest)
        val generated = openApiGenerated
        val snapshot = openApiSnapshot
        inputs.files(generated, snapshot)
        doLast {
            val generatedFile = generated.get().asFile
            if (!generatedFile.exists()) {
                throw GradleException("OpenAPI 생성 파일이 없다: $generatedFile (OpenApiSnapshotTest 확인)")
            }
            val actual = generatedFile.readText().replace("\r\n", "\n")
            val expected = if (snapshot.exists()) snapshot.readText().replace("\r\n", "\n") else ""
            if (actual != expected) {
                throw GradleException(
                    "OpenAPI 스냅샷 불일치. ./gradlew openApiUpdate 실행 후 docs/api/openapi.yaml 을 커밋한다",
                )
            }
        }
    }

tasks.register<Copy>("openApiUpdate") {
    group = "documentation"
    description = "생성된 OpenAPI로 docs/api/openapi.yaml 을 갱신한다"
    dependsOn(integrationTest)
    from(openApiGenerated)
    into(file("../docs/api"))
}

tasks.check {
    dependsOn(tasks.named("spotlessCheck"), integrationTest, tasks.jacocoTestCoverageVerification, openApiCheck)
}
