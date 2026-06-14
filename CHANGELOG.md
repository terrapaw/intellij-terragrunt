# Changelog

## [Unreleased]

### Added
- Plugin logo for Marketplace listing
- Navigate to `locals` block / `inputs` attribute from chain keywords (Ctrl+B on `locals` in `local.config.locals` or `include.root.inputs`)
- Navigate to directories from path strings and interpolated paths (e.g. `"../modules/vpc"`, `"${get_terragrunt_dir()}/modules"`)
- Navigate from function names in path expressions (Ctrl+B on `read_terragrunt_config` or `find_in_parent_folders` navigates to the resolved file)
- Unified Ctrl+hover highlight for interpolated path strings (full string underlines as one unit)
- Code folding for attributes with object values (`inputs = {...}`, `mock_outputs = {...}`)
- Rich HTML description on Marketplace plugin page
- File templates: New → Terragrunt File (Unit, Root, Stack)
- Structure view (Alt+7) showing blocks, attributes, and nested object keys
- Breadcrumbs showing block/attribute/object key hierarchy in the editor
- Quick-fix: Remove extra label (Alt+Enter on unexpected labels)
- Quick-fix: Rename duplicate block label (Alt+Enter on duplicates)
- Quick-fix: Remove duplicate block (Alt+Enter on duplicates)
- Quick-fix: Suggest closest name for typos in block types, attributes, and variable references
- `values.X` navigation in stack units (Ctrl+B jumps to definition in `terragrunt.stack.hcl`, find usages from definition)
- Cross-file rename: Shift+F6 on a local variable now updates usages in other files (`include.X.locals.Y`, `local.alias.Y`, `local.alias.locals.Y`)
- Rename inputs keys (Shift+F6 on key in `inputs = { ... }`) updates cross-file `include.X.inputs.Y` references
- Rename nested object keys (Shift+F6 on key in locals object) updates `local.attr.key` usages in same file and cross-file
- Rename mock_outputs keys (Shift+F6) updates `dependency.X.outputs.Y` usages, works from both definition and usage side
- Run configurations: execute `terragrunt` commands (plan/apply/init/validate/destroy) from the IDE with output in the Run tool window
- Gutter run marker on `terragrunt.hcl` (plan, apply, init) and `terragrunt.stack.hcl` (stack generate, stack run plan, stack run apply)
- Binary path setting in Settings → Languages & Frameworks → Terragrunt (auto-detects from PATH)
- Dependency tree tool window (View → Tool Windows → Terragrunt Dependencies) showing DAG of dependency blocks across the project

### Fixed
- `feature.X.value` and `dependency.X.outputs.Y` navigation incorrectly matched the first block instead of the named one when multiple blocks existed
- Nested alias completion and navigation: `local.read.locals.a.b.c` now follows same-file alias chains (e.g. `a = local.abc.locals.a`) through multiple files
- `dependency.X.outputs.` completion suggested mock_outputs keys from the wrong dependency block when multiple existed
- Potential crash during live editing when parser produces incomplete block nodes (null identifier guard)
- Syntax highlighting colours missing compared to Terraform/HCL plugin (#62)
- Live templates not appearing between blocks when next line starts with a block (e.g. `dep` before `terraform {`)
- Live templates not expanding on blank lines between blocks when parser creates malformed block from typed prefix
- Formatter now matches `terragrunt hcl format` output (= alignment, spacing, comment/blank-line group resets)
- StackOverflow crash on projects with `.terragrunt-cache` symlinks during find-usages (now skips hidden dirs)
- Chain resolver reading past cursor boundary causing incorrect completion in deep chains

## [0.3.0] - 2026-06-02

### Added
- Function-aware path resolution in includes and read_terragrunt_config
  - `get_terragrunt_dir()`, `get_root_terragrunt_dir()`
  - `get_parent_terragrunt_dir()` with include semantics (returns own dir for parent configs, resolves include for children, supports named includes)
  - `get_repo_root()`, `get_path_to_repo_root()`, `get_path_from_repo_root()`
  - `find_in_parent_folders()` inside interpolation (with fallback arg support)
  - `dirname()`, `basename()` (nested evaluation)
- Stack context resolution: `read_terragrunt_config(find_in_parent_folders(...))` resolves from includer directories (non-entry-point files won't guess — shows "no declaration" until includers exist)
- Arbitrary-depth chain navigation and completion (e.g. `include.root.locals.env_config.locals.environment`)
- Deep object key navigation and completion (e.g. `local.config.network.vpc_cidr` navigates into nested objects, including cross-file)
- Find usages from object key definitions (Ctrl+B on key → finds all `local.X.key` usages, including cross-file)
- Duplicate block name inspection
- Label count inspection (missing labels, empty labels, extra labels)
- Parser error recovery tests and label edge case tests
- Architecture documentation (`docs/architecture.md`)
- Focused example projects (`examples/`)
- Settings page (Languages & Frameworks → Terragrunt) for custom entry point filenames and marker filenames (`--config` support)
- Comment-based inspection suppression (`# noinspection TerragruntUnresolvedPath`) — committable to source control
- Auto-close quotes when typing `"`

### Changed
- All inspections now use `GENERIC_ERROR_OR_WARNING` (respects user severity settings)
- Label count inspection highlights only the offending label, not the entire node
- Restructured examples into focused subdirectories (basic, cross-file, function-paths, stack, inspections)

### Fixed
- Crash when directories are moved/deleted externally (e.g. `git mv`) while IDE is open
- `.terraform.lock.hcl` no longer claimed as a Terragrunt file
- Parser error with quoted keys in multi-line objects (e.g. `merge({"key1" = "val1"\n "key2" = "val2"})`)
- Unresolved path warning suppressed for `.terragrunt-stack/` paths when `terragrunt.stack.hcl` defines the unit

## [0.2.0] - 2024-05-24

### Added
- Cross-file navigation: `include.X.locals.Y`, `include.X.inputs.Y`
- Deep navigation through aliases and `read_terragrunt_config()`
- Cross-file find-usages (direct, aliased, read_terragrunt_config references)
- `feature.X.value` navigation to default attribute
- Find usages from feature/dependency block labels
- Unknown attribute inspection
- Improved code folding (shows block type + label in placeholder)
- Live template context filtering (top-level only)
- No block suggestions inside map values (`inputs = {}`)

### Fixed
- Live template indent (closing brace no longer indented)
- Completion inside `inputs = {}` no longer suggests blocks

## [0.1.1] - 2024-05-22

### Added
- `dependency.X.outputs.Y` navigation to mock_outputs
- `include.X.inputs` alias completion
- For-expression completion (loop variable completion)

### Fixed
- File type detection for `.hcl` files in Terragrunt projects

## [0.1.0] - 2024-05-21

### Added
- Standalone HCL parser (JFlex + Grammar-Kit)
- Syntax highlighting (keywords, strings, numbers, comments, operators, interpolation)
- Code completion (blocks, attributes, 60+ functions, dot-completion)
- 6 inspections (unknown block, missing required, deprecated, unresolved path, unresolved variable)
- Navigation (local.X, dependency.X, feature.X, include/dependency file paths)
- Find usages from locals definitions
- Quick-fix for missing required attributes
- Rename refactoring for local variables
- Formatter (2-space indent)
- Live templates (dep, inc, gen, feat, loc, inp)
- Documentation popup (Ctrl+Q)
- String interpolation support
- Code folding, brace matching, commenter
- File type detection
- GitHub Actions CI and release workflows
