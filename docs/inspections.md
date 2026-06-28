# Inspections

## Suppressing Warnings

Add a `# noinspection` comment above the block or attribute to suppress a specific inspection. These comments are committable to source control.

```hcl
# noinspection TerragruntUnresolvedPath
dependency "api" {
  config_path = "./.terragrunt-stack/api"
}
```

You can also use Alt+Enter on any warning → "Suppress" to insert the comment automatically.

## Available Inspections

| Short Name | Description |
|---|---|
| `TerragruntUnknownBlock` | Flags block types not in the Terragrunt schema |
| `TerragruntUnknownAttribute` | Flags attributes not valid for the containing block |
| `TerragruntMissingAttribute` | Flags blocks missing required attributes (with quick-fix) |
| `TerragruntDeprecatedAttribute` | Flags deprecated attributes |
| `TerragruntUnresolvedPath` | Flags `include`/`dependency` paths that can't be resolved |
| `TerragruntUnresolvedVariable` | Flags `local.X`, `dependency.X`, `feature.X` that don't exist |
| `TerragruntDuplicateBlock` | Flags duplicate block type + label in the same file |
| `TerragruntDuplicateAttribute` | Flags same attribute key appearing twice in the same block body |
| `TerragruntLabelCount` | Flags missing, empty, or extra labels on blocks |
| `TerragruntUnusedLocal` | Flags `locals` attributes not referenced via `local.X` in the file |
| `TerragruntUnusedDependency` | Flags `dependency` blocks whose label is never referenced |

## Quick-Fixes

| Inspection | Quick-Fix |
|---|---|
| `TerragruntMissingAttribute` | Insert missing required attributes with defaults |
| `TerragruntDuplicateBlock` | Change block label / Remove duplicate block |
| `TerragruntLabelCount` | Remove extra label |
| `TerragruntUnknownBlock` | Change to closest matching block name (typo fix) |
| `TerragruntUnknownAttribute` | Change to closest matching attribute name (typo fix) |
| `TerragruntUnresolvedVariable` | Change to closest matching local/dependency/feature (typo fix) |
| All inspections | Suppress with `# noinspection` comment |

## Disabling Inspections

- **Per-statement:** `# noinspection ShortName` comment (see above)
- **Per-project:** Settings → Editor → Inspections → uncheck specific inspections
- **Per-scope:** Settings → Editor → Inspections → configure scope for each inspection
