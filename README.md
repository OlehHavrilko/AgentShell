# AgentShell

> 📊 **[Текущее состояние проекта (STATUS.md)](STATUS.md)** — полная информация о завершённом Sprint 10

## Multi-module Structure

This is a multi-module Gradle project:

| Module | Description |
|--------|-------------|
| `:agent-core` | Pure Kotlin/JVM library — all business logic (orchestrator, LLM gateways, executors, memory, state, rules, MCP) |
| `:android-app` | Android application — Compose UI, foreground service, Room DB, ViewModel layer |

### Building

```bash
# Build the CLI fat JAR (runs on any JVM)
./gradlew :agent-core:shadowJar
# Output: agent-core/build/libs/agent-core-1.0.0-all.jar

# Build the Android APK
./gradlew :android-app:assembleRelease
# Output: android-app/build/outputs/apk/release/android-app-release.apk

# Build the Alpine Linux rootfs for the Proot sandbox (requires Docker)
./scripts/build_rootfs.sh
# Output: android-app/src/main/assets/rootfs.tar.gz
```

---

A resume-first, approval-gated agent runtime for safe autonomous tool execution on Android/JVM.

Supports scripted YAML pipelines **and** fully autonomous LLM-driven agentic loops with MCP tool integration.

## Quickstart

```bash
git clone https://github.com/OlehHavrilko/AgentShell
cd AgentShell
./scripts/demo.sh        # installs Ollama, pulls model, runs project-scan preset
# open http://localhost:9090/ui for the Web UI dashboard
```

Or with Docker:

```bash
docker compose up        # builds fat JAR, starts agent + web UI on port 9090
```

## Agent Presets

Built-in presets for common tasks — no prompt engineering needed:

| Preset | Description |
|--------|-------------|
| `code-review` | `git diff HEAD~1`, analyses changed files, outputs inline review |
| `git-workflow` | writes conventional-commit message + PR description |
| `project-scan` | scans for TODOs/FIXMEs, writes `REPORT.md` |

```bash
# Run a preset
./gradlew run --args="--agent dev-bot --preset code-review --provider ollama"

# Override goal
./gradlew run --args="--agent dev-bot --preset git-workflow --goal 'Write PR for feature/auth' --provider openai"
```

## Architecture

```
Main (CLI)
  ├─ AgentRunner              — scripted & agentic execution loop
  │    ├─ AgentRuntime        — start / resume decision
  │    ├─ RuleEngine          — YAML-configurable risk rule evaluation
  │    ├─ RiskScorer          — 0-100 threat score per tool call
  │    ├─ ApprovalGate        — human sign-off with TTL auto-reject
  │    ├─ IdempotencyService  — duplicate-call prevention (TTL cache)
  │    ├─ HeartbeatService    — background liveness updates
  │    ├─ WatchdogService     — detects stuck runs, marks them CRASHED
  │    ├─ ContextBudgetManager— token counting, message trimming
  │    ├─ ToolDispatcher      — routes calls to executors
  │    │    ├─ SchemaValidator     — JSON args validation
  │    │    ├─ SandboxGuard        — path / network / output enforcement
  │    │    ├─ ShellToolExecutor   — shell commands via ProcessBuilder
  │    │    ├─ FileToolExecutor    — read / write / append files
  │    │    ├─ GitToolExecutor     — git subcommands
  │    │    └─ McpToolExecutor     — MCP stdio server tools (dynamic)
  │    └─ StateStore (interface)
  │         ├─ InMemoryStateStore  — tests / ephemeral use
  │         └─ SqliteStateStore    — production, WAL SQLite
  ├─ LlmGateway (interface)
  │    ├─ OpenAiGateway       — OpenAI API (GPT-4o, o1, …)
  │    ├─ OllamaGateway       — local models via /api/chat
  │    ├─ OpenRouterGateway   — 200+ models via openrouter.ai
  │    └─ GeminiGateway       — Google Gemini (native REST)
  ├─ McpClient                — stdio JSON-RPC 2.0 (tools/list, tools/call)
  ├─ ApprovalServer           — embedded HTTP approval API
  ├─ AuditTrail               — full SQLite audit log (masked secrets)
  ├─ SecretsVault             — runtime secret masking for logs & audit
  └─ WorkflowLoader           — YAML pipeline loader
```

## How it works

1. **Start or Resume** — if a previous run crashed, the runtime resumes from the last saved checkpoint.
2. **Risk rules** — every tool call is evaluated against `risk-rules.yaml`. First matching rule wins: can set a score, add to it, require approval, or block the call entirely.
3. **Approval gate** — calls that exceed the risk threshold pause at `WAITING_APPROVAL`. Approve or reject via HTTP API.
4. **Agentic loop** — in `--agentic` mode, an LLM drives the loop: it receives tool results and decides what to call next, up to `--max-iterations`.
5. **MCP** — connect any stdio-based MCP server; its tools are registered dynamically and called the same way as built-in tools.
6. **Idempotency** — tool calls with an `idempotencyKey` are deduplicated.
7. **Heartbeat + Watchdog** — a daemon heartbeat thread updates `heartbeatMs` every 10 s. The watchdog marks runs CRASHED if heartbeat ages past 2 minutes.
8. **Secrets masking** — `SecretsVault` masks API keys in all log output and audit events before persisting to SQLite.
9. **Persistence** — `SqliteStateStore` persists runs, checkpoints, and audit events across restarts.

