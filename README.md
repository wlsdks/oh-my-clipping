<div align="center">

# oh-my-clipping

**An open-source news clipping agent.**
RSS → AI summary → Slack digest, with an admin UI and an MCP server interface.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Kotlin 2.3](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org/)
[![React 19](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev/)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[Quick start](#quick-start) · [Features](#features) · [Architecture](docs/ARCHITECTURE.md) · [Contributing](CONTRIBUTING.md) · [한국어 README](README.ko.md)

</div>

---

## What it does

You point oh-my-clipping at the RSS feeds you care about. It collects new articles, summarises them with an LLM, runs them through a reviewable rule engine, and ships a per-persona daily digest to Slack — for as many users and channels as you configure.

The same operations are exposed as **MCP tools**, so an LLM agent can collect, summarise, and post on your behalf.

## Features

- 🗞️ **RSS + manual URL collection** with SSRF-safe validation and per-source health tracking.
- 🤖 **LLM summarisation** (Gemini by default; the integration is portable) with per-run cost tracking.
- ✅ **Review queue** — human-in-the-loop approval before items reach a digest, with auto-exclude rules and an audit log.
- 💬 **Slack delivery** — Block Kit digests, per-category channels, link-share analytics, HMAC-verified webhooks.
- 🎛️ **Admin & user UI** — React 19 admin console plus a lightweight user surface for self-service subscriptions.
- 🔌 **MCP server** — `/mcp` endpoint exposing 13 admin tools and 9 user tools.
- 🧱 **Modular monolith** — 15 Gradle submodules with enforced dependency boundaries (`./gradlew check`).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Kotlin 2.3, Spring Boot 3.5 |
| Frontend | React 19, Vite 6, TypeScript 5.9, Tailwind v4 |
| Database | PostgreSQL 16 (H2 in tests) |
| Cache / rate limit | Redis 7 |
| LLM | Google Gemini via Spring AI |
| Build | Gradle (Kotlin DSL), pnpm |

## Quick start

```bash
# 1. Configure
cp .env.example .env
# Fill DB_PASSWORD (required). Set DEV_BOOTSTRAP=true for seeded local users.

# 2. Start Postgres + Redis
docker compose up -d postgres redis

# 3. Backend
./gradlew bootRun -PskipFrontendBuild=true     # http://localhost:8086

# 4. Frontend (separate terminal)
cd frontend && pnpm install && pnpm dev        # http://localhost:5173
```

When `DEV_BOOTSTRAP=true`:

- Admin: `dev.admin@clipping.local` / `LocalPass123!`
- User:  `dev.user@clipping.local`  / `LocalPass123!`

> ⚠️ **Local development only.** `DEV_BOOTSTRAP=true` runs a SQL bootstrap that *deletes* user-data tables before reseeding. A pre-flight guard refuses to run if real users exist, but never enable it against production.

Full setup walkthrough — including the Slack app, Gemini key, and Slack interactivity tunneling — is in [`docs/ONBOARDING.md`](docs/ONBOARDING.md).

## Documentation

| Where | What's there |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module map, dependency rules, where to start for a given change. |
| [`docs/ONBOARDING.md`](docs/ONBOARDING.md) | Local setup, dev commands, Slack app config. |
| [`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) | Admin / user / MCP API contracts. |
| [`docs/ADR.md`](docs/ADR.md) | Architecture decision records. |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Completed and planned work. |
| [`docs/TEST_STRATEGY.md`](docs/TEST_STRATEGY.md) | Test pyramid and coverage targets. |
| [`docs/LESSONS.md`](docs/LESSONS.md) | Lessons from past bugs and the rules that came out of them. |
| [`docs/DESIGN_STRATEGY.md`](docs/DESIGN_STRATEGY.md) | UI/UX principles. |
| [`docs/DEPLOY_GUIDE.md`](docs/DEPLOY_GUIDE.md) | Deployment checklist. |
| [`docs/SLACK_SETUP.md`](docs/SLACK_SETUP.md) · [`docs/SLACK_APP_MANIFEST.md`](docs/SLACK_APP_MANIFEST.md) | Slack workspace and app setup. |
| [`AGENTS.md`](AGENTS.md) | Engineering rulebook — architecture boundaries, conventions, quality gates. Authoritative when in conflict with this README. |

Most prose is in Korean, reflecting the project's origin. PRs that translate or summarise pieces in English are very welcome.

## MCP tools

Admin (13): `admin_list_categories`, `admin_collect[_async]`, `admin_summarize[_async]`, `admin_daily_summary`, `admin_job_status`, `admin_export`, `admin_send_digest` ⚠️ (publishes to Slack), `admin_pipeline` (preview), `admin_list_recent_jobs`, `admin_list_failing_sources`, `admin_list_pending_requests`.

User (9): `user_list_categories`, `user_list_sources`, `user_list_recent_summaries`, `user_search_summaries`, `user_list_top_summaries`, `user_get_summary_detail`, `user_get_original_preview`, `user_get_category_overview`, `user_get_trending_keywords`.

Enable with `CLIPPING_MCP_SERVER_ENABLED=true` + `CLIPPING_MCP_SERVICE_TOKEN=<32+ char bearer>`. Endpoint: `/mcp`. Audit log: `mcp_audit_log` (90-day retention).

## Quality gates

```bash
# Backend (unit + integration + boundary checks)
./gradlew check -PskipFrontendBuild=true

# Frontend (typecheck, lint, build, unit)
cd frontend
pnpm typecheck && pnpm lint && pnpm build && pnpm test

# End-to-end (Playwright)
cd frontend
pnpm exec playwright test --timeout=60000 --reporter=list
```

## Contributing

PRs welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) first — the short version:

1. Fork → feature branch → PR (never commit to `main`).
2. Run the quality gates before opening the PR.
3. Tests ship with the change. Bug fixes ship with a regression test.

By contributing, you agree your work is licensed under the MIT License.

## Security

To report a vulnerability, **don't open a public issue** — see [`SECURITY.md`](SECURITY.md).

## License

[MIT](LICENSE)
