---
name: locale-tui-localization
description: Use this skill when users request manual i18n/localization updates for Android string resources.
---

# Manual Android Localization

Use this skill to translate Android string resources manually. It has no Python, `uv`, API, or external tool dependency.

## When to use

- The user asks to add a new localized string key.
- The user asks to translate/update `strings.xml` across multiple locales.
- The user mentions i18n/l10n or asks to update Android locale resources.

## Workflow

1. Confirm the target module (for example `app`).
2. Add or update the English source entry in `values/strings.xml`.
3. Translate the entry manually in every existing `values-*/strings.xml` file in the module.
4. Preserve placeholders, escapes, markup, and formatting exactly across locales.
5. Verify that every locale contains the same key and that XML remains well-formed.
6. Report the exact files and keys changed.

## Constraints

- The source value should be English.
- Every configured language must be updated; do not leave new keys relying on fallback English.
- Do not use automatic translation services or require Python tooling.
- Do not commit secrets or API keys.
