import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.16.0"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = "com.github.terrapaw"
version = "0.3.0"

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
        description = """
            <p>Full-featured Terragrunt HCL language support for IntelliJ-based IDEs. Standalone parser — no dependency on the Terraform plugin.</p>

            <h3>Code Completion</h3>
            <ul>
              <li>Context-aware block and attribute suggestions</li>
              <li>60+ built-in functions with signatures</li>
              <li>Dot-completion: <code>local.</code>, <code>dependency.vpc.outputs.</code>, <code>include.root.locals.</code></li>
              <li>Cross-file resolution through <code>include</code>, <code>read_terragrunt_config</code>, and aliases</li>
              <li>Nested object keys at any depth, including across files</li>
            </ul>

            <h3>Navigation</h3>
            <ul>
              <li>Ctrl+Click on variables, paths, and object keys — jumps to definition</li>
              <li>Resolves 10 Terragrunt functions in paths (<code>find_in_parent_folders</code>, <code>get_repo_root</code>, etc.)</li>
              <li>Arbitrary-depth chain navigation across multiple config files</li>
              <li>Find usages from definitions (locals, inputs, block labels, object keys)</li>
            </ul>

            <h3>Inspections &amp; Quick-Fixes</h3>
            <ul>
              <li>Unknown blocks/attributes, duplicate blocks, missing required attributes</li>
              <li>Unresolved file paths and variable references</li>
              <li>Alt+Enter quick-fixes to insert missing attributes or suppress warnings</li>
              <li>Suppressible with <code># noinspection</code> comments (committable to source control)</li>
            </ul>

            <h3>Editor Support</h3>
            <ul>
              <li>Syntax highlighting, code folding, brace matching, formatter</li>
              <li>Live templates (<code>dep</code>, <code>inc</code>, <code>gen</code>, <code>feat</code>, <code>loc</code>, <code>inp</code>)</li>
              <li>String interpolation support (<code>${'$'}{...}</code> in strings and heredocs)</li>
              <li>Rename refactoring (Shift+F6)</li>
              <li>Documentation popup (Ctrl+Q) for functions</li>
            </ul>

            <h3>Supported Blocks</h3>
            <p><code>terraform</code>, <code>remote_state</code>, <code>include</code>, <code>locals</code>, <code>dependency</code>, <code>dependencies</code>, <code>generate</code>, <code>catalog</code>, <code>engine</code>, <code>feature</code>, <code>exclude</code>, <code>errors</code>, <code>unit</code>, <code>stack</code></p>

            <h3>Requirements</h3>
            <p>IntelliJ IDEA 2025.1+ &bull; Java 21</p>
        """.trimIndent()
        vendor {
            name = "terrapaw"
        }
        ideaVersion {
            sinceBuild = "251"
        }
        changeNotes = providers.provider {
            changelog.renderItem(changelog.get(project.version.toString()), org.jetbrains.changelog.Changelog.OutputType.HTML)
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    version.set(project.version.toString())
    path.set(file("CHANGELOG.md").canonicalPath)
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

