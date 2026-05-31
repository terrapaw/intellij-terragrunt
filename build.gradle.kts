import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.12.0"
}

group = "com.github.terrapaw"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.terrapaw.terragrunt"
        name = "Terragrunt HCL"
        version = project.version.toString()
        description = "IntelliSense and linting for Terragrunt HCL configuration files"
        vendor {
            name = "terrapaw"
        }
        ideaVersion {
            sinceBuild = "251"
        }
        changeNotes = providers.provider {
            val ver = project.version.toString()
            val changelog = file("CHANGELOG.md").readText()
            val section = changelog
                .substringAfter("## [$ver]", "")
                .ifEmpty { changelog.substringAfter("## [Unreleased]", "") }
                .substringBefore("## [")
                .trim()
            if (section.isBlank()) "See CHANGELOG.md" else section
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("src/main/gen")
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    generateLexer {
        sourceFile.set(file("src/main/java/com/github/terrapaw/terragrunt/lang/TerragruntLexer.flex"))
        targetOutputDir.set(file("src/main/gen/com/github/terrapaw/terragrunt/lang"))
        purgeOldFiles.set(true)
    }

    generateParser {
        sourceFile.set(file("src/main/java/com/github/terrapaw/terragrunt/lang/Terragrunt.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("com/github/terrapaw/terragrunt/lang/parser/TerragruntParser.java")
        pathToPsiRoot.set("com/github/terrapaw/terragrunt/lang/psi")
        purgeOldFiles.set(true)
    }

    withType<JavaCompile> {
        dependsOn(generateLexer, generateParser)
    }

    test {
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
            showCauses = true
            showExceptions = true

            afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                if (desc.parent == null) {
                    println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    println("  Results: ${result.resultType}")
                    println("  Tests:   ${result.testCount}")
                    println("  Passed:  ${result.successfulTestCount}")
                    println("  Failed:  ${result.failedTestCount}")
                    println("  Skipped: ${result.skippedTestCount}")
                    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                }
            }))
        }
    }
}
