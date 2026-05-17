# intellij-terragrunt

Terragrunt HCL language support for IntelliJ-based IDEs.

## Features

- **Syntax highlighting** — keywords, strings, numbers, comments, operators, interpolation
- **Code completion** — Terragrunt blocks, attributes, and 60+ built-in functions with signatures
- **Inspections/Linting**
  - Unknown block types
  - Missing required attributes
  - Deprecated attributes
  - Unresolved file paths in `include` and `dependency` blocks
- **Navigation** — Ctrl+Click on `include` paths and `dependency` config_paths to jump to referenced files
- **Editor support** — code folding, brace matching, comment/uncomment

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
