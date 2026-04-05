# AgentShell

A resume-first, approval-gated agent runtime for safe autonomous tool execution.

## Architecture

```
Main (CLI)
  └─ AgentRunner          — full execution loop
       ├─ AgentRuntime    — start / resume decision
       ├─ RiskScorer      — 0-100 threat score per tool call
       ├─ ApprovalGate    — human sign-off with TTL auto-reject
       ├─ IdempotencyService — duplicate-call prevention (TTL cache)
       ├─ HeartbeatService — background liveness updates
       ├─ ToolDispatcher  — routes calls to executors
       │    ├─ SchemaValidator   — JSON args validation
       │    ├─ SandboxGuard      — path / network / output enforcement
       │    ├─ ShellToolExecutor — shell commands via ProcessBuilder
       │    ├─ FileToolExecutor  — read / write / append files
       │    └─ GitToolExecutor   — git subcommands
       └─ StateStore (interface)
            ├─ InMemoryStateStore  — tests / ephemeral use
            └─ SqliteStateStore    — production, survives crashes
```

## How it works

1. **Start or Resume** — if a previous run crashed or was interrupted, the runtime resumes from the last saved checkpoint (step N → continues from N+1).
2. **Risk scoring** — every tool call is scored 0-100. Calls that exceed the threshold pause the run at `WAITING_APPROVAL`.
3. **Idempotency** — tool calls with an `idempotencyKey` are deduplicated; a cached result is returned instead of re-executing.
4. **Retry** — retryable errors (timeouts, provider unavailable) are retried with exponential backoff (500 → 1000 → 2000 ms).
5. **Heartbeat** — a daemon thread updates the run's `heartbeatMs` every 10 s so stale/crashed runs can be detected.
6. **Persistence** — `SqliteStateStore` persists runs, checkpoints, and schema versions across restarts using WAL-mode SQLite.

## Build & test

```bash
./gradlew test          # run all tests
./gradlew build         # compile + test
```

## Run

```bash
./gradlew run --args="--agent <id> [options]"
```

| Flag | Default | Description |
|------|---------|-------------|
| `--agent <id>` | required | Agent identifier |
| `--db <path>` | `~/.agentshell/<id>.db` | SQLite database path |
| `--risk-threshold <0-100>` | `60` | Score at which approval is required |
| `--approval-ttl-ms <ms>` | `3600000` | Approval request TTL (auto-reject after) |
| `--demo` | — | Run a single harmless demo step |

### Example

```bash
# Demo run
./gradlew run --args="--agent my-agent --demo"

# Custom risk threshold
./gradlew run --args="--agent ci-bot --risk-threshold 80 --db /var/db/agentshell.db"
```

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
| `git_push` | `GitToolExecutor` | `{"subcommand":"status\|log\|push\|…","args":[…],"workingDir":"…"}` |

## Run lifecycle

```
CREATED → QUEUED → RUNNING → COMPLETED
                           → FAILED
                           → WAITING_APPROVAL → (approved) → RUNNING
                           → CRASHED → (resume) → RUNNING
```

## Logs

Logs are written to `logs/agentshell.log` (rolling, 7 days, 50 MB/file).
Override log directory with `-Dagentshell.logDir=/your/path`.

