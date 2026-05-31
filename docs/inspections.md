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
| `TerragruntLabelCount` | Flags missing, empty, or extra labels on blocks |

## Disabling Inspections

- **Per-statement:** `# noinspection ShortName` comment (see above)
- **Per-project:** Settings → Editor → Inspections → uncheck specific inspections
- **Per-scope:** Settings → Editor → Inspections → configure scope for each inspection
