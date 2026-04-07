# Audit Log

Chronological record of audits, releases, documentation passes, and other
maintenance activities. Append-only -- newest entries at the bottom.

## 2026-04-08 -- /open-source protocol-app

- **Commits**: `a70cd1e`..`89e52c2`
- **Outcome**: Open-sourced protocol-app. Audit: 26 findings (2 critical, 5 high, 6 medium, 4 low, 9 info), all critical/high addressed. Added Apache 2.0 LICENSE + SPDX headers to all .kt files. Added CI (GitHub Actions: build + lint). Removed dead code (ChecklistRepository, DataStoreMigration, PrefsKeys) and purged DataStore dependency. Added ProGuard rules, batch DB query, GYM_WEEKLY_TARGET constant. Docs: CLAUDE.md (architecture, build, data layer, notifications, DB schema), README.md. Fixed lint-failing backup_rules.xml. Added repo topics. Release skipped (personal Android app, no binary distribution).
- **Deferred**:
  - Zero test coverage (high)
  - Manual DI via Application casts (low -- fine at current scale)
  - agents-guide.md (project too small)
  - 🎯T1: Today page rendering lag (docs/targets.md)
