# Security Policy

## Reporting a vulnerability

If you believe you've found a security vulnerability in oh-my-clipping, **please do not open a public GitHub issue**. Public issues make the problem visible to everyone before a fix can ship.

Instead, report it privately via GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) feature on this repository. If that's not available, open an issue titled "Security contact request" without details and a maintainer will reach out.

When reporting, include:

- A description of the issue and its impact.
- Steps to reproduce (or a proof-of-concept).
- The version / commit you tested.
- Any suggested mitigations you've considered.

We'll acknowledge receipt within a few business days and aim to keep you updated on progress.

## Scope

In scope:

- The Spring Boot backend (`src/`, `clipping-*/` modules).
- The React frontend (`frontend/`).
- Build, packaging, and deployment configuration shipped in this repository.

Out of scope:

- Vulnerabilities in third-party services this project integrates with (Slack, Gemini, Naver, public RSS feeds). Report those to the upstream vendor.
- Findings that require a malicious local administrator (e.g. someone with database write access).
- Issues in your own fork or deployment that arise from changes you've made.

## Secrets, tokens, and dev credentials

The repository ships with **local development defaults** that are clearly placeholders:

- `dev.admin@clipping.local` / `LocalPass123!` — seeded by `LocalDevSupportService` when `DEV_BOOTSTRAP=true`. Local development only.
- `.env.example` — empty values for all secrets. Fill in `.env` before booting.

These defaults are not secrets. **For any deployed environment, you must:**

- Set `DB_PASSWORD`, `ENCRYPTION_KEY`, `SLACK_SIGNING_SECRET` to strong unique values.
- Set `DEV_BOOTSTRAP=false` (the included guard refuses to seed onto a database with real users, but the env var is the first line of defence).
- Set `SESSION_COOKIE_SECURE=true` if serving over HTTPS.
- Rotate any seed user passwords immediately, or disable the seed entirely.

If you suspect a real secret has been committed (we audited this repo before publishing — see the open-sourcing notes — but mistakes happen), please report it privately as above so we can rotate and force-push history.
