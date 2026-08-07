---
name: review-nyanchat-ui
description: Review or implement NyanChat Jetpack Compose UI changes using the fork's component, layout, navigation, inset, accessibility, and verification conventions. Use for Compose pages and components, settings forms, repeated-item lists, dialogs and sheets, navigation flows, responsive layouts, or any NyanChat UI/UX review.
---

# Review NyanChat UI

Apply NyanChat's established interaction and visual language before proposing a new component or layout pattern. Treat
the repository code and `docs/NyanwChanges.md` as the source of truth when they are more specific than this skill.

## Gather Context

1. Read the repository `AGENTS.md` and the relevant section of `docs/NyanwChanges.md`.
2. Read [the NyanChat design system](references/design-system.md).
3. Inspect the target composable, its state owner, and at least one nearby screen with the same workflow.
4. Identify the complete state and interaction surface before editing: normal, empty, loading, error, disabled, destructive,
   keyboard-visible, and restored state as applicable.
5. Preserve upstream commits exactly when the task synchronizes RikkaHub work. Put NyanChat-specific adaptation and
   documentation in a later, separate commit.

## Choose Existing Building Blocks

- Use `CardGroup` for settings and form rows that form one Material 3 group.
- Use `OutlinedItemCard` for repeated entities and reorderable list items. Keep drag and item-animation modifiers at the
  list call site.
- Use `rememberScreenEdgeCornerShape` or the screen-corner composition locals only for page-level containers that meet
  the existing adaptation rules. Do not apply screen radii indiscriminately to message bubbles or item cards.
- Reuse the existing HugeIcons dependency and project icon conventions. Use `find-hugeicons` before guessing an icon
  name, and give unfamiliar icon-only controls a content description or tooltip as appropriate.
- Match the neighboring screen's scaffold, app bar, sheet, dialog, spacing, typography, and color treatment unless the
  requested change deliberately fixes that shared pattern.

## Review Behavior and Layout

- Optimize common operations for scanning and repeated use. Avoid extra confirmation steps for reversible actions; keep
  destructive actions explicit and identify the affected object.
- Keep page hierarchy restrained and information-dense. Do not nest decorative cards or introduce one-off containers
  when grouping, spacing, or a divider communicates the hierarchy.
- Preserve stable dimensions for icon controls and repeated rows. Verify that long values, localized text, and increased
  font scale wrap or truncate intentionally without covering controls.
- Keep interactive targets at least 48 dp where practical, preserve visible focus and pressed states, and expose role,
  state, label, and action semantics to accessibility services.
- Consume system bars and IME insets once at the correct owner. Ensure focused fields and sheet actions remain visible
  while the keyboard is open.
- Preserve Navigation 3 back-stack, saved-state, and ViewModel ownership. Use the existing `Navigator`, typed `Screen`
  keys, and entry decorators rather than creating a parallel navigation path.

## Route Specialized Work

Load the smallest relevant repository skill in addition to this one:

- Use `compose-state-and-effects` for state ownership and effects, and `compose-component-design` for reusable APIs.
- Use `navigation-3`, `adaptive`, or `edge-to-edge` for navigation, window-size behavior, or inset problems.
- Use `compose-ui-testing-patterns` or `testing-setup` when tests or test infrastructure are in scope.
- Use `optimizing-lazy-layouts` for lazy-list behavior and `avoiding-subcomposition-pitfalls` for measured layout issues.
- Use `compose-performance` to triage performance. Continue into recomposition, stability, release measurement, Baseline
  Profile, Perfetto, or R8 skills only when compiler reports, a trace, a benchmark, or a clear reproduction supports it.

Do not label code as a performance problem from style alone. State the missing evidence and propose the smallest useful
measurement when no evidence exists.

## Verify and Report

Use [the verification matrix](references/verification-matrix.md) to select checks proportional to the change. Prefer
findings tied to a user workflow and file location over subjective visual commentary.

After an implementation:

1. Search for new or changed symbols and confirm every implementation is referenced.
2. Search locale resources for unused or unsynchronized keys introduced by the change.
3. Update `docs/NyanwChanges.md` in its relevant section.
4. Run static checks permitted by `AGENTS.md`. Do not run Gradle, Android Studio, `pnpm`, tests, builds, or Lint; leave
   those checks to the maintainer.