## Build & test

```bash
./gradlew test          # run all tests
./gradlew build         # compile + test
```

## Run

### Scripted mode (YAML workflow)

```bash
./gradlew run --args="--agent ci-bot --workflow workflows/deploy.yaml --approval-port 8080"
```

### Agentic mode (LLM-driven loop)

```bash
# OpenAI
OPENAI_API_KEY=sk-... ./gradlew run --args="--agent dev-bot --agentic --goal 'List all .kt files and count lines' --provider openai"

# Gemini
GEMINI_API_KEY=... ./gradlew run --args="--agent dev-bot --agentic --goal 'Summarise recent git log' --provider gemini --model gemini-2.0-flash"

# OpenRouter (Claude, Llama, Mistral, …)
OPENROUTER_API_KEY=... ./gradlew run --args="--agent dev-bot --agentic --goal 'Refactor Main.kt' --provider openrouter --model anthropic/claude-3-5-sonnet"

# Local Ollama
./gradlew run --args="--agent dev-bot --agentic --goal 'Run tests' --provider ollama --model qwen2.5-coder:7b"
```

### All CLI flags

| Flag | Default | Description |
|------|---------|-------------|
| `--agent <id>` | required | Agent identifier |
| `--db <path>` | `~/.agentshell/<id>.db` | SQLite database path |
| `--risk-threshold <0-100>` | `60` | Score at which approval is required |
| `--approval-ttl-ms <ms>` | `3600000` | Approval TTL (auto-reject after) |
| `--approval-port <port>` | — | Start embedded approval HTTP server |
| `--metrics-port <port>` | — | Start metrics/UI HTTP server |
| `--workflow <file>` | — | Load steps from YAML/JSON workflow file |
| `--preset <name>` | — | Use a built-in agent preset (`code-review`, `git-workflow`, `project-scan`) |
| `--demo` | — | Run a single harmless demo step |
| `--agentic` | — | Enable LLM-driven agentic loop |
| `--goal <text>` | — | Goal description for agentic mode |
| `--provider <name>` | `openai` | LLM provider: `openai`, `ollama`, `openrouter`, `gemini` |
| `--model <name>` | provider default | Model to use |
| `--ollama-url <url>` | `http://localhost:11434` | Ollama base URL |
| `--max-iterations <n>` | `20` | Max agentic loop iterations |

## LLM providers

| Provider | Env var | Default model |
|----------|---------|---------------|
| `openai` | `OPENAI_API_KEY` | `gpt-4o` |
| `ollama` | — | `qwen2.5-coder:7b` |
| `openrouter` | `OPENROUTER_API_KEY` | `anthropic/claude-3-5-sonnet` |
| `gemini` | `GEMINI_API_KEY` | `gemini-2.0-flash` |

## MCP integration

Connect an MCP-compatible stdio server; its tools are registered dynamically:

```kotlin
val mcpClient = McpClient(command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"))
val mcpExecutor = McpToolExecutor(mcpClient)
dispatcher.register(mcpExecutor)
```

## Risk rules (YAML)

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

## Web UI & Observability

Start with `--metrics-port 9090`.

```bash
./gradlew run --args="--agent dev-bot --preset project-scan --provider ollama --metrics-port 9090"
# open http://localhost:9090/ui
```

| Endpoint | Description |
|----------|-------------|
| `GET /ui` | Dark-theme dashboard (Runs / Approvals / Metrics tabs) |
| `GET /runs` | JSON list of all runs |
| `GET /report/{runId}` | Full JSON timeline for a run |
| `GET /metrics` | Prometheus text metrics |
| `GET /health` | Liveness check |

## Approval HTTP API

Start with `--approval-port 8080`.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Liveness check |
| `/approvals` | GET | List pending requests |
| `/approvals/{id}/approve` | POST | Approve a request |
| `/approvals/{id}/reject` | POST | Reject a request |

## Sandbox policies

| Policy | Network | Paths |
|--------|---------|-------|
| `shell_default` | ❌ | `$REPO_ROOT/**`, `$HOME/.config/agentshell/**` |
| `git_default` | ✅ github.com, gitlab.com, bitbucket.org | same as above |

Override `agentshell.repoRoot` system property to set the repo root path.

## Tool contracts

| Tool name | Executor | Args |
|-----------|----------|------|
| `shell_exec` | `ShellToolExecutor` | `{"command":"…", "workingDir":"…"}` |
| `file_write` | `FileToolExecutor` | `{"operation":"read\|write\|append","path":"…","content":"…"}` |
| `git_exec` | `GitToolExecutor` | `{"subcommand":"status\|log\|push\|…","args":[…],"workingDir":"…"}` |

## Run lifecycle

```
CREATED → QUEUED → RUNNING → COMPLETED
                           → FAILED
                           → WAITING_APPROVAL → (approved) → RUNNING
                           → CRASHED → (resume) → RUNNING
```

Crashed runs are auto-detected by `WatchdogService` (heartbeat age > 2 min).

## Logs

Logs are written to `logs/agentshell.log` (rolling, 7 days, 50 MB/file).  
Override log directory with `-Dagentshell.logDir=/your/path`.

