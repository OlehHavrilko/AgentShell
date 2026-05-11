# ADR-001: Resume-First Architecture

**Date:** May 11, 2026  
**Status:** Accepted  
**Context:** AgentShell must handle long-running agent operations that may be interrupted (approval gates, timeouts, crashes).

## Problem

When an agent execution is interrupted (approval gate, crash, timeout), how should the system resume?

### Options Considered

1. **Restart from scratch** — simplest, but wasteful (cost, latency)
2. **Resume from last checkpoint** — maintains progress, reduces cost
3. **Time-travel debugging** — record all state, replay from any point (overkill for MVP)

## Decision

**Implement resume-first architecture** with checkpoints and state persistence.

### Implementation

- Every run has a `Checkpoint(stepIndex, contextSummary, artifactRefs)`
- Saved to StateStore after each major milestone
- On restart: `AgentRuntime.startOrResume()` decides whether to create new run or resume existing
- Idempotency keys prevent duplicate tool executions

### Benefits

- ✅ Cost-efficient (resume rather than restart)
- ✅ Better UX (user sees progress preserved)
- ✅ Approval gate support (pause → approve → resume)
- ✅ Crash recovery (detect stale runs, mark CRASHED)

### Trade-offs

- ❌ More complex state management
- ❌ Potential for stale data if checkpoint not saved atomically
- ❌ Requires careful testing of recovery scenarios

## Consequences

- **Positive:**
  - Enables approval gates to pause/resume
  - Reduces re-execution cost
  - Better resilience

- **Negative:**
  - More database operations
  - Need recovery mechanism for dangling approvals
  - Testing complexity increases

## Related Decisions

- ADR-002: Approval gate with TTL
- ADR-003: SQLite with WAL mode

---

# ADR-002: Approval Gate with TTL

**Date:** May 11, 2026  
**Status:** Accepted  
**Context:** High-risk tool calls (deletions, pushes) need manual approval, but shouldn't block indefinitely.

## Problem

- Some tool calls (git push, rm -rf) are too risky to execute autonomously
- Require manual approval from human operator
- Approval shouldn't wait forever (dead approvals pile up)

## Decision

**Implement approval gate with time-to-live (TTL)**.

### Implementation

- `ApprovalGate` maintains in-memory queue of pending approvals
- Each approval has TTL (default: 60 minutes)
- HTTP API: `POST /approvals/{approvalId}/approve` or `/reject`
- Expired approvals auto-reject on next query

### Benefits

- ✅ Human control over risky operations
- ✅ Prevents stale approvals (TTL auto-reject)
- ✅ Simple HTTP API for integrations
- ✅ In-memory storage (fast, no DB)

### Trade-offs

- ❌ Approvals lost on restart (in-memory)
- ❌ No multi-instance approval queue (Phase 5: Redis)
- ❌ TTL is clock-based (not event-based)

## Consequences

- **Positive:**
  - Enables safe execution of risky tools
  - Prevents approval queue clutter
  - Easy to integrate with external systems

- **Negative:**
  - Need UI to display pending approvals
  - Need notification mechanism
  - Single-instance limitation

## Related Decisions

- ADR-001: Resume-first architecture
- ADR-004: Risk scoring with rules

---

# ADR-003: SQLite with WAL Mode for State

**Date:** May 11, 2026  
**Status:** Accepted  
**Context:** Need persistent, reliable state store that doesn't require external services in MVP.

## Problem

- Runs must be persisted across restarts
- Audit trail must be immutable
- Don't want to depend on external DB (PostgreSQL, MySQL) in MVP
- Performance must be acceptable for 10+ concurrent runs

## Decision

**Use SQLite with WAL mode** for all persistent state.

### Implementation

```sql
-- WAL mode for concurrent reads/writes
PRAGMA journal_mode = WAL;

-- Tables
CREATE TABLE runs (...)        -- run state + checkpoint
CREATE TABLE audit_events (...) -- immutable audit log
CREATE TABLE approvals (...)   -- pending approvals
CREATE TABLE memory (...)      -- context/memory cache
```

### Benefits

- ✅ Zero external dependencies
- ✅ ACID compliance
- ✅ WAL mode: concurrent reads while writing
- ✅ Single-file deployment (easy Docker)
- ✅ Built-in SQLite JDBC driver

### Trade-offs

