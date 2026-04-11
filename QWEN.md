# AgentShell — Project Context

## Project Overview

**AgentShell** is a resume-first, approval-gated agent runtime for safe autonomous tool execution on Android and JVM platforms. It supports both scripted YAML pipelines and fully autonomous LLM-driven agentic loops with MCP (Model Context Protocol) tool integration.

This is a **multi-module Gradle** project written primarily in **Kotlin**, targeting two platforms:

| Module | Description |
|--------|-------------|
| `:agent-core` | Pure Kotlin/JVM library — all business logic (orchestrator, LLM gateways, executors, memory, state, rules, MCP) |
| `:android-app` | Android application — Compose UI, foreground service, Room DB, ViewModel layer |

### Architecture Highlights

- **Agentic mode**: LLM-driven autonomous loop with goal-based task execution
- **Scripted mode**: YAML-defined workflow pipelines
- **Risk-gated execution**: Every tool call is evaluated against configurable YAML risk rules; high-risk calls require human approval
- **Multi-provider LLM support**: OpenAI, Ollama (local), OpenRouter, Gemini
- **MCP integration**: Dynamic tool registration from stdio-based MCP servers
- **Persistence**: SQLite-backed state store with checkpoint/resume capability
- **Audit trail**: Full SQLite audit log with secret masking
- **Web UI**: Dark-theme dashboard at `--metrics-port 9090` for monitoring runs, approvals, and metrics
- **Docker-ready**: Multi-stage Dockerfile with Alpine runtime

### Package Structure (`agent-core`)

```
dev.agentshell
├── approval/       — Approval gate (human sign-off with TTL auto-reject)
├── audit/          — SQLite audit trail (masked secrets)
├── domain/         — Core domain models (Run, ToolCall, etc.)
├── executor/       — Tool executors (shell, file, git, MCP)
├── llm/            — LLM provider gateways (OpenAI, Ollama, OpenRouter, Gemini)
├── mcp/            — MCP stdio client (JSON-RPC 2.0)
├── memory/         — Context/memory management
├── observability/  — Metrics, heartbeat, watchdog services
├── orchestrator/   — Main execution orchestrizer
├── report/         — Reporting utilities
├── rules/          — YAML risk rule engine + risk scorers
├── runtime/        — Agent runtime (start/resume decision)
├── security/       — Secrets vault, sandbox guard, schema validator
├── state/          — State store (in-memory + SQLite implementations)
└── workflow/       — YAML pipeline loader
```

## Technologies & Dependencies

- **Kotlin 1.9.24** with JVM target (Java 21 for agent-core, Java 17 for android-app)
- **Gradle 8.7** (Kotlin DSL)
- **Coroutines** (`kotlinx-coroutines-core:1.8.0`)
- **Serialization** (`kotlinx-serialization-json:1.6.3`, `kaml:0.59.0` for YAML, `SnakeYAML`, `Moshi`)
- **SQLite** (`sqlite-jdbc:3.45.3.0`)
- **HTTP** (`OkHttp:4.12.0`)
- **Logging** (`SLF4J 2.0.13`, `Logback 1.5.6`)
- **Testing** (`JUnit 5.10.2`, `MockK 1.13.10`, `OkHttp MockWebServer`)
- **Android**: Compose, Material3, Room, ViewModel, WorkManager, DataStore

## Building & Running

### Prerequisites

- JDK 21 (for agent-core)
- JDK 17 / Android SDK (for android-app)
- Docker (optional, for containerized runtime or rootfs build)

### Commands

```bash
# Run all tests
./gradlew test

# Build everything (compile + test)
./gradlew build

# Build the CLI fat JAR (runs on any JVM with Java 21)
./gradlew :agent-core:shadowJar
# Output: agent-core/build/libs/agent-core-1.0.0-all.jar

# Build the Android APK
./gradlew :android-app:assembleRelease

# Build the Alpine Linux rootfs for Proot sandbox (requires Docker)
./scripts/build_rootfs.sh

# Docker: build and run the agent + web UI on port 9090
docker compose up
```

### Running the Agent

**Agentic mode** (LLM-driven):
```bash
# OpenAI
OPENAI_API_KEY=sk-... ./gradlew run --args="--agent dev-bot --agentic --goal 'List all .kt files' --provider openai"

# Local Ollama
./gradlew run --args="--agent dev-bot --agentic --goal 'Run tests' --provider ollama --model qwen2.5-coder:7b"

# OpenRouter
OPENROUTER_API_KEY=... ./gradlew run --args="--agent dev-bot --agentic --goal 'Refactor Main.kt' --provider openrouter"

# Gemini
GEMINI_API_KEY=... ./gradlew run --args="--agent dev-bot --agentic --goal 'Summarise git log' --provider gemini"
```

**Scripted mode** (YAML workflow):
```bash
./gradlew run --args="--agent ci-bot --workflow workflows/deploy.yaml --approval-port 8080"
```

**Built-in presets**:
```bash
./gradlew run --args="--agent dev-bot --preset code-review --provider ollama"
./gradlew run --args="--agent dev-bot --preset git-workflow --provider openai"
./gradlew run --args="--agent dev-bot --preset project-scan --provider ollama"
```

