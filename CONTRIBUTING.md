# Contributing to AgentShell

Thank you for your interest in contributing to AgentShell! This document provides guidelines and instructions for contributing code, documentation, and feedback.

## Code of Conduct

We are committed to fostering an inclusive community. Please read and adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) (link: coming soon).

## Getting Started

### Prerequisites

- **JDK 21** (for agent-core development)
- **JDK 17** (for android-app development)
- **Kotlin 1.9.24**
- **Gradle 8.7** (included via gradlew)
- **Git**

### Setting Up Your Development Environment

```bash
# Clone the repository
git clone https://github.com/OlehHavrilko/AgentShell
cd AgentShell

# Grant execute permissions to gradlew
chmod +x gradlew

# Run all tests to ensure setup is correct
./gradlew test

# Build the project
./gradlew build
```

### Project Structure

```
AgentShell/
├── agent-core/              # Core agent library (Kotlin/JVM)
│   ├── src/main/kotlin/     # Source code
│   └── src/test/kotlin/     # Tests
├── android-app/             # Android application
├── .github/workflows/       # CI/CD pipelines
├── scripts/                 # Build and utility scripts
├── ROADMAP.md              # 5-phase production readiness plan
├── PROGRESS.md             # Implementation status tracking
└── docs/                   # Documentation (coming)
```

## Development Workflow

### 1. Check the Roadmap & Progress

- Review [ROADMAP.md](ROADMAP.md) to understand the current phase priorities
- Check [PROGRESS.md](PROGRESS.md) to see what's already in progress
- Claim a task in the issue tracker before starting work

### 2. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-name
```

**Branch naming conventions:**
- `feature/...` — new features
- `fix/...` — bug fixes
- `docs/...` — documentation
- `test/...` — test improvements
- `perf/...` — performance optimizations

### 3. Write Code

#### Code Style
- Follow Kotlin official code style: https://kotlinlang.org/docs/coding-conventions.html
- Use `./gradlew detekt` for static analysis (coming in Phase 3)
- Aim for clear, self-documenting code with comments for complex logic

#### Testing
- Write unit tests for new functionality
- Aim for >70% code coverage
- Use `MockK` for mocking in tests
- Run tests frequently: `./gradlew test`

#### Commit Messages
Follow conventional commits format:
```
type(scope): description

[optional body]

[optional footer]
```

**Examples:**
- `feat(approval): add rate limiting`
- `fix(runtime): handle timeout in retry loop`
- `docs(readme): add installation instructions`
- `test(executor): add edge case tests`

**Types:**
- `feat` — new feature
- `fix` — bug fix
- `docs` — documentation
- `test` — test improvements
- `perf` — performance optimization
- `refactor` — code refactoring (no functionality change)
- `chore` — tooling, dependencies, CI/CD

### 4. Push and Create a Pull Request

```bash
git push origin feature/your-feature-name
```

Then create a PR on GitHub with:
- Clear title (use conventional commits format)
- Description of changes
- Link to related issues
- Checklist of testing performed

**PR Title Examples:**
- `feat(health-check): add liveness and readiness endpoints`
- `fix(retry): prevent infinite retry loops on 4xx errors`
- `docs: add deployment guide for Kubernetes`

### 5. Code Review

- Address all feedback from reviewers
- Push updates to the same branch (don't create new PRs)
- Re-request review after making changes
- Maintainers will merge when approved

## Testing Guidelines

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test module
./gradlew :agent-core:test

# Run specific test class
./gradlew test --tests "*AgentRunnerTest*"

# Run with coverage report (coming in Phase 4)
./gradlew test jacoco
```

### Writing Tests

- Place tests in `src/test/kotlin/` matching source package structure
- Use JUnit 5 (`@Test` annotation)
- Use MockK for mocking: `every { ... } returns ...`
- Test both happy path and error cases
- Aim for descriptive test names: `fun testToolExecutionRetriesOn5xx() { ... }`

### Test Coverage

We aim for **≥70% code coverage** (enforced in Phase 4). Coverage reports:
```bash
./gradlew jacocoTestReport
# View report in build/reports/jacoco/test/html/index.html
```

## Documentation

### Updating Documentation

- Update [PROGRESS.md](PROGRESS.md) when completing tasks
- Add/update docs in `docs/` directory for new features
- Keep README.md in sync with major changes
- Use clear headings and examples

### Writing Good Documentation

1. **Installation docs:** step-by-step instructions for all platforms
2. **API docs:** endpoint, parameters, responses, examples
3. **Architecture docs:** why decisions were made, trade-offs considered
4. **Troubleshooting:** common issues and solutions

## Reporting Issues

### Bug Reports

Use the [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md):
- Clear, descriptive title
- Steps to reproduce
- Expected vs actual behavior
- Environment (OS, Java version, etc.)
- Logs/screenshots if applicable

### Feature Requests

Use the [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md):
- Clear description of the feature
- Use case / problem it solves
- Proposed solution (optional)
- Acceptance criteria

## Performance Considerations

- Avoid unnecessary allocations in hot paths
- Use `val` instead of `var` when possible
- Consider memory usage for large data structures
- Profile before optimizing: `./gradlew jmh` (coming in Phase 4)

## Security

- Never commit secrets (API keys, passwords)
- Use `SecretsVault` for runtime secret masking
- Follow principle of least privilege
- Report security issues privately to maintainers (see SECURITY.md coming in Phase 4)

## Release Process

TBD: Coming in Phase 4 with release automation.

## Getting Help

- **GitHub Issues:** Ask questions with `help wanted` label
- **Discussions:** Join community Q&A (coming soon)
- **Code Review:** Ask for early feedback before opening formal PR

## License

By contributing, you agree that your contributions will be licensed under the [Apache 2.0 License](LICENSE).

---

Thank you for making AgentShell better! 🚀
