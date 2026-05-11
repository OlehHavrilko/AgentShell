# Architecture Overview

This document provides a high-level overview of AgentShell's architecture and design decisions.

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Core Modules](#core-modules)
3. [Design Patterns](#design-patterns)
4. [Data Flow](#data-flow)
5. [Key Decisions](#key-decisions)

---

## System Architecture

AgentShell is organized as a multi-module Gradle project with a clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLI Entry Point                         │
│                           (Main.kt)                              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐   ┌─────────▼────────┐   ┌────────▼────────┐
│  AgentRunner   │   │   Orchestrator   │   │   WorkflowLoader│
│ (Scripted Mode)│   │  (Multi-agent)   │   │   (YAML Pipelines)
└────────────────┘   └──────────────────┘   └─────────────────┘
        │
        ├─► LLM Gateway (OpenAI, Ollama, Gemini, OpenRouter)
        │
        ├─► Tool Dispatcher
        │   ├─► ShellExecutor
        │   ├─► FileExecutor
        │   ├─► GitExecutor
        │   └─► MCPExecutor
        │
        ├─► Risk Scorer & RuleEngine
        │
        ├─► Approval Gate (HTTP API)
        │
        ├─► State Store (SQLite)
        │
        ├─► Audit Trail (SQLite)
        │
        └─► Support Services
            ├─► Heartbeat
            ├─► Watchdog
            ├─► Idempotency Service
            └─► Memory Store
```

### Execution Modes

**1. Agentic Mode** — LLM-driven autonomous execution
- User provides a goal
- Agent loops: [LLM completion] → [tool calls] → [results] → repeat until done
- Requires LLM provider (OpenAI, Ollama, etc.)

**2. Scripted Mode** — YAML-defined workflow
- Predefined sequence of steps
- Each step: LLM completion + tool execution (optional)
- Use case: CI/CD pipelines, repeatable workflows

**3. Orchestrate Mode** — Multi-agent coordination
- Orchestrator runs multiple agents sequentially/in parallel
- Each agent has own config and goal
- Enables complex workflows with dependencies

---

## Core Modules

### `agent-core` — Main JVM Library

#### `/approval`
- **ApprovalServer** — HTTP API for manual approvals
- **ApprovalRequest/Gate** — Data model and decision logic
- TTL-based auto-reject for expired requests

#### `/audit`
- **AuditTrail** interface — Contract for audit logging
- **SqliteAuditTrail** — SQLite implementation
- **AuditEvent** — Immutable event model
- Secret masking via SecretsVault

#### `/domain`
- **Run** — Execution run (with status, checkpoint)
- **ToolCall/ToolResult** — Execution models
- **ErrorCode** — Enumerated error types
- **RiskLevel** — HIGH/MEDIUM/LOW risk classification

#### `/executor`
- **ToolDispatcher** — Routes tool calls to executors
- **ShellToolExecutor** — ProcessBuilder-based shell execution
- **FileToolExecutor** — File operations (read/write/append)
- **GitToolExecutor** — Git command wrappers
- **MCPToolExecutor** — MCP stdio client
- **SandboxGuard** — Path/network/output validation

#### `/llm`
- **LlmGateway** interface — Provider abstraction
- **OpenAiGateway** — OpenAI API (GPT-4o, etc.)
- **OllamaGateway** — Ollama `/api/chat`
- **GeminiGateway** — Google Gemini REST API
- **OpenRouterGateway** — OpenRouter (200+ models)
- **ContextBudgetManager** — Token counting and trimming

#### `/mcp`
- **McpClient** — JSON-RPC 2.0 stdio client
- Connects to MCP servers and dynamically registers tools

#### `/memory`
- **MemoryStore** interface — Context/memory persistence
- **SqliteMemoryStore** — SQLite implementation
- **MemoryAugmentedGateway** — Wraps LLM gateway with memory

#### `/runtime`
- **AgentRunner** — Main execution orchestrator
- **AgentRuntime** — Start/resume decision logic
- **RiskScorer** — 0-100 threat score per tool call
- **ApprovalGate** — TTL-based approval queue
- **IdempotencyService** — Duplicate call prevention
- **HeartbeatService** — Background liveness
- **WatchdogService** — Detects stuck runs

#### `/rules`
- **RuleEngine** — YAML-based risk rule evaluation
- **RiskRule** — Pattern-matching on tool name/args

#### `/security`
- **SecretsVault** — Runtime secret masking
- **SandboxGuard** — Path/network enforcement

#### `/state`
- **StateStore** interface — Run persistence
- **SqliteStateStore** — WAL-mode SQLite implementation
- **InMemoryStateStore** — For testing

---

## Design Patterns

### 1. **Gateway Pattern** (LLM providers)
- All LLM providers implement `LlmGateway` interface
- New provider = new implementation + registration
- Enables swapping providers without code change

### 2. **Executor Pattern** (Tools)
- `ToolDispatcher` routes to executor implementations
- Each tool type: `ShellExecutor`, `FileExecutor`, `GitExecutor`, `MCPExecutor`
- Extensible: add new executor type easily

### 3. **Strategy Pattern** (Risk rules)
- `RuleEngine` evaluates YAML rules against tool call
- Rules are data, not code → no recompile needed
- Supports action strategies: score adjustment, approval, block

### 4. **Factory Pattern**
- `LlmGatewayFactory` — creates gateway instances
- `ExecutorFactory` — creates executor instances
- `StateStoreFactory` — selects persistence backend

### 5. **Observer Pattern**
- `AuditTrail` subscribes to execution events
- `HeartbeatService` periodically updates run state
- `WatchdogService` monitors for stuck runs

### 6. **Retry with Exponential Backoff** (Phase 1)
- Wraps external API calls (LLM, HTTP)
- Configurable retry count and backoff multiplier
- Circuit breaker for persistent failures

---

## Data Flow

### Agentic Execution Flow

```
1. User Input: goal + system prompt + config
                    │
                    ▼
2. Create Run in StateStore
                    │
                    ▼
3. LLM Completion Loop (max-iterations):
   a) Trim conversation to fit budget
   b) Send to LLM (with available tools)
   c) Parse response:
      - TOOL_USE → extract calls
      - END_TURN → finish
      - ERROR → handle gracefully
   d) For each tool call:
      - Score risk
      - If needs approval → pause & wait
      - Execute with retry
      - Append result to conversation
   e) Repeat
                    │
                    ▼