**Quick demo** (installs Ollama, pulls model, runs project-scan preset):
```bash
./scripts/demo.sh
# Open http://localhost:9090/ui
```

### Key CLI Flags

| Flag | Default | Description |
|------|---------|-------------|
| `--agent <id>` | required | Agent identifier |
| `--db <path>` | `~/.agentshell/<id>.db` | SQLite database path |
| `--risk-threshold <0-100>` | `60` | Score at which approval is required |
| `--approval-port <port>` | — | Start embedded approval HTTP server |
| `--metrics-port <port>` | — | Start metrics/UI HTTP server |
| `--agentic` | — | Enable LLM-driven agentic loop |
| `--goal <text>` | — | Goal description for agentic mode |
| `--provider <name>` | `openai` | LLM provider: `openai`, `ollama`, `openrouter`, `gemini` |
| `--model <name>` | provider default | Model to use |
| `--max-iterations <n>` | `20` | Max agentic loop iterations |
| `--preset <name>` | — | Built-in preset: `code-review`, `git-workflow`, `project-scan` |

## Development Conventions

- **Kotlin code style**: official (`kotlin.code.style=official` in `gradle.properties`)
- **Testing**: JUnit 5 with MockK for mocking; all tests run via `./gradlew test`
- **CI/CD**: GitHub Actions (`.github/workflows/ci.yml`) — builds, tests against local Ollama service, uploads test reports and fat JAR artifact
- **Logging**: Rolling file logs to `logs/agentshell.log` (7 days retention, 50 MB/file); override via `-Dagentshell.logDir`
- **Gradle JVM args**: `-Xmx1g -Dfile.encoding=UTF-8`

## Run Lifecycle States

```
CREATED → QUEUED → RUNNING → COMPLETED
                        → FAILED
                        → WAITING_APPROVAL → (approved) → RUNNING
                        → CRASHED → (resume) → RUNNING
```

Crashed runs are auto-detected by `WatchdogService` (heartbeat age > 2 min).

## Built-in Tools

| Tool name | Executor | Args |
|-----------|----------|------|
| `shell_exec` | `ShellToolExecutor` | `{"command":"…", "workingDir":"…"}` |
| `file_write` | `FileToolExecutor` | `{"operation":"read\|write\|append","path":"…","content":"…"}` |
| `git_exec` | `GitToolExecutor` | `{"subcommand":"status\|log\|push\|…","args":[…],"workingDir":"…"}` |
| MCP tools | `McpToolExecutor` | Dynamically registered from stdio MCP servers |

## Sandbox Policies

| Policy | Network | Paths |
|--------|---------|-------|
| `shell_default` | ❌ | `$REPO_ROOT/**`, `$HOME/.config/agentshell/**` |
| `git_default` | ✅ github.com, gitlab.com, bitbucket.org | same as above |

Override `agentshell.repoRoot` system property to set the repo root path.

## HTTP Endpoints

**Metrics/UI** (`--metrics-port 9090`):

| Endpoint | Description |
|----------|-------------|
| `GET /ui` | Dark-theme dashboard (Runs / Approvals / Metrics tabs) |
| `GET /runs` | JSON list of all runs |
| `GET /report/{runId}` | Full JSON timeline for a run |
| `GET /metrics` | Prometheus text metrics |
| `GET /health` | Liveness check |

**Approval API** (`--approval-port 8080`):

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Liveness check |
| `/approvals` | GET | List pending requests |
| `/approvals/{id}/approve` | POST | Approve a request |
| `/approvals/{id}/reject` | POST | Reject a request |

## LLM Providers

| Provider | Env var | Default model |
|----------|---------|---------------|
| `openai` | `OPENAI_API_KEY` | `gpt-4o` |
| `ollama` | — | `qwen2.5-coder:7b` |
| `openrouter` | `OPENROUTER_API_KEY` | `anthropic/claude-3-5-sonnet` |
| `gemini` | `GEMINI_API_KEY` | `gemini-2.0-flash` |

## Risk Rules (YAML)

Edit `src/main/resources/rules/risk-rules.yaml` (or pass a custom path) to configure risk behaviour declaratively:

```yaml
defaultScore: 30

rules:
  - name: block-rm-rf
    pattern: "rm\\s+-[a-zA-Z]*r[a-zA-Z]*f"
    block: true

  - name: require-approval-git-push
    tool: git_exec
    pattern: "push"
    score: 80
    requireApproval: true

  - name: high-risk-sudo
    pattern: "\\bsudo\\b"
    score: 80
```

Rule fields: `tool` (exact match), `pattern` (regex on args JSON), `score` (set), `addScore` (delta), `requireApproval`, `block`.

## Agent Presets

| Preset | Description |
|--------|-------------|
| `code-review` | `git diff HEAD~1`, analyses changed files, outputs inline review |
| `git-workflow` | writes conventional-commit message + PR description |
| `project-scan` | scans for TODOs/FIXMEs, writes `REPORT.md` |

## Qwen Added Memories
- Git remote: https://github.com/OlehHavrilko/AgentShell.git — authenticated as OlehHavrilko with Personal Access Token (ghp_). Use `git remote set-url origin https://OlehHavrilko:<TOKEN>@github.com/OlehHavrilko/AgentShell.git` before push.
