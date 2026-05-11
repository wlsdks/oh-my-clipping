# Changelog

All notable changes to this project will be documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial open-source release as `oh-my-clipping`.
- MIT license, contribution guide, code of conduct, security policy.
- `docs/ARCHITECTURE.md` for module overview.

### Changed
- Project renamed from internal product to `oh-my-clipping`.
- Documentation set trimmed to the docs needed by external contributors.
- Repository layout: the 15 flat `clipping-*` Gradle modules are regrouped into `core/`, `ports/`, `adapters/`, `modules/`. See ADR-040 for the full mapping.
- Root Java package renamed from `com.clipping.mcpserver` to `com.ohmyclipping`. Sub-packages are unchanged.
- `clipping-pipeline-models` is folded into `core/api-models`; the pipeline run DTO now lives under the same module alongside the existing API/MCP/service-result DTOs.
- `clipping-application-models` is split by feature: user DTOs into `modules/user`, admin DTOs into a new `modules/admin`, analytics DTOs into `modules/analytics`, cross-cutting DTOs remain in `core/api-models`.

### Removed
- Internal seed/sample data, sales material, and incident-specific docs.
- Identifiable third-party brand references in fixtures and seed migrations.
