# Convergence Targets

## 🎯T1 Today page renders instantly on day change and app open

**Status**: Not started

**Desired state**: Swiping between days on the Today page shows the
correct checklist with no perceptible pause. Opening the app on a new
day shows today's checklist immediately — never a blank or hung page.

**Symptoms observed**:

- Odd pause when flipping between days.
- Occasionally on a new day the page doesn't render at all until the
  user swipes away and back. Unclear whether this is extreme slowness
  or an actual hang.

**Likely causes** (investigate in order):

1. **DB query per swipe**: `completions` in `TodayViewModel` triggers
   `db.completedItems()` (IO-dispatched SQLite query) on every date
   change. The pager waits for `settledPage`, so the sequence is:
   animation finishes → date changes → DB query starts → UI updates.
   That round-trip is the pause. Consider pre-fetching adjacent days
   or caching recent queries.

2. **Stale-date blank page**: `_selectedDate` is initialised to
   `LocalDate.now()` and the pager starts at `PAGE_OFFSET` (also
   today). If the ViewModel survives overnight but `_selectedDate` is
   still yesterday, the pager's `LaunchedEffect` calls
   `goToDate(today)` which correctly updates `_selectedDate`. But if
   the ViewModel is fresh *and* the date is already today,
   `MutableStateFlow` won't re-emit (same value), so the `merge` in
   `completions` relies on the `stateIn` subscription replay from
   `_selectedDate`. Verify this always fires — if `stateIn`'s upstream
   subscription races with the composable's first collection, the
   initial `emptyMap()` default could persist.

3. **Default-value flash**: `checklist` combines `dayType` (default
   `REST`) and `completions` (default `emptyMap()`). On an OFFICE day
   the UI briefly shows a REST checklist before the real `dayType`
   arrives from the DB. This may look like "nothing came up" if the
   user glances away.

4. **`refreshDate()` is never called on resume**: `MainActivity.onResume`
   schedules notifications but doesn't tell `TodayViewModel` that the
   date may have changed. If the process was alive overnight, the
   ViewModel is stale.

**Approach**: Fix (4) first (cheap), then (1) with a small LRU or
prefetch, then audit (2) and (3) for remaining edge cases.
