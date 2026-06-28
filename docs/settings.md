# Settings & Configuration

## Plugin Settings

**Location:** Settings → Languages & Frameworks → Terragrunt

| Setting | Default | Description |
|---|---|---|
| Entry point filenames | `terragrunt.hcl` | Filenames treated as entry points in the dependency tree (highlighted with deploy icon) |
| Marker filenames | `terragrunt.hcl`, `root.hcl`, `terragrunt.stack.hcl` | Files whose presence marks a directory as a Terragrunt project (used for file detection of generic `.hcl` files) |
| Terragrunt binary path | Auto-detected from PATH | Path to the `terragrunt` binary for run configurations |

## Inspections

**Location:** Settings → Editor → Inspections → Terragrunt

| Inspection | Default | Description |
|---|---|---|
| Unknown Terragrunt block | Enabled | Flags block types not in the schema |
| Unknown attribute in block | Enabled | Flags attributes not valid for their block |
| Missing required attribute | Enabled | Flags blocks missing required attrs (with quick-fix) |
| Deprecated attribute | Enabled | Flags deprecated attributes |
| Unresolved file path | Enabled | Flags include/dependency paths that can't resolve |
| Unresolved variable reference | Enabled | Flags local.X, dependency.X, feature.X that don't exist |
| Duplicate block name | Enabled | Flags same type+label in one file |
| Duplicate attribute | Enabled | Flags same key twice in a block body |
| Unexpected label count | Enabled | Flags missing, empty, or extra labels |
| Unused local variable | Enabled | Flags locals not referenced in the same file (skips shared configs) |
| Unused local variable (cross-file) | **Disabled** | Scans entire project — flags locals not referenced anywhere. Enable for thorough analysis. |
| Unused dependency | Enabled | Flags dependency blocks whose label is never used |

All inspections can be suppressed per-statement with `# noinspection ShortName` (see [inspections.md](inspections.md)).

## Code Style

**Location:** Settings → Editor → Code Style → Terragrunt

| Setting | Default | Description |
|---|---|---|
| Indent size | 2 spaces | Indentation for blocks and nested structures |

## Live Templates

**Location:** Settings → Editor → Live Templates → Terragrunt

| Abbreviation | Expands to |
|---|---|
| `dep` | `dependency` block |
| `inc` | `include` block |
| `gen` | `generate` block |
| `feat` | `feature` block |
| `loc` | `locals` block |
| `inp` | `inputs` attribute |

Type abbreviation + Tab to expand. Customizable in settings.

## File Detection

The plugin claims `.hcl` files as Terragrunt when:
1. Filename matches: `terragrunt.hcl`, `root.hcl`, `terragrunt.stack.hcl`, `terragrunt.values.hcl`
2. File is in a directory (or within 4 parent levels) containing a marker file
3. File contains Terragrunt-specific blocks (content heuristic)

To prevent the plugin from claiming non-Terragrunt `.hcl` files (Packer, Vault, etc.), adjust marker filenames in the plugin settings.
