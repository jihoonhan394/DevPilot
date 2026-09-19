// DevPilot backend (Gradle 9.7.1 wrapper, Spring Initializr 2026-09-17 생성값)
// 기준: docs/18-project-setup-and-local-dev.md §4

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // 저장소는 여기서만 선언한다. plugin이 project 저장소를 추가해도 무시한다
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "devpilot-api"
