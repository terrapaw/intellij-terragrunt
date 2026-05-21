# intellij-terragrunt

Terragrunt HCL language support for IntelliJ-based IDEs.

## Features

- **Syntax highlighting** — keywords, strings, numbers, comments, operators, interpolation
- **Code completion**
  - Terragrunt blocks and attributes (context-aware)
  - 60+ built-in functions with signatures
  - Dot-completion: `dependency.` → names, `dependency.vpc.` → `outputs`, `local.` → variables, `feature.X.` → `value`
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
  - From definition → find all usages (Ctrl+B on `app_name` in `locals`)
- **Refactoring**
  - Rename local variables (Shift+F6) — updates definition and all `local.X` usages
- **Documentation (Ctrl+Q)** — shows function signatures and descriptions
- **Live templates** — `dep`, `inc`, `gen`, `feat`, `loc`, `inp` (type + Tab to expand)
- **Editor support** — code folding, brace matching, comment/uncomment, color settings

## Supported Blocks

`terraform`, `remote_state`, `include`, `locals`, `dependency`, `dependencies`, `generate`, `catalog`, `engine`, `feature`, `exclude`, `errors`, `unit`, `stack`

## File Detection

The plugin activates for:
- Files named `terragrunt.hcl`, `root.hcl`, or `terragrunt.stack.hcl`
- Any `.hcl` file containing Terragrunt-specific blocks (content heuristic)

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

71 tests covering lexer, parser, inspections, completion, and navigation.

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
