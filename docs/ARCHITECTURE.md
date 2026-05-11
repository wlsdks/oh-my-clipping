# Architecture

This is a quick map of the codebase. For the full rulebook with rationale, see [`AGENTS.md`](../AGENTS.md).

## Shape

oh-my-clipping is a **modular monolith**: one Spring Boot app composed from 14 Gradle submodules grouped into `core/`, `ports/`, `adapters/`, and `modules/`.

```
                 ┌─────────────────────────────────────┐
                 │             root app (src/)         │
                 │  Spring Boot entry, scheduler,      │
                 │  security, adapter wiring           │
                 └────────────────┬────────────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
 application services        core / engine               adapters
 (modules/collection,     (core/domain,                (adapters/persistence,
  modules/digest,          modules/digest-policy)       adapters/notification)
  modules/source, …)                                         ▲
        │                         │                          │
        └────────────► ports/ ◄───┴──────────────────────────┘
                       (ports/persistence,
                        ports/workflow)
```

Dependency direction is **only** root → modules/core/engine → ports ← adapters. Submodules never depend back into the root app. The `./gradlew check` task includes a `:checkBoundaries` task per submodule that fails the build on a violation.

## Module map

| Module | Role |
|---|---|
| **root app** (`src/`) | Spring Boot entry point. API controllers, scheduler, security, adapter wiring (Slack, Gemini, RSS, Naver). |
| `core/domain` | Pure domain models (Category, RssSource, Persona, AdminUser, …). No Spring/JPA/store/service. |
| `core/api-models` | API / MCP / service-result DTO contracts, pipeline-execution history DTOs, and DTOs that cross multiple feature modules. |
| `core/error-types` | Shared exception / error-code types. |
| `ports/persistence` | Store SPI ports and their return DTOs. |
| `ports/workflow` | App-internal workflow / notification port boundaries (e.g. prepared digest workflow). |
| `adapters/persistence` | JPA entities, Spring Data repositories, JPA/JDBC store implementations. |
| `adapters/notification` | Operational and user notification services. Slack delivery, runtime settings, dedup via ports. |
| `modules/digest-policy` | Clipping engine core — digest/pipeline policy + RSS/LLM/Slack/pipeline port DTOs. |
| `modules/collection` | RSS / manual URL / Naver News collection application services. |
| `modules/source` | RSS source verification, health, coverage, SLA, category sync. |
| `modules/digest` | Digest application helpers — port mapping, notification DTO conversion. |
| `modules/user` | User subscription / request / event / delivery-log services, plus user-facing application DTOs. |
| `modules/admin` | Admin application DTOs. Service logic currently lives in the root app; may migrate here later. |
| `modules/analytics` | Keyword / sentiment / top-article / trend / stats query services, plus analytics application DTOs. |

## Layers in plain terms

- **Core** (`core/domain`, `core/api-models`, `core/error-types`): the pure types and contracts. No Spring, no JPA, no I/O.
- **Engine / domain policy** (`modules/digest-policy`): the rules. What a digest *is*, what a category *is*, how scoring works. Has no idea how data is stored or how Slack works.
- **Application** (`modules/collection`, `modules/source`, `modules/digest`, `modules/user`, `modules/analytics`): use cases. "Collect feeds for category X", "build today's digest for user Y". Calls into the domain and out through ports.
- **Ports / SPI** (`ports/persistence`, `ports/workflow`): interfaces application code uses to reach external systems. No implementation.
- **Adapters** (`adapters/persistence`, `adapters/notification`, root app `src/main/kotlin/…`): the implementations of those ports — JPA repositories, HTTP clients, Slack senders, Gemini callers.

## "Where should this change go?"

Examples:

- New REST endpoint → controller in `src/main/kotlin/…/admin` or `…/user`. Wire to an application service.
- New domain rule (e.g. selection policy) → `modules/digest-policy` (no Spring).
- New DB table / migration → `adapters/persistence` + a Flyway file in `src/main/resources/db/migration/V{N}__*.sql`.
- New external integration → start with a port in `ports/persistence` or `ports/workflow`, then an adapter in the root app or under `adapters/`.
- New MCP tool → tool class in the root app, alongside the existing `admin_*` and `user_*` tools.

When in doubt, grep for an existing thing that does roughly what you want, follow that pattern, and ask the reviewer.

## Constraints you'll trip over

- **No PostgreSQL-only SQL in production source.** Tests use H2 in PostgreSQL mode, which doesn't understand `INTERVAL '1 day'`, `ON CONFLICT (col) DO UPDATE`, or `::jsonb`. The `checkPostgresSpecificSql` Gradle task enforces this.
- **No new `catch (Exception)`.** `checkBroadExceptionBaseline` blocks regressions. Lower the count when you remove one.
- **No `useMemo` / `useCallback` / `React.memo`.** React Compiler memoises automatically; the lint rule will fail you.
- **Migration numbers are shared.** If you branch off `Vn` and main moves to `Vn+1`, renumber yours to `Vn+2` before merge.

See [`AGENTS.md`](../AGENTS.md) §1.3 and §1.5 for the full constraint list.
