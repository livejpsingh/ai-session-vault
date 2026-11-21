#!/usr/bin/env bash
# AI Session Vault - Hook Installer for Linux/macOS
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Installing AI Session Vault hooks in $REPO_ROOT..."
chmod +x "$SCRIPT_DIR/session-vault-hook.sh"

mkdir -p "$REPO_ROOT/.agents"
cat << 'EOF' > "$REPO_ROOT/.agents/hooks.json"
{
  "vault-session-sync": {
    "description": "Auto-sync Antigravity conversation transcripts to AI Session Vault.",
    "SessionStart": [
      {
        "type": "command",
        "command": "bash scripts/session-vault-hook.sh --platform antigravity --event SessionStart",
        "timeout": 15
      }
    ],
    "Stop": [
      {
        "type": "command",
        "command": "bash scripts/session-vault-hook.sh --platform antigravity --event Stop",
        "timeout": 45
      }
    ]
  }
}
EOF

mkdir -p "$REPO_ROOT/.github/hooks"
cat << 'EOF' > "$REPO_ROOT/.github/hooks/ai-session-vault.json"
{
  "version": 1,
  "hooks": {
    "SessionStart": [
      {
        "type": "command",
        "bash": "bash scripts/session-vault-hook.sh --platform copilot --event SessionStart",
        "timeoutSec": 15
      }
    ],
    "Stop": [
      {
        "type": "command",
        "bash": "bash scripts/session-vault-hook.sh --platform copilot --event Stop",
        "timeoutSec": 45
      }
    ]
  }
}
EOF

echo "AI Session Vault hooks installed successfully!"
