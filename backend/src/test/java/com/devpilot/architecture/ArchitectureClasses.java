package com.devpilot.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * ArchUnit 대상 클래스 (docs/09 §7). main은 DoNotIncludeTests, 테스트 규칙은 OnlyIncludeTests. 한 번만 import한다.
 */
final class ArchitectureClasses {

    static final String ROOT = "com.devpilot";

    private static final JavaClasses MAIN =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    private static final JavaClasses TESTS =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                    .importPackages(ROOT);

    private ArchitectureClasses() {}

    static JavaClasses main() {
        return MAIN;
    }

    static JavaClasses tests() {
        return TESTS;
    }
}
