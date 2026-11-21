#!/usr/bin/env bash
# AI Session Vault - Lifecycle Hook for Antigravity & GitHub Copilot (Linux/macOS)
set -eo pipefail

PLATFORM="antigravity"
EVENT="SessionStart"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -Platform|--platform)
      PLATFORM="$2"
      shift 2
      ;;
    -Event|--event)
      EVENT="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Read stdin
INPUT_JSON=$(cat || echo "{}")
SESSION_ID=$(echo "$INPUT_JSON" | grep -o '"conversationId": *"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
if [ -z "$SESSION_ID" ]; then
  SESSION_ID=$(echo "$INPUT_JSON" | grep -o '"session_id": *"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
fi
TRANSCRIPT_PATH=$(echo "$INPUT_JSON" | grep -o '"transcriptPath": *"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
if [ -z "$TRANSCRIPT_PATH" ]; then
  TRANSCRIPT_PATH=$(echo "$INPUT_JSON" | grep -o '"transcript_path": *"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
fi

VAULT_URL="${VAULT_BASE_URL:-http://localhost:8080}"
VAULT_KEY="${VAULT_API_KEY:-vault-secret-key-2026}"

if [ -n "$SESSION_ID" ] && [ "$EVENT" = "SessionStart" ]; then
  PROVIDER="google-antigravity"
  EXT_ID="$SESSION_ID"
  if [ "$PLATFORM" = "copilot" ]; then
    PROVIDER="github-copilot"
    EXT_ID="github-copilot:$SESSION_ID"
  fi

  curl -s -X POST "$VAULT_URL/api/sessions" \
    -H "Content-Type: application/json" \
    -H "X-API-KEY: $VAULT_KEY" \
    -d "{\"externalSessionId\":\"$EXT_ID\",\"title\":\"$PROVIDER session $SESSION_ID\",\"provider\":\"$PROVIDER\",\"workspacePath\":\"$REPO_ROOT\"}" > /dev/null 2>&1 || true
fi

if [ -n "$TRANSCRIPT_PATH" ] && [ -f "$TRANSCRIPT_PATH" ]; then
  PROVIDER="google-antigravity"
  EXT_ID="$SESSION_ID"
  if [ "$PLATFORM" = "copilot" ]; then
    PROVIDER="github-copilot"
    EXT_ID="github-copilot:$SESSION_ID"
  fi

  curl -s -X POST "$VAULT_URL/api/transcripts/ingest" \
    -H "Content-Type: application/json" \
    -H "X-API-KEY: $VAULT_KEY" \
    -d "{\"externalSessionId\":\"$EXT_ID\",\"filePath\":\"$TRANSCRIPT_PATH\",\"provider\":\"$PROVIDER\",\"workspacePath\":\"$REPO_ROOT\"}" > /dev/null 2>&1 || true
fi

if [ "$PLATFORM" = "antigravity" ] && [ "$EVENT" = "SessionStart" ]; then
  echo '{"injectSteps":[{"systemMessage":{"systemMessage":"AI Session Vault is active. History and token optimization available via MCP."}}]}'
elif [ "$PLATFORM" = "antigravity" ]; then
  echo '{"decision":"stop"}'
else
  echo '{}'
fi
exit 0
