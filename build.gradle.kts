import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.17.0"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.17.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "com.github.terrapaw"
version = "0.4.1"

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
            <p>Full-featured Terragrunt support for IntelliJ-based IDEs. Navigate Terragrunt configurations across files, includes, dependencies, stacks, and function-generated paths as naturally as navigating source code.</p>
            <p>Works independently or alongside the Terraform/HCL plugin — no Terraform plugin dependency required.</p>

            <h3>Cross-File Navigation</h3>
            <ul>
              <li>Go to definition works through <code>read_terragrunt_config</code>, <code>find_in_parent_folders</code>, aliases, and nested configuration references</li>
              <li>Code completion resolves locals, dependencies, includes, and referenced configs across files</li>
              <li>File path completion with function evaluation — type <code>../</code> or <code>${'$'}{get_repo_root()}/</code> to get file/directory suggestions</li>
              <li>Navigate through alias chains regardless of depth</li>
              <li>Find usages of locals, inputs, object keys, and block labels</li>
            </ul>

            <h3>Refactoring</h3>
            <ul>
              <li>Rename locals, input keys, object keys, and mock_outputs across files (Shift+F6)</li>
              <li>Works from both definition and usage side — renames follow the same chains as navigation</li>
            </ul>

            <h3>Inspections &amp; Quick-Fixes</h3>
            <ul>
              <li>Unknown or deprecated blocks and attributes</li>
              <li>Unresolved paths and references</li>
              <li>Duplicate blocks and missing required attributes</li>
              <li>Typo correction suggestions via Alt+Enter</li>
              <li>Suppressible with <code># noinspection</code> comments</li>
            </ul>

            <h3>Tool Windows</h3>
            <ul>
              <li>Dependency graph — searchable tree view of the DAG with DOT export</li>
              <li>Input calculator — shows computed inputs with deep recursive resolution across includes and locals</li>
              <li>Run terragrunt commands (plan, apply, init, stack) from gutter markers and run configurations</li>
            </ul>

            <h3>Editor</h3>
            <ul>
              <li>Semantic syntax highlighting matching the Terraform/HCL plugin</li>
              <li>Formatter matching <code>terragrunt hcl format</code> output</li>
              <li>60+ built-in function signatures with documentation</li>
              <li>Code folding, live templates, structure view, breadcrumbs, file templates</li>
            </ul>

            <h3>Stacks &amp; Autoinclude</h3>
            <ul>
              <li>Full <code>autoinclude</code> support — completion and navigation for <code>unit.&lt;name&gt;.path</code> and <code>stack.&lt;name&gt;.path</code></li>
              <li>Validates autoinclude content (flags invalid locals, values, nested autoinclude)</li>
            </ul>

            <p>Supports Terragrunt 1.x including units, stacks, features, dependencies, remote state, generation, autoinclude, and catalog blocks.</p>

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
            val ver = project.version.toString()
            val item = if (ver.contains("-beta") || ver.contains("-rc")) {
                changelog.getUnreleased()
            } else {
                changelog.get(ver)
            }
            changelog.renderItem(item, org.jetbrains.changelog.Changelog.OutputType.HTML)
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.environmentVariable("PUBLISH_CHANNEL").map { ch ->
            if (ch == "default" || ch.isBlank()) listOf("") else listOf(ch)
        }.orElse(listOf(""))
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

