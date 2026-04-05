package dev.agentshell.domain

enum class ErrorCode(val retryable: Boolean) {
    ERR_SCHEMA_VIOLATION(false),
    ERR_PATH_VIOLATION(false),
    ERR_SANDBOX_KILLED(false),
    ERR_STALE_READ(true),
    ERR_APPROVAL_REJECTED(false),
    ERR_APPROVAL_TIMEOUT(false),
    ERR_SESSION_DEAD(true),
    ERR_TIMEOUT(true),
    ERR_IDEMPOTENCY_CONFLICT(false),
    ERR_PROVIDER_UNAVAILABLE(true),
    ERR_CONTEXT_OVERFLOW(true),
    ERR_MCP_DISCONNECTED(true),
    ERR_PLUGIN_INCOMPATIBLE(false),
    EXECUTION_FAILED(true),
}
