#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys


DESTRUCTIVE_PATTERNS = [
    r"(^|\s)rm\s+-rf\s+/",
    r"(^|\s)mkfs(\.|\s)",
    r"(^|\s)dd\s+if=",
]

HIGH_RISK_PATTERNS = [
    r"(^|\s)rm\s+-rf\b",
    r"(^|\s)sudo\b",
    r"git\s+push\b",
    r"git\s+reset\s+--hard\b",
    r"git\s+clean\s+-fdx\b",
    r"docker\s+compose\s+down\b.*-v",
    r"docker\s+system\s+prune\b",
    r"kubectl\s+delete\b",
]


def emit(payload: dict) -> int:
    json.dump(payload, sys.stdout)
    sys.stdout.write("\n")
    return 0


def matches_any(patterns: list[str], text: str) -> bool:
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except Exception as exc:  # pragma: no cover - defensive hook path
        return emit({"systemMessage": f"Hook input could not be parsed: {exc}"})

    event = payload.get("hookEventName")

    if event == "SessionStart":
        return emit(
            {
                "hookSpecificOutput": {
                    "hookEventName": "SessionStart",
                    "additionalContext": (
                        "Workspace policy: investigate root cause before fixing bugs; "
                        "prefer a failing test before the fix; avoid mock-only assertions; "
                        "and do not claim success without fresh verification evidence "
                        "(command, exit status, or test output)."
                    ),
                }
            }
        )

    if event == "PreToolUse":
        tool_name = payload.get("tool_name", "")
        tool_input = payload.get("tool_input", {}) or {}

        if tool_name != "run_in_terminal":
            return emit({"continue": True})

        command = tool_input.get("command", "")
        if not isinstance(command, str):
            command = json.dumps(command)

        if matches_any(DESTRUCTIVE_PATTERNS, command):
            return emit(
                {
                    "hookSpecificOutput": {
                        "hookEventName": "PreToolUse",
                        "permissionDecision": "deny",
                        "permissionDecisionReason": "Blocked a destructive terminal command by workspace policy.",
                        "additionalContext": (
                            "Use a safer, reversible command and verify the result with explicit output."
                        ),
                    }
                }
            )

        if matches_any(HIGH_RISK_PATTERNS, command):
            return emit(
                {
                    "hookSpecificOutput": {
                        "hookEventName": "PreToolUse",
                        "permissionDecision": "ask",
                        "permissionDecisionReason": "High-risk terminal command requires confirmation.",
                        "additionalContext": (
                            "Run one terminal command at a time and use the resulting output as evidence before claiming completion."
                        ),
                    }
                }
            )

        return emit(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "additionalContext": (
                        "For terminal work, avoid parallel commands and use fresh command output as verification evidence in your reply."
                    ),
                }
            }
        )

    if event == "Stop":
        if payload.get("stop_hook_active"):
            return emit({"continue": True})

        return emit(
            {
                "systemMessage": (
                    "Before finishing, back any success claim with a fresh verification command and its output."
                )
            }
        )

    return emit({"continue": True})


if __name__ == "__main__":
    raise SystemExit(main())