- ❌ Single-writer limitation (scales to ~10 concurrent writes)
- ❌ File-based (no network replication)
- ❌ Per-connection overhead higher than PostgreSQL
- ❌ Not suitable for 1000+ concurrent runs

## Consequences

- **Positive:**
  - Simple deployment (no DB service)
  - Good enough for MVP/small deployments
  - Checkpoints enable resumption

- **Negative:**
  - Doesn't scale beyond ~100 concurrent runs
  - Phase 5: migrate to PostgreSQL for production scaling
  - Backup strategy needed (file copy)

## Mitigation

- Phase 5 plan: PostgreSQL migration guide
- Add WAL mode compression tuning
- Implement backup procedures in Phase 4

## Related Decisions

- ADR-005: Multi-provider LLM gateway

---

# ADR-004: Risk Scoring with YAML Rules

**Date:** May 11, 2026  
**Status:** Accepted  
**Context:** Determining which tool calls need human approval is complex and domain-specific.

## Problem

- Different environments have different risk tolerances
- Risk assessment is not just "this tool is dangerous" — it's contextual
  - Example: `git push` is low-risk to `develop`, high-risk to `main`
- Want rules to be data (YAML) not code (Java)
- Need to support new tools without recompilation

## Decision

**Implement risk scoring with pattern-matching YAML rules**.

### Implementation

```yaml
# risk-rules.yaml
rules:
  - name: "git push to main"
    toolName: "git_push"
    args: { branch: "main" }
    action: "score += 80"  # High risk
    
  - name: "rm -rf on production"
    toolName: "shell_exec"
    argsMatch: "rm -rf /prod"
    action: "block"  # Completely block
    
  - name: "read env file"
    toolName: "file_read"
    argsMatch: ".*\\.env"
    action: "score += 20"  # Medium risk
```

### Benefits

- ✅ Declarative, human-readable
- ✅ No recompilation needed
- ✅ Per-environment rules (dev vs prod)
- ✅ Supports pattern matching and scoring

### Trade-offs

- ❌ YAML parsing overhead (negligible)
- ❌ Rule complexity can grow
- ❌ Testing rules requires data-driven tests

## Consequences

- **Positive:**
  - Risk assessment is flexible
  - Can tune risk per environment
  - Rules are auditable

- **Negative:**
  - Need good documentation for rule syntax
  - Rules must be validated on load
  - Complex rules may be hard to debug

## Related Decisions

- ADR-002: Approval gate with TTL
- ADR-001: Resume-first architecture

---

# ADR-005: Multi-Provider LLM Gateway

**Date:** May 11, 2026  
**Status:** Accepted  
**Context:** No single LLM provider is perfect. Need ability to switch providers.

## Problem

- OpenAI: expensive, most capable, rate limits
- Ollama: free local, weaker, no internet needed
- Gemini: good balance, different API
- OpenRouter: 200+ models but routing complexity

## Decision

**Implement LlmGateway interface with per-provider implementations**.

### Implementation

```kotlin
interface LlmGateway {
    fun complete(messages, tools, systemPrompt): LlmResponse
}

class OpenAiGateway(...) : LlmGateway { ... }
class OllamaGateway(...) : LlmGateway { ... }
class GeminiGateway(...) : LlmGateway { ... }
```

### Benefits

- ✅ Easy to add new providers
- ✅ Swap providers at runtime (CLI arg: `--provider`)
- ✅ Test with local Ollama, deploy with OpenAI
- ✅ No vendor lock-in

### Trade-offs

- ❌ Each provider has quirks (API differences)
- ❌ Tool calling format differs slightly per provider
- ❌ Token counting accuracy varies

## Consequences

- **Positive:**
  - Freedom to choose provider per deployment
  - Dev can test locally, prod uses OpenAI
  - Easier debugging (swap to Ollama locally)

- **Negative:**
  - Test coverage needed for each provider
  - Phase 2: will add provider failover/fallback

## Mitigation

- Comprehensive provider tests
- Unified tool schema (JSONSchema 2020-12)
- Phase 1: add retry logic and circuit breakers

---

**End of ADRs**

## How to Add New ADRs

1. Copy this template
2. Use next number (ADR-006, ADR-007, etc.)
3. Link related ADRs
4. Update status (Accepted / Proposed / Superseded)
5. Make PR with the new ADR
