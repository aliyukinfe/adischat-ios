# Repeatable compile results

Command: `gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain --max-workers=1`

- Formal run 1: PASS, 114.2 seconds
- Immediate run 2: PASS, 49.2 seconds, up-to-date
- Timestamp-touch run 3: PASS, 48.8 seconds
- Final post-device-fix run: PASS, 98.3 seconds

One intermediate run correctly reported a late-import syntax error introduced with the date formatter. The import was moved into the import block and the next normal compile passed. No heap, collector, or compiler-execution workaround was added.
