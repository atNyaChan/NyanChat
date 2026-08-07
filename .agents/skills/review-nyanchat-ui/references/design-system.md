# NyanChat Compose Design System

Use this reference after reading the target screen. It records fork-specific defaults, not a replacement for inspecting
the current implementation.

## Component Selection

### Settings and forms

- Group related settings with `CardGroup` from
  `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt`.
- Use `item` for navigable or compact rows and `FormItem` when a label, description, trailing control, and vertically
  expanding content belong together.
- Keep controls aligned at the trailing edge when they remain readable there. Give long input fields their own row.
- Preserve the page's 16 dp horizontal rhythm and a matching bottom inset after the final group. Let `CardGroup` own its
  item spacing, pressed shape, colors, and outer-corner behavior.
- Place destructive full-width actions outside the normal form group and separate them from routine controls.

### Repeated entities and lists

- Use `OutlinedItemCard` from
  `app/src/main/java/me/rerere/rikkahub/ui/components/ui/OutlinedItemCard.kt` for repeated entities.
- Keep list margins, item spacing, drag behavior, stable lazy keys, `contentType`, and `animateItem` at the list owner.
- Default to clicking the item to open or edit it. Put secondary actions in the detail surface when the list would
  otherwise become crowded.
- Retain the established 16 dp item radius and compact 8 dp list rhythm unless the surrounding feature has a documented
  exception.

Do not put `OutlinedItemCard` inside another card. Do not use it for a page section that is not a repeated item.

## Physical Screen Corners

`ScreenCornerShape.kt` exposes the fork's physical bottom-corner support:

- Use `rememberScreenEdgeCornerShape(horizontalInset, bottomInset)` for a page-level container near the bottom screen
  edge. Pass the container's actual distance from the screen edges.
- Use `squareBottom` only when a surface intentionally attaches to the IME or another flush edge.
- Respect `LocalScreenCornerAdaptationEnabled`, `LocalScreenEdgeCornerRadii`, and
  `LocalScreenCornerFallbackRadius`; do not read platform rounded corners independently.
- Preserve the documented fallback of 20 dp and the user's square 4 dp mode through the shared theme locals.
- Keep message bubbles, individual reasoning/tool blocks, public outlined item cards, file rows, and fixed search fields
  on their existing shapes.

## Hierarchy and Density

- Favor a scan-friendly page: title bar, optional concise status or actions, then unframed content or grouped rows.
- Use cards to identify actual grouped controls or repeated objects, not to decorate every section.
- Keep headings sized for their surface. Settings, dialogs, and compact panels should not use display-scale text.
- Reuse neighboring page spacing. The common baseline is 16 dp at page edges and 8 dp between repeated items or major
  internal elements.
- Keep primary actions easy to reach and give loading operations an explicit cancel path only when cancellation is real.
- Represent no-data, first-use, loading, partial, error, offline, disabled, and permission-denied states when the feature
  can enter them. Empty states should occupy the available content area instead of appearing as a decorative mini-card.

## Icons and Commands

- Reuse HugeIcons already imported by the project. Search with the repository `find-hugeicons` skill when necessary.
- Use familiar icon-only controls for common toolbar actions. Supply a content description unless the icon is paired
  with an accessible text label or is purely decorative.
- Add a tooltip for unfamiliar icon-only commands. Do not introduce a locale key solely to label visible instructional
  text unless localization is explicitly in scope.
- Keep icon-button bounds stable so loading, pressed, checked, and disabled states cannot move adjacent content.

## Navigation, Insets, and State

- Add destinations as typed `Screen` keys and route through the existing `Navigator` and `NavDisplay` in
  `RouteActivity.kt`.
- Preserve `rememberNavBackStack`, saveable-state and ViewModel-store entry decorators, transitions, predictive back,
  and single-top or pop-up behavior where the workflow requires them.
- Apply Scaffold padding and `consumeWindowInsets` at the surface that owns them. Do not stack equivalent system-bar,
  safe-drawing, navigation-bar, and IME padding without checking the resulting geometry.
- Use `imePadding` on the scroll or action container that must move above the keyboard. Confirm the focused field can be
  scrolled into view and the primary action remains reachable.
- Hoist business state to the screen state owner. Use `rememberSaveable` for local interaction state that users expect
  to survive recreation, and key repeated-item state by stable identity rather than row position.

## Accessibility and Adaptation

- Support compact phone, landscape, tablet/foldable, and freeform window widths without hard-coded phone assumptions.
- Allow long translations and dynamic text to wrap, scroll, or truncate intentionally. Do not reduce font size based on
  viewport width.
- Verify 1.0x, 1.3x, and 2.0x font scale for affected dense surfaces.
- Maintain at least 48 dp interaction targets where practical and make the full intended row clickable.
- Expose selection, toggle state, error state, headings, roles, and custom actions through Compose semantics.
- Check light, dark, and AMOLED behavior with theme colors rather than hard-coded contrast assumptions.
