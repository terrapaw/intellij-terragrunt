# Testing

## Test Isolation

Tests use `BasePlatformTestCase` which shares a light project across all tests in the same class. Files added via `addFileToProject` persist for the lifetime of the class — they leak between tests.

**Convention:** Use unique filename prefixes per test (e.g. `stack-ctx-env.hcl`, `deep-comp-shared.hcl`) to avoid collisions. This is the standard IntelliJ plugin testing workaround — the framework doesn't support per-test file cleanup for light tests.

The alternative (heavy tests with `HeavyPlatformTestCase`) creates a fresh project per test but is significantly slower. Not worth it for our test suite.

## Running Tests

```bash
./gradlew test
```

Run a single test:
```bash
./gradlew test --tests "com.github.terrapaw.terragrunt.TerragruntCrossFileTest.testStackContextResolution"
```

## Test Structure

| Class | Covers |
|-------|--------|
| `TerragruntLexerTest` | Token types, keywords, operators |
| `TerragruntParsingTest` | Basic parsing scenarios |
| `TerragruntParsingEdgeCaseTest` | Edge cases, error recovery |
| `TerragruntInspectionTest` | All inspections |
| `TerragruntCompletionTest` | Context-aware completion |
| `TerragruntCompletionInsertTest` | Insert behavior, live templates |
| `TerragruntCrossFileTest` | Cross-file navigation, completion, stack context, deep chains |
| `TerragruntSettingsTest` | Settings defaults and integration |
| `TerragruntFileTypeTest` | File detection, exclusions |
| `TerragruntCoreUxTest` | Find usages, quick-fix, unresolved variable |

## Tips

- Always add a `root.hcl` marker file if your test needs non-`terragrunt.hcl` files to be detected as Terragrunt
- Use `myFixture.configureFromExistingVirtualFile()` when testing navigation on a specific file
- IntelliJ must not be running when executing tests (JBR process conflict)
