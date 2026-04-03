# AgentShell

AgentShell runtime foundation implementation (v2.0 hardened spec baseline).

## Included now

- Execution domain models and error taxonomy
- Sandbox policy model (step-level)
- Idempotency service with TTL-based store
- Risk scoring and approval gate primitives
- Resume-first agent runtime decision flow
- Unit tests for idempotency, risk gating, and resume behavior

## Build & test

```bash
./gradlew test
```

## Next implementation slices

- Persistent State Store (SQLite/Room-ready adapter)
- Tool execution pipeline (schema validation + sandbox enforcement)
- Audit trail writer and export pipeline
- AI Gateway adapters and context budget manager
