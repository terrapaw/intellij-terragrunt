# intellij-terragrunt

Terragrunt HCL language support for IntelliJ-based IDEs.

## Features

- **Syntax highlighting** — keywords, strings, numbers, comments, operators, interpolation
- **Code completion**
  - Terragrunt blocks and attributes (context-aware)
  - 60+ built-in functions with signatures
  - Dot-completion: `dependency.` → names, `dependency.vpc.` → `outputs`, `dependency.vpc.outputs.` → mock_outputs keys, `local.` → variables, `feature.X.` → `value`
  - Cross-file: `include.root.locals.` → suggests attributes from included file
  - Alias-aware: `local.root_config.` → suggests attributes when `root_config = include.root.locals`
  - `read_terragrunt_config`: `local.common.locals.` and `local.common.inputs.` → suggests from loaded file
- **Inspections/Linting**
  - Unknown block types
  - Missing required attributes (with quick-fix to insert them)
  - Deprecated attributes
  - Unresolved file paths in `include` and `dependency` blocks
  - Unresolved variable references (`local.X`, `dependency.X`, `feature.X`)
- **Navigation (Ctrl+Click / Ctrl+B)**
  - `include` paths and `dependency` config_paths → jump to referenced files
  - `local.app_name` → jump to definition in `locals` block
  - `dependency.vpc` → jump to `dependency "vpc"` block
  - `feature.flag` → jump to `feature "flag"` block
  - `include.root.locals.region` → jump to `region` in the included file
  - `local.env_vars.environment` → resolves alias (`env_vars = include.env.locals`), jumps to included file
  - `local.common.locals.region` → resolves `read_terragrunt_config()`, jumps to loaded file
  - From definition → find all usages (Ctrl+B on `app_name` in `locals`)
  - Cross-file find usages — finds both direct and aliased references across project
- **Refactoring**
  - Rename local variables (Shift+F6) — updates definition and all `local.X` usages
- **Documentation (Ctrl+Q)** — shows function signatures and descriptions
- **Live templates** — `dep`, `inc`, `gen`, `feat`, `loc`, `inp` (type + Tab to expand)
- **Formatter (Ctrl+Alt+L)** — auto-indents with 2 spaces (configurable in Settings → Code Style)
- **String interpolation** — full support for `${...}` in strings and heredocs (highlighting, navigation, completion)
- **Editor support** — code folding, brace matching, comment/uncomment, color settings

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

133 tests covering lexer, parser, inspections, completion, navigation, formatting, and cross-file resolution.

## Installation

1. Build the plugin: `./gradlew buildPlugin`
2. In IntelliJ: Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select `build/distributions/terragrunt-hcl-plugin-0.1.0.zip`

## Requirements

- IntelliJ IDEA 2025.1+
- Java 21

## Architecture

Standalone plugin with its own Grammar-Kit based HCL parser — no dependency on the JetBrains Terraform/HCL plugin. This ensures stability across IDE updates.

## License

MIT
