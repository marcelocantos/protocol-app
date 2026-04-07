# Audit Log

Chronological record of audits, releases, documentation passes, and other
maintenance activities. Append-only -- newest entries at the bottom.

## 2026-04-08 -- /audit

- **Commit**: `b63ff31`
- **Outcome**: 26 findings (2 critical, 5 high, 6 medium, 4 low, 9 info). Report: docs/audit-2026-04-08.md.
- **Deferred**:
  - No licence file (critical)
  - No copyright/SPDX headers (critical)
  - local.properties committed to VCS (high)
  - Zero test coverage (high)
  - No CI pipeline (high)
  - Race condition in migration startup (high)
  - Dead code: ChecklistRepository (high)
  - No README (medium)
  - No CLAUDE.md (medium)
  - Missing ProGuard rules for kotlinx-serialization (medium)
  - WeekViewModel stale date list (medium)
  - Backup/export strategy for SQLite (medium)

## 2026-04-08 -- /docs

- **Commit**: (this commit)
- **Outcome**: Created CLAUDE.md (project overview, build, architecture, data layer, notifications, DB schema, conventions) and README.md (features, build, licence). Fixed lint-failing backup_rules.xml.
- **Deferred**:
  - agents-guide.md (project too small to justify separate file)
  - Testing strategy docs (no tests exist yet)
