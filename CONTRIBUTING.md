# Contributing

Thanks for your interest in contributing to the Terragrunt HCL plugin!

## Development Setup

**Requirements:**
- JDK 21
- IntelliJ IDEA (recommended for plugin development)

**Build:**
```bash
./gradlew buildPlugin
```

**Run a sandboxed IDE with the plugin:**
```bash
./gradlew runIde
```

**Run tests:**
```bash
./gradlew test
```

## Making Changes

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and add tests
4. Run the full test suite: `./gradlew test`
5. Commit with a signed commit: `git commit -S -m "Description"`
6. Push and open a pull request

## Guidelines

- All PRs require CI to pass (build + test + CodeQL)
- Add tests for new features and bug fixes
- Update `CHANGELOG.md` under `[Unreleased]` when adding features or fixing bugs
- Update `README.md` if the change affects user-facing features
- Follow existing code style and patterns
- Keep commits focused — one logical change per commit

## Project Structure

```
src/main/java/com/github/terrapaw/terragrunt/
├── lang/           Language, FileType, Lexer, Parser, PSI
├── highlight/      Syntax highlighting, color settings
├── editor/         Brace matching, folding, formatter, structure view, breadcrumbs
├── schema/         Block/attribute/function definitions
├── completion/     Code completion
├── inspection/     Inspections and quick-fixes
├── reference/      Navigation, find usages, file resolution
├── refactor/       Rename refactoring
├── run/            Run configurations, gutter markers
├── toolwindow/     Dependency tree tool window
└── settings/       Plugin settings
```

## Architecture

See [docs/architecture.md](docs/architecture.md) for details on the lexer design, grammar, and key decisions.

## Reporting Issues

- Use GitHub Issues for bugs and feature requests
- For security vulnerabilities, see [SECURITY.md](SECURITY.md)