4. Update Run status (COMPLETED/FAILED/CRASHED)
                    │
                    ▼
5. Return RunOutcome with results
```

### Tool Execution Flow

```
Tool Call Input
    │
    ▼
SchemaValidator (JSON args validation)
    │
    ▼
RiskScorer (0-100 threat score)
    │
    ├─ If score >= threshold → ApprovalGate
    │   └─► Wait for manual approval/rejection
    │
    ▼
SandboxGuard (path/network/output policy check)
    │
    ├─ If policy violation → BLOCKED
    │
    ▼
ToolDispatcher Routes to Executor
    ├─► ShellExecutor   (shell_exec)
    ├─► FileExecutor    (file_read, file_write)
    ├─► GitExecutor     (git_commit, git_push)
    └─► MCPExecutor     (dynamic MCP tools)
    │
    ▼
executeWithRetry (exponential backoff)
    │
    ├─ On success → ToolResult (success=true)
    ├─ On retryable error → retry
    └─ On fatal error → ToolResult (success=false, errorCode)
    │
    ▼
AuditTrail.record (with secret masking)
    │
    ▼
ToolResult returned to agent
```

---

## Key Decisions

### 1. **Why SQLite (not PostgreSQL) in MVP?**
- Simpler deployment (no external DB)
- WAL mode enables concurrent reads
- Phase 5 migration to PostgreSQL for scaling
- Trade-off: single-machine limit

### 2. **Why Kotlin (not Go/Python/Rust)?**
- JVM ecosystem maturity
- Strong type system
- Coroutines for async (future use)
- Existing experience base

### 3. **Why resume-first architecture?**
- Approval gate pauses → must resume
- Timeouts and crashes must recover
- Checkpoint/replay pattern ensures consistency
- Cost optimization: don't restart from 0

### 4. **Why MCP (Model Context Protocol)?**
- Standardized tool interface (Anthropic)
- Decouples agents from tool implementations
- Enables dynamic tool registration
- Future: integrate with ecosystem

### 5. **Why YAML for rules (not JSON)?**
- More human-readable
- Comments supported
- Less syntax-heavy

### 6. **Why risk scoring + approval instead of allowlist?**
- Allowlists: fragile, miss edge cases
- Risk scoring: data-driven decisions
- Approval gate: human override when needed

---

## Future Improvements

See [ROADMAP.md](../ROADMAP.md) for planned improvements in Phases 1-5.

---

## Related Documents

- [ROADMAP.md](../ROADMAP.md) — 5-phase production plan
- [PROGRESS.md](../PROGRESS.md) — Implementation tracking
- [CONTRIBUTING.md](../CONTRIBUTING.md) — Developer guidelines
- [Architecture Decision Records](./adr/) — Detailed decisions
