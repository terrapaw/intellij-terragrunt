# Changelog

## [Unreleased]

### Added
- Function-aware path resolution in includes and read_terragrunt_config
  - `get_terragrunt_dir()`, `get_root_terragrunt_dir()`
  - `get_parent_terragrunt_dir()` with include semantics (returns own dir for parent configs, resolves include for children, supports named includes)
  - `get_repo_root()`, `get_path_to_repo_root()`, `get_path_from_repo_root()`
  - `find_in_parent_folders()` inside interpolation (with fallback arg support)
  - `dirname()`, `basename()` (nested evaluation)
- Stack context resolution: `read_terragrunt_config(find_in_parent_folders(...))` resolves from includer directories (non-entry-point files won't guess — shows "no declaration" until includers exist)
- Arbitrary-depth chain navigation and completion (e.g. `include.root.locals.env_config.locals.environment`)
- Duplicate block name inspection
- Label count inspection (missing labels, empty labels, extra labels)
- Parser error recovery tests and label edge case tests
- Architecture documentation (`docs/architecture.md`)
- Focused example projects (`examples/`)
- Settings page (Languages & Frameworks → Terragrunt) for custom entry point filenames (`--config` support)

### Changed
- All inspections now use `GENERIC_ERROR_OR_WARNING` (respects user severity settings)
- Label count inspection highlights only the offending label, not the entire node
- Restructured examples into focused subdirectories (basic, cross-file, function-paths, stack, inspections)

### Fixed
- Crash when directories are moved/deleted externally (e.g. `git mv`) while IDE is open
- `.terraform.lock.hcl` no longer claimed as a Terragrunt file

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
