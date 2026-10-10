#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${CODEX_WORKTREE_PATH:-}"
if [[ -z "$ROOT" ]]; then
  ROOT="$(git rev-parse --show-toplevel)"
fi
if [[ ! -d "$ROOT" ]]; then
  printf 'SETUP FAILED: worktree root does not exist: %s\n' "$ROOT" >&2
  exit 1
fi

cd "$ROOT"
LOG_FILE="$ROOT/.codex-worktree-setup.log"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" | tee -a "$LOG_FILE"
}

run_logged() {
  log "Running: $*"
  "$@" 2>&1 | tee -a "$LOG_FILE"
  local command_status=${PIPESTATUS[0]}
  if (( command_status != 0 )); then
    log "SETUP FAILED: command exited with $command_status: $*"
    exit "$command_status"
  fi
}

log "Starting Attention worktree setup in $ROOT"

command -v git >/dev/null 2>&1 || { log "SETUP FAILED: git was not found on PATH."; exit 1; }
command -v java >/dev/null 2>&1 || { log "SETUP FAILED: Java was not found on PATH."; exit 1; }

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && -d "/mnt/d/Android/Sdk" ]]; then
  export ANDROID_HOME="/mnt/d/Android/Sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  log "Using Android SDK at $ANDROID_HOME"
elif [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  log "WARNING: ANDROID_HOME/ANDROID_SDK_ROOT is not set; Android tasks may need SDK configuration."
fi

if command -v codegraph >/dev/null 2>&1; then
  CODEGRAPH_DIR="$ROOT/.codegraph"
  has_index=0
  if [[ -d "$CODEGRAPH_DIR" ]] && find "$CODEGRAPH_DIR" -mindepth 1 -maxdepth 1 ! -name .gitignore -print -quit | grep -q .; then
    has_index=1
  fi
  if (( has_index == 1 )); then
    run_logged codegraph sync "$ROOT"
  else
    run_logged codegraph init "$ROOT"
  fi
else
  if [[ "${CODEGRAPH_REQUIRED:-0}" == "1" ]]; then
    log "SETUP FAILED: CODEGRAPH_REQUIRED=1 but codegraph was not found on PATH."
    exit 1
  fi
  log "WARNING: codegraph was not found; skipping index setup."
fi

if [[ ! -x "$ROOT/gradlew" ]]; then
  log "SETUP FAILED: gradlew was not found or is not executable at the worktree root."
  exit 1
fi
run_logged "$ROOT/gradlew" --no-daemon :domain:test

log "Attention worktree setup completed successfully."
