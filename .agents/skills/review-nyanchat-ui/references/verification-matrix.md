# NyanChat UI Verification Matrix

Select every row relevant to the changed workflow. Record unrun checks explicitly; do not imply device or performance
validation from static inspection.

| Area | Minimum scenarios | Verify |
| --- | --- | --- |
| Core workflow | New, existing, edit, cancel, save | The shortest common path works; cancel preserves prior state; save is not duplicated |
| State surfaces | Empty, loading, content, error, disabled | State changes do not resize navigation or hide recovery actions |
| Destructive action | Named item, empty name, failure | Confirmation identifies the target; failure preserves data and reports recovery |
| Repeated list | Empty, one item, many items, reorder | Stable keys and row state survive movement; long labels do not cover actions |
| Keyboard | Closed, open, focus moved, back dismissed | Focused input and primary actions remain visible; inset is not applied twice |
| Navigation 3 | Push, back, predictive back, recreation | Back stack, typed arguments, saveable UI state, and ViewModel ownership remain correct |
| Compact phone | Narrow portrait and landscape | No horizontal clipping; controls retain stable hit areas; content remains scrollable |
| Large window | Tablet, foldable, or resizable window | Width is constrained or pane-aware; whitespace and reading width remain intentional |
| Text | Long value, long word, multiline label | Wrapping and truncation preserve meaning and do not overlap controls |
| Font scale | 1.0x, 1.3x, 2.0x | Rows can grow; text remains reachable; fixed controls do not clip labels |
| Theme | Light, dark, AMOLED when affected | Text, outlines, disabled state, scrims, and errors maintain usable contrast |
| Accessibility | Touch, keyboard/focus if relevant, screen reader semantics | Targets, traversal, labels, roles, state, and custom actions describe the workflow |
| Screen corners | Physical radii, unavailable radii, square mode | Only eligible page-level surfaces adapt; insets and fallback shapes remain correct |
| Process recreation | Rotate or restore after process death | Saveable local state restores; transient operations do not replay incorrectly |

## Performance Evidence Gate

Do not prescribe performance rewrites until at least one evidence source identifies a problem:

| Symptom | First evidence | Route after confirmation |
| --- | --- | --- |
| Scroll jank | Reproducible scroll plus frame trace or benchmark | `optimizing-lazy-layouts`, then release measurement or Perfetto |
| Unexpected recomposition | Layout Inspector counts or runtime recomposition trace | `debugging-recompositions` or `tracing-recompositions-at-runtime` |
| Unstable parameters | Compose compiler stability reports | `diagnosing-compose-stability`, then `stabilizing-compose-types` |
| Slow first layout | Trace showing repeated measure or subcomposition | `avoiding-subcomposition-pitfalls` |
| Startup or first-scroll regression | Release Macrobenchmark | `generating-baseline-profiles` |
| Release size or shrinker issue | Release artifact and R8 configuration evidence | `configuring-r8-for-compose` or `r8-analyzer` |

When no evidence is available, report the suspected user-visible symptom and the smallest measurement needed. Do not
turn a speculative optimization into an implementation requirement.
