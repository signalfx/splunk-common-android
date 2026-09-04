# AGENTS.md

Guidance for `splunk-common-android`, shared by Splunk Android RUM and Session Replay SDKs and maintained by the MRUM Android maintainers.

## Priorities

- **No new dependencies** without explicit approval, including runtime, test, build-plugin, Gradle, and transitive dependencies. `buildSrc/src/main/kotlin/Dependencies.kt` is only a catalog.
- **Preserve upgrade compatibility.** Assume users upgrade an installed app while old files, preferences, serialized data, scheduled jobs, class/package names, and encryption keys remain on the device. Preserve or migrate these formats and identifiers before renaming or removing them.
- **Never crash the host app for predictable SDK runtime failures.** Contain expected exceptions, degrade to a safe no-op or partial result, and keep diagnostics bounded. Never forward background failures to the main thread or let SDK/listener callbacks escape with an exception. Avoid uncaught `throw`, `error`, `require`, forced unwraps, and unchecked casts where a fallback exists; do not use blanket catches to hide bugs.
- **Treat mobile performance as correctness.** Avoid main-thread blocking and unbounded queues, retries, caches, listeners, or retained objects. Review startup, lifecycle/UI callbacks, reflection, allocations, locks, I/O, CPU, memory, network, and battery impact.
- **Raise performance concerns** and request explicit impact analysis when aggregate host-app cost is unclear.

## Modules

| Module | Purpose |
|---|---|
| `encoder` | Bitmap/video encoding |
| `http` | HTTP client and request/response models |
| `id` | Identifier generation |
| `job` | `JobScheduler` and persisted job IDs |
| `logger` | Logging and consumers |
| `storage` | Persistent files, preferences, caches, and encryption |
| `utils` | Lifecycle/UI, reflection, threading, and shared helpers |

All modules are published Android libraries. Treat every non-`internal` symbol, namespace, artifact ID, package/class name, default, error behavior, and inter-module contract as compatibility-sensitive.

## R8 and Runtime Discovery

- Treat `consumer-rules.pro`, `proguard-rules.pro`, reflection, `@Keep`, manifests, `JobScheduler`, and Android/AndroidX internal APIs as production correctness surfaces.
- Changes to these areas require release/minified host-app validation and supported-API checks. Include an upgrade scenario when older persisted state may reference previous names.
- Keep shrinker rules narrow; do not add broad rules without considering APK size, startup, and runtime impact.

## Production Failure Checklist

- **Async and callbacks:** handle executor rejection, cancellation, shutdown races, callbacks after shutdown, reentrancy, and exceptions from listeners/consumers without crashing or blocking the host.
- **Storage and crypto:** handle missing, corrupt, truncated, legacy, or partially written data; full/read-only storage; failed atomic operations; process death; concurrent access; and unavailable or invalidated keys. Discard SDK-owned data when safe rather than crash.
- **Network and jobs:** handle offline, DNS/TLS/proxy and timeout failures, malformed endpoints, HTTP errors, scheduler rejection, Doze/background limits, missing services or permissions, and OEM behavior. Bound retries and payload/response sizes.
- **Encoding and resources:** handle unsupported or broken codecs, invalid/empty frames, missing files, output failures, cancellation, and memory pressure. Release resources and disable the affected feature safely.
- **Lifecycle and process:** handle repeated/concurrent initialization, attach/detach, out-of-order callbacks, process restoration, and multi-process use without duplicate work, leaks, or stale state.
- **Diagnostics:** redact tokens, headers, payloads, user data, query parameters, and sensitive paths; diagnostic code must itself be safe and low-cost.

## Tests and Changes

- Keep changes in the smallest module boundary and follow existing Kotlin, Gradle, threading, storage, and visibility patterns. Prefer existing utilities and `internal`.
- Tests should verify observable behavior and inject relevant failures from the checklist: compatibility migrations, degraded paths, R8/runtime discovery, lifecycle/concurrency, persistence, and resource pressure.
- Public API, defaults, telemetry, storage formats, or failure behavior changes require compatibility rationale, migration guidance, and focused old/new tests. New behavior should be opt-in unless approved.
- Do not casually edit `*/lint-baseline.xml` to hide new findings.

## Commands

| Task | Command |
|---|---|
| Format/check style | `./gradlew ktlintFormat` / `./gradlew ktlintCheck` |
| Check/build a module | `./gradlew :<module>:check` / `./gradlew :<module>:build` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Full verification/build | `./gradlew check assemble` or `./gradlew build` |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` |

Use JDK 17 and the Gradle wrapper. Run the narrowest relevant checks, then full checks for shared code or build changes. Do not publish, sign, or run release tasks locally unless explicitly requested.
