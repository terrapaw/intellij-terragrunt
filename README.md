# intellij-terragrunt

[![CI](https://github.com/terrapaw/intellij-terragrunt/actions/workflows/ci.yml/badge.svg)](https://github.com/terrapaw/intellij-terragrunt/actions/workflows/ci.yml)

Terragrunt HCL language support for IntelliJ-based IDEs.

## Features

- **Syntax highlighting** — keywords, strings, numbers, comments, operators, interpolation
- **Code completion**
  - Terragrunt blocks and attributes (context-aware)
  - 60+ built-in functions with signatures
  - Dot-completion: `dependency.` → names, `dependency.vpc.` → `outputs`, `dependency.vpc.outputs.` → mock_outputs keys, `local.` → variables, `feature.X.` → `value`
  - Cross-file: `include.root.locals.` → suggests attributes from included file
  - Alias-aware: `local.root_config.` → suggests attributes when `root_config = include.root.locals`
  - Alias-aware: `local.env_inputs.` → suggests input keys when `env_inputs = include.env.inputs`
  - `read_terragrunt_config`: `local.common.locals.` and `local.common.inputs.` → suggests from loaded file
  - For expressions: `[for` / `{for` templates, and loop variable completion inside for body
- **Inspections/Linting**
  - Unknown block types
  - Unknown attributes in blocks
  - Duplicate block names (e.g. two `dependency "vpc"` in the same file) — quick-fix to change label or remove block
  - Unexpected label count (e.g. `locals "foo"`, `dependency "vpc" "extra"`, `dependency {}`, `dependency "" {}`) — quick-fix to remove extra labels
  - Missing required attributes (with quick-fix to insert them)
  - Deprecated attributes
  - Unresolved file paths in `include` and `dependency` blocks (suppressed for `.terragrunt-stack/` when stack defines the unit)
  - Unresolved variable references (`local.X`, `dependency.X`, `feature.X`)
  - All inspections suppressible with `# noinspection ShortName` comments (committable to source control)
- **Navigation (Ctrl+Click / Ctrl+B)**
  - `include` paths and `dependency` config_paths → jump to referenced files
  - Function-aware: resolves `get_parent_terragrunt_dir()`, `get_terragrunt_dir()`, `get_root_terragrunt_dir()`, `get_repo_root()`, `find_in_parent_folders()`, `dirname()`, `basename()` in paths
  - `get_parent_terragrunt_dir()` uses include semantics (returns own dir for parent configs, supports named includes)
  - Stack context: resolves `read_terragrunt_config(find_in_parent_folders(...))` from includer directories (e.g. `.terragrunt-stack/` generated units)
  - `local.app_name` → jump to definition in `locals` block
  - `dependency.vpc` → jump to `dependency "vpc"` block
  - `feature.flag` → jump to `feature "flag"` block
  - `include.root.locals.region` → jump to `region` in the included file
  - `local.env_vars.environment` → resolves alias (`env_vars = include.env.locals`), jumps to included file
  - `local.common.locals.region` → resolves `read_terragrunt_config()`, jumps to loaded file
  - Deep chain: `include.root.locals.env_config.locals.environment` → resolves through nested aliases at any depth
  - `dependency.vpc.outputs.vpc_id` → jumps to `vpc_id` in mock_outputs
  - `feature.flag.value` → jumps to `default` attribute in feature block
  - `values.environment` → jumps to definition in `terragrunt.stack.hcl` unit values block
  - From definition → find all usages (Ctrl+B on `app_name` in `locals`)
  - From label → find all usages (Ctrl+B on `"vpc"` in `dependency "vpc"`)
  - Cross-file find usages — finds both direct and aliased references across project
- **Refactoring**
  - Rename local variables (Shift+F6) — updates definition and all `local.X` usages
- **Documentation (Ctrl+Q)** — shows function signatures and descriptions
- **Live templates** — `dep`, `inc`, `gen`, `feat`, `loc`, `inp` (type + Tab to expand)
- **Formatter (Ctrl+Alt+L)** — auto-indents with 2 spaces (configurable in Settings → Code Style)
- **String interpolation** — full support for `${...}` in strings and heredocs (highlighting, navigation, completion)
- **Editor support** — code folding, brace matching, auto-close quotes/brackets, comment/uncomment, color settings
- **Structure view (Alt+7)** — shows blocks, attributes, and nested object keys in the Structure tool window
- **Breadcrumbs** — editor breadcrumb bar showing block/attribute hierarchy
- **File templates** — New → Terragrunt File (Unit, Root, Stack templates)
- **Run configurations** — execute terragrunt commands from the IDE (plan, apply, init, validate, destroy, stack generate) with output in the Run tool window
- **Gutter run markers** — click the run icon in the gutter to quickly run commands for the current file
- **Dependency tree** — tool window showing the DAG of dependency blocks across the project, with search, context menu, entry point highlighting, and DOT export
- **Settings** — configurable entry point filenames, marker filenames, and binary path (Settings → Languages & Frameworks → Terragrunt)

## Supported Blocks

`terraform`, `remote_state`, `include`, `locals`, `dependency`, `dependencies`, `generate`, `catalog`, `engine`, `feature`, `exclude`, `errors`, `unit`, `stack`

## File Detection

The plugin activates for:
- Files named `terragrunt.hcl`, `root.hcl`, `terragrunt.stack.hcl`, or `terragrunt.values.hcl`
- Any `.hcl` file containing Terragrunt-specific blocks (content heuristic)
- Any `.hcl` file in a directory (or parent directories) containing Terragrunt files

## Building

```bash
./gradlew buildPlugin
```

The distributable zip is output to `build/distributions/`.

## Running (Development)

```bash
./gradlew runIde
```

Launches a sandboxed IntelliJ instance with the plugin loaded.

## Testing

```bash
./gradlew test
```

305 tests covering lexer, parser, inspections, completion, navigation, formatting, and cross-file resolution.

## Installation

1. Build the plugin: `./gradlew buildPlugin`
2. In IntelliJ: Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select `build/distributions/terragrunt-hcl-plugin-x.x.x.zip`

## Requirements

- IntelliJ IDEA 2025.1+
- Java 21

## Releasing

1. Update `version` in `build.gradle.kts`
2. Commit and push to main
3. Tag and push:
   ```bash
   git tag v0.2.0
   git push --tags
   ```
4. GitHub Actions builds, tests, and creates a Release with the plugin zip attached

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and add tests
4. Run tests: `./gradlew test`
5. Submit a pull request

All PRs require CI to pass before merging.

## Architecture

Standalone plugin with its own Grammar-Kit based HCL parser — no dependency on the JetBrains Terraform/HCL plugin. This ensures stability across IDE updates.

## License

Apache 2.0
