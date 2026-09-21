# Restore Point: Direct CSV Export & Adaptive Class Detection Fix
**Date**: September 21, 2026
**Commit / State Description**:
- Direct CSV export (`/export?format=csv&gid=...`) prioritized over GViz API to eliminate SQL type-inference drops of text headers (teacher names, course names).
- Dynamic GID resolution and caching from Google Spreadsheet `htmlview`.
- Adaptive class header detection supporting technical and vocational abbreviations (e.g. `BT3 MEC`, `TS`, `BP`, `LT`) and 24-row step patterns.
- Verified with unit tests passing all checks for 12 classes, student parsing, and teacher course coverage.

**Files Saved in this Snapshot**:
- `app/src/main/java/com/example/data/GoogleSheetsService.kt`
- `app/src/main/java/com/example/data/SchoolRepository.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- All other resources under `app/src/`
