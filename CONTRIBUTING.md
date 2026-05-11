# Contributing to oh-my-clipping

Thanks for your interest! This guide explains how to propose changes and what the project expects in return.

If you're more comfortable in Korean, the engineering rulebook in [`AGENTS.md`](AGENTS.md) and [`AGENTS.md`](AGENTS.md) is the source of truth for code-level conventions. This file is the shorter, English contributor-facing summary.

## TL;DR

1. Fork → branch (`feature/short-description` or `fix/short-description`).
2. Make small, focused commits.
3. Run the quality gates (`./gradlew check`, `pnpm test`, optionally `pnpm exec playwright test`).
4. Open a PR against `main` with a clear summary and test plan.

> **Never commit to `main` directly.** Even one-line fixes go through a PR.

## Development setup

Prerequisites:
- JDK 21
- Node.js 20+ and `pnpm`
- PostgreSQL 16 and Redis 7 (Docker Compose works: `docker compose up postgres redis`)
- Optional: a Slack workspace + bot token for end-to-end delivery testing
- Optional: a Google AI Studio key for Gemini summarisation

Steps:

```bash
cp .env.example .env
# Fill in DB_PASSWORD (required) and any optional API keys you want to exercise.

./gradlew bootRun -PskipFrontendBuild=true   # backend on :8086
cd frontend && pnpm install && pnpm dev      # frontend on :5173
```

For seeded local users, set `DEV_BOOTSTRAP=true` in `.env`. The seed creates `dev.admin@clipping.local` / `LocalPass123!` and friends — **never enable this on a database that has real user data; the bootstrap SQL deletes user tables**. See [`docs/ONBOARDING.md`](docs/ONBOARDING.md) for the full local setup.

## Project layout

- `src/` — Spring Boot root app (controllers, scheduler, security, adapter wiring)
- `clipping-*/` — Gradle submodules for domain, persistence, engine, ports, application services
- `frontend/` — Vite + React 19 admin/user UI
- `docs/` — architecture, ADRs, policy, runbooks (Korean)

`README.md` has a one-line summary of every module. The architectural boundary rules live in [`AGENTS.md`](AGENTS.md) §2.1 and are enforced by `:checkBoundaries` Gradle tasks (`./gradlew check` runs them).

## Quality gates

Before opening a PR, all of these should pass:

```bash
# Backend
./gradlew check -PskipFrontendBuild=true

# Frontend
cd frontend
pnpm typecheck
pnpm lint
pnpm build
pnpm test

# Optional but recommended for UI-touching PRs:
pnpm exec playwright test --timeout=60000 --reporter=list
```

CI is not yet wired up at the new repo — until it is, treat the local commands above as the gate.

## Coding conventions

The detailed rules are in [`AGENTS.md`](AGENTS.md), but the things that surprise people most often:

- **No `useMemo` / `useCallback` / `React.memo`** — React Compiler memoises automatically; the lint rule will fail you.
- **Migration numbering is shared** — if your branch lands on top of a newer `Vn`, renumber yours before merge.
- **Don't widen `catch (Exception)`** — there's a baseline scanner that blocks new occurrences (`checkBroadExceptionBaseline`).
- **No PostgreSQL-only SQL in source** — the H2 mode used in tests doesn't understand `INTERVAL '1 day'` or `ON CONFLICT (col) DO UPDATE`. The `checkPostgresSpecificSql` Gradle task will catch you.
- **Tests are part of the change** — new features ship with happy path + edge + error tests. Bugfixes ship with a regression test.
- **Korean comments are fine** — most existing in-code documentation is Korean and we're keeping it that way.

## Commit and PR style

- Use imperative present-tense subjects (`fix: race in digest worker`, not `fixed the race`).
- One logical change per commit; squash on merge.
- PR description should explain the *why*. The *what* is in the diff.
- Reference the issue / ADR / lesson the change responds to when relevant.

## Reporting bugs

Open a GitHub issue with:

1. What you expected.
2. What actually happened.
3. Steps to reproduce (smallest possible).
4. Environment (OS, JDK, Node, browser if relevant).

For security issues, **do not open a public issue** — see [`SECURITY.md`](SECURITY.md).

## License

By contributing, you agree that your contributions will be licensed under the MIT License (see [`LICENSE`](LICENSE)).
