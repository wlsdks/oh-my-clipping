## Summary

<!-- 1-3 bullets describing the change and the why. -->

## Test plan

<!-- Checklist of what you ran / what reviewers should check. -->
- [ ] `./gradlew check -PskipFrontendBuild=true`
- [ ] `cd frontend && pnpm typecheck && pnpm lint && pnpm build && pnpm test`
- [ ] `cd frontend && pnpm exec playwright test` (if UI changes)
- [ ] Manual verification: ...

## Related

<!-- Issues, ADRs, lessons. Use Closes #123 to auto-close. -->
