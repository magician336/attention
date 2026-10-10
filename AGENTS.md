## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.

<!-- CODEGRAPH_START -->
## CodeGraph

For code repositories, use CodeGraph as the default code-understanding path. First identify the repository root (the directory containing `.git`). If `.codegraph/` is missing, run `codegraph init` at that root before surveying symbols or editing code. If `.codegraph/` exists, use CodeGraph before grep/find or reading files when you need to understand or locate code:

- `codegraph_explore` answers most code questions in one call when the MCP tool is available.
- The shell equivalent is `codegraph explore "<symbol names or question>"`.
- After changes that affect symbols or files, refresh an existing index with `codegraph sync`; use `codegraph index` when a full rebuild is required.
- A worktree may contain `.codegraph/` without its generated index because the index is not part of Git. If exploration reports that no index exists, initialize or rebuild CodeGraph for that worktree before relying on its results.

<!-- CODEGRAPH_END -->
