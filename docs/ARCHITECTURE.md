# Architecture

This is a quick map of the codebase. For the full rulebook with rationale, see [`AGENTS.md`](../AGENTS.md).

## Shape

oh-my-clipping is a **modular monolith**: one Spring Boot app composed from 15 Gradle submodules.

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
 application services      domain / engine            persistence /
 (clipping-collection,     (clipping-domain,           SPI / models
  clipping-digest-          clipping-engine)          (clipping-persistence,
  application, …)                                     clipping-store-spi,
                                                      clipping-api-models, …)
```

Dependency direction is **only** root → application/domain/engine/SPI. Submodules never depend back into the root app. The `./gradlew check` task includes a `:checkBoundaries` task per submodule that fails the build on a violation.

## Module map

| Module | Role |
|---|---|
| **root app** (`src/`) | Spring Boot entry point. API controllers, scheduler, security, adapter wiring (Slack, Gemini, RSS, Naver). |
| `clipping-domain` | Pure domain models (Category, RssSource, Persona, AdminUser, …). No Spring/JPA/store/service. |
| `clipping-engine` | Clipping engine core — digest/pipeline policy + RSS/LLM/Slack/pipeline port DTOs. |
| `clipping-collection` | RSS / manual URL / Naver News collection application services. |
| `clipping-source` | RSS source verification, health, coverage, SLA, category sync. |
| `clipping-user-application` | User subscription / request / event / delivery-log services. |
| `clipping-analytics-application` | Keyword / sentiment / top-article / trend / stats query services. |
| `clipping-digest-application` | Digest application helpers — port mapping, notification DTO conversion. |
| `clipping-notification` | Operational and user notification services. Slack delivery, runtime settings, dedup via ports. |
| `clipping-persistence` | JPA entities, Spring Data repositories, JPA/JDBC store implementations. |
| `clipping-store-spi` | Store SPI ports and their return DTOs. |
| `clipping-app-ports` | App-internal workflow / notification port boundaries (e.g. prepared digest workflow). |
| `clipping-api-models` | API / MCP / service-result DTO contracts. |
| `clipping-application-models` | User / admin application DTOs. |
| `clipping-pipeline-models` | Pipeline-execution history DTOs. |
| `clipping-error-types` | Shared exception / error-code types. |

## Layers in plain terms

- **Domain** (`clipping-domain`, `clipping-engine`): the rules. What a digest *is*, what a category *is*, how scoring works. Has no idea how data is stored or how Slack works.
- **Application** (`clipping-*-application`, `clipping-collection`, `clipping-source`, `clipping-notification`): use cases. "Collect feeds for category X", "build today's digest for user Y". Calls into the domain and out through ports.
- **Ports / SPI** (`clipping-store-spi`, `clipping-app-ports`): interfaces application code uses to reach external systems. No implementation.
- **Adapters** (root app `src/main/kotlin/…`, `clipping-persistence`): the implementations of those ports — JPA repositories, HTTP clients, Slack senders, Gemini callers.
- **Models** (`clipping-api-models`, `clipping-application-models`, `clipping-pipeline-models`): the DTO contracts that cross layer boundaries.

## "Where should this change go?"

Examples:

- New REST endpoint → controller in `src/main/kotlin/…/admin` or `…/user`. Wire to an application service.
- New domain rule (e.g. selection policy) → `clipping-engine` (no Spring).
- New DB table / migration → `clipping-persistence` + a Flyway file in `src/main/resources/db/migration/V{N}__*.sql`.
- New external integration → start with a port in `clipping-store-spi` or `clipping-app-ports`, then an adapter in the root app.
- New MCP tool → tool class in the root app, alongside the existing `admin_*` and `user_*` tools.

When in doubt, grep for an existing thing that does roughly what you want, follow that pattern, and ask the reviewer.

## Constraints you'll trip over

- **No PostgreSQL-only SQL in production source.** Tests use H2 in PostgreSQL mode, which doesn't understand `INTERVAL '1 day'`, `ON CONFLICT (col) DO UPDATE`, or `::jsonb`. The `checkPostgresSpecificSql` Gradle task enforces this.
- **No new `catch (Exception)`.** `checkBroadExceptionBaseline` blocks regressions. Lower the count when you remove one.
- **No `useMemo` / `useCallback` / `React.memo`.** React Compiler memoises automatically; the lint rule will fail you.
- **Migration numbers are shared.** If you branch off `Vn` and main moves to `Vn+1`, renumber yours to `Vn+2` before merge.

See [`AGENTS.md`](../AGENTS.md) §1.3 and §1.5 for the full constraint list.
