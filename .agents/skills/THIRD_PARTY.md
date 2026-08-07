# Third-Party Agent Skills

The skill directories listed below are vendored without modification. Update them by replacing the complete directory
from the pinned upstream revision, then review and validate the resulting diff. Keep NyanChat-specific guidance in
`review-nyanchat-ui` instead of editing these files.

Skydoves skills are installed as top-level discoverable directories. Compatibility symlinks under `.agents/build`,
`.agents/lists`, `.agents/measurement`, `.agents/recomposition`, `.agents/side-effects`, and `.agents/stability`
preserve their original cross-category relative links without duplicating or modifying upstream files.

All three sources are distributed under the Apache License 2.0. A copy is available at
[`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt).

## Validation Notes

Run the validator in an isolated, disposable dependency environment with:

```bash
uv run --no-cache --no-project --with pyyaml \
  "${CODEX_HOME:-$HOME/.codex}/skills/.system/skill-creator/scripts/quick_validate.py" \
  .agents/skills/<skill-name>
```

At the pinned revisions, `collecting-flows-safely` and `deferring-state-reads` are structurally valid but the official
validator rejects angle brackets in their unchanged upstream descriptions (`Flow<T>` and `State<T>`). Keep those files
unchanged and treat this as an upstream validator compatibility exception. All other vendored skills pass the official
validator.

`git diff --check` also reports four unchanged upstream trailing-space lines in Android reference documents under
`adaptive` and `navigation-3`. Do not normalize these vendored files locally; run whitespace validation separately for
project-owned skill and collaboration-rule changes.

## chrisbanes/skills

- Repository: https://github.com/chrisbanes/skills
- Revision: `e04a16e079c578b489d201cbed8a30396e2d67b0`
- Paths:
  - `skills/compose-animations`
  - `skills/compose-component-design`
  - `skills/compose-focus-navigation`
  - `skills/compose-performance`
  - `skills/compose-state-and-effects`
  - `skills/compose-ui-testing-patterns`
  - `skills/kotlin-api-design`
  - `skills/kotlin-concurrency-and-flow`
  - `skills/kotlin-control-flow`

## skydoves/compose-performance-skills

- Repository: https://github.com/skydoves/compose-performance-skills
- Revision: `1b32f81724c0d71fe9ef093ca44697f559fdab6e`
- Paths:
  - `build/configuring-r8-for-compose`
  - `lists/configuring-lazy-prefetch`
  - `lists/optimizing-lazy-layouts`
  - `measurement/generating-baseline-profiles`
  - `measurement/testing-compose-in-release-mode`
  - `measurement/tracing-recompositions-at-runtime`
  - `recomposition/avoiding-subcomposition-pitfalls`
  - `recomposition/choosing-derivedstateof`
  - `recomposition/debugging-recompositions`
  - `recomposition/deferring-state-reads`
  - `recomposition/using-strong-skipping-correctly`
  - `side-effects/collecting-flows-safely`
  - `side-effects/using-efficient-effects`
  - `stability/diagnosing-compose-stability`
  - `stability/enforcing-stability-in-ci`
  - `stability/stabilizing-compose-types`
  - `stability/understanding-stability-inference`

## android/skills

- Repository: https://github.com/android/skills
- Revision: `28822b2306f34740542e4150888c5b0cde41724d`
- Paths:
  - `jetpack-compose/adaptive`
  - `jetpack-compose/theming/styles`
  - `navigation/navigation-3`
  - `performance/r8-analyzer`
  - `profilers/perfetto-sql`
  - `profilers/perfetto-trace-analysis`
  - `security/android-intent-security`
  - `system/edge-to-edge`
  - `testing/testing-setup`
