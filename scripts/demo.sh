#!/usr/bin/env bash
# AgentShell E2E Demo — uses local Ollama (no API key required)
# Usage: ./scripts/demo.sh

set -e
BOLD='\033[1m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RESET='\033[0m'

echo -e "${BOLD}⚡ AgentShell Demo${RESET}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Check/install Ollama ──────────────────────────────────────────────────
if ! command -v ollama &>/dev/null; then
  echo -e "${YELLOW}Ollama not found. Installing...${RESET}"
  curl -fsSL https://ollama.ai/install.sh | sh
fi

# ── 2. Start Ollama if not running ───────────────────────────────────────────
if ! curl -s http://localhost:11434/api/version &>/dev/null; then
  echo -e "${CYAN}Starting Ollama...${RESET}"
  ollama serve &>/tmp/ollama.log &
  sleep 3
fi

# ── 3. Pull model if needed ───────────────────────────────────────────────────
MODEL="${AGENTSHELL_MODEL:-qwen2.5-coder:7b}"
if ! ollama list 2>/dev/null | grep -q "$MODEL"; then
  echo -e "${CYAN}Pulling $MODEL (first time only, ~4GB)...${RESET}"
  ollama pull "$MODEL"
fi

# ── 4. Build AgentShell if needed ─────────────────────────────────────────────
if [ ! -f build/libs/AgentShell-*-all.jar ]; then
  echo -e "${CYAN}Building AgentShell...${RESET}"
  ./gradlew shadowJar --no-daemon -q
fi

# ── 5. Run the demo agent ─────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}▶ Starting AgentShell with preset: project-scan${RESET}"
echo -e "  Model: ${BOLD}$MODEL${RESET} (local Ollama)"
echo -e "  UI Dashboard: ${CYAN}http://localhost:9090/ui${RESET}"
echo ""

./gradlew run --no-daemon --args="\
  --agent demo-agent \
  --preset project-scan \
  --provider ollama \
  --model $MODEL \
  --metrics-port 9090 \
  --approval-port 8080 \
  --risk-threshold 50 \
" 2>&1

echo ""
echo -e "${GREEN}✅ Demo complete!${RESET}"
echo -e "   REPORT.md created in current directory"
echo -e "   View run history: ${CYAN}http://localhost:9090/ui${RESET}"
