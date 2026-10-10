# Attention worktree setup

These scripts are intended for the Codex worktree setup hook. They are safe to run repeatedly.

Configure the setup command in Codex with the platform-specific command below:

## Windows

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$CODEX_WORKTREE_PATH\scripts\codex\setup.ps1"
```

## macOS/Linux/WSL

```bash
bash "$CODEX_WORKTREE_PATH/scripts/codex/setup.sh"
```

The scripts:

1. Resolve the current worktree from `CODEX_WORKTREE_PATH`, falling back to Git.
2. Check Git and Java, and detect the validated Android SDK path when present.
3. Initialize CodeGraph when the worktree has no generated index, or run `codegraph sync` when one exists.
4. Run `:domain:test` as a fast project health check.
5. Write output to `.codex-worktree-setup.log`.

Set `CODEGRAPH_REQUIRED=1` when a missing CodeGraph installation should fail setup instead of producing a warning. The scripts do not install Python or Node packages because Attention is a Kotlin/Android project.
