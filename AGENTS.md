# Repository Guidelines

## Project Overview

NyanChat is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material 3 Expressive (MD3 Expressive) design language.

## Design Language

The UI follows Material 3 Expressive. New and reworked screens should match it rather than the older baseline
Material 3 look (cards with dividers, plain forms).

- The app theme is `MaterialExpressiveTheme` (app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt); prefer the
  expressive variants of components, shapes (`MaterialShapes`), emphasized typography (e.g. `titleSmallEmphasized`)
  and motion from `androidx.compose.material3` over hand-rolled equivalents.
- Settings-style and form pages use segmented groups via `CardGroup` (
  app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt) instead of a card with dividers.
- Favor whole-row click targets.

## Unified UI Conventions

The fork consolidates cross-cutting UI behavior into shared components and rules. Reuse them instead
of adding per-screen variants.

- **List items**: reorderable entity lists, favorites, message search results, model pickers,
  network/permission request logs, the sidebar assistant picker and the "copy assistant" list all
  render through the shared `OutlinedItemCard`
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/OutlinedItemCard.kt) — full width, outlined,
  16dp corners, whole-card click. List margins and item gaps are 8dp and the card vertical content
  padding is 8dp. Reorderable lists apply the shared `longPressReorder` modifier
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/ReorderableDrag.kt: 0.95 drag scale plus
  haptic feedback) and disable reordering while a search filter is active.
- **Settings and forms**: group options with `CardGroup`; on pages that reuse the settings-home large
  title plus card/list layout, keep 8dp between the title bar and the first item. The gap below the last
  card must match the page's left/right inset: with the 8dp outer `contentPadding` plus the 8dp item
  padding that means 16dp at the bottom, never half of it (the stats page's `LazyColumn` follows this).
- **Bottom sheets**: always use `BottomSheetDefaults.DragHandle()` with drag-to-dismiss, wrap the
  content height up to 95% of the available height, and use top padding 0dp / bottom 8dp (plus the
  navigation-bar inset). Fixed bottom button rows keep 8dp above and 8dp below, and use
  "Cancel / Confirm" via `common_confirm_action`. Titles are horizontally centered and use
  `MaterialTheme.typography.headlineSmall`, through the shared `BottomSheetTitle`
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/SheetContent.kt).
- **Colors** (`CustomColors`, app/src/main/java/me/rerere/rikkahub/ui/theme/Color.kt): page
  backgrounds, the top bar and all drawers use `surface`; card/list fills use `surfaceContainerLow`
  (`CustomColors.cardColorsOnSurfaceContainer`, `CustomColors.listItemColors`). Every
  `AlertDialog`/`BasicAlertDialog` uses `MaterialTheme.colorScheme.surface`.
- **Shapes**: `OutlinedTextField`s use the theme's `Shapes.extraSmall` (16dp); search fields use the
  fixed 20dp `SearchFieldShape`
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/SearchFieldShape.kt) instead of the
  screen-corner shape. Large containers (`CardGroup`, page cards, dialogs) adapt to the physical
  screen corner via `rememberScreenEdgeCornerShape`
  (app/src/main/java/me/rerere/rikkahub/ui/theme/ScreenCornerShape.kt), falling back to 28dp (the
  "直角" mode uses 4dp); message bubbles, thinking blocks, shared outlined list cards and file item
  cards keep their own shape.
- **Monospace text / code ligatures**: every piece of monospace text rendered as text (code
  blocks, line numbers, paths, identifiers, logs, JSON, formula source, …) must control ligatures
  from the settings switch `DisplaySetting.enableCodeLigatures` via
  `Boolean.codeFontFeatureSettings`
  (app/src/main/java/me/rerere/rikkahub/ui/theme/FontFeature.kt). Never leave a `FontFamily.Monospace`
  / `JetbrainsMono` style without `fontFeatureSettings = LocalSettings.current.displaySetting
  .enableCodeLigatures.codeFontFeatureSettings`; the `ui` markdown module receives it through
  `MarkdownTheme.codeFontFeatureSettings` instead of reading settings itself. Character-cell
  terminals and the user-chosen chat font are not code-ligature targets.
- **Shared components**: loading uses `AppLoadingIndicator`; color picking uses the shared
  `ColorPicker` (ui/src/main/java/me/rerere/ui/common/ColorPicker.kt); custom request Header/Body
  editing uses `CustomRequestProperties`
  (app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/PropertyEditor.kt) in the provider
  drawer, assistant request settings and model advanced settings; whether a request log is an LLM
  call is decided only by `isLlmRequest(log)`
  (app/src/main/java/me/rerere/rikkahub/data/ai/RequestInterceptController.kt); tabbed pages use the
  shared `HorizontalPager` wrapper
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/HorizontalPager.kt), which forwards to the
  foundation pager with `overscrollEffect = null` so swiping between pages never stretches — never
  import `androidx.compose.foundation.pager.HorizontalPager` directly.
- **File sizes**: whenever a file/content size is displayed (UI text, dialogs, logs, tool output),
  use binary units (`B`, `KiB`, `MiB`, `GiB`, `TiB`) — never decimal `KB` / `MB` / `GB`. Dynamic
  sizes go through the shared `Long.fileSizeToString()`
  (app/src/main/java/me/rerere/rikkahub/utils/StringUtils.kt), which already divides by 1024 and
  picks the precision; fixed limits/constants render as a whole binary number (e.g. `48KiB`,
  `20 MiB`) instead of a decimal `KB` / `MB` string. Do not hand-format a size with a decimal
  divisor.
- **Feedback**: `LocalToaster` errors open a scrollable, selectable, copyable dialog; success,
  warning and short messages use the Android system Toast. Do not add the old custom colored in-app
  banner.
- **Delete dialogs**: every delete confirmation goes through the shared `DeleteConfirmDialog`
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/DeleteConfirmDialog.kt) — do not rebuild the
  title or message at the call site. The title is always `delete_dialog_title` (“Delete {type}” /
  “删除{类型}”), the buttons are always `common_cancel` / `common_delete`, and the body is one of
  `delete_dialog_message_named` / `delete_dialog_message_unnamed` / `delete_dialog_message_batch`.
  Pass a localized `delete_type_*` string as the type plus an optional `name` (`count` + `pluralType`
  for batch); any extra paragraph goes through the `extra` slot. Simplified Chinese quotes use “”,
  Traditional Chinese quotes use 「」; `media_creation_*` delete dialogs keep their own copy.
- All scrollable pages (including the chat message list) use the platform stretch overscroll effect.

## Chat Attachments (Content-Addressed Storage, CAS)

Chat attachments are stored content-addressed. **Any code that creates, reads, moves, lists, deletes,
counts, or backs up attachment files must go through the CAS layer** — never treat `filesDir/upload`
as an ordinary folder, and never invent a uuid/filename-keyed path for an attachment.

- **Entities are content-addressed.** Bytes live at `filesDir/upload/<sha256>` (lowercase 64-hex),
  identical content is stored once, and the on-disk name is always the content hash. The only place a
  `uuid.ext` name still exists is the legacy RikkaHub-compatible ZIP export.
- **Registration and references.** One Room row per sha256 (`ManagedFileCasEntity`, table
  `managed_files_cas`) holds MIME/size and a `usages` JSON list of `FileUsage`
  (`message` / `assistant_avatar` / `assistant_background` / `user_avatar` / `none`). Content is
  referenced when any non-`none` usage exists; a lone `none` records on-disk time and the last display
  name.
- **Stored vs runtime form.** In-memory `UIMessage`/`Settings` always hold absolute `file://` urls; the
  database and DataStore only store paths relative to `filesDir` (`upload/<sha256>`). The conversion is
  `data/files/LocalFileUrls` (`withStoredLocalUrls` / `withRuntimeLocalUrls`), applied at
  `ConversationRepository`'s read/write boundary. Never persist a package-qualified absolute path, and
  never parse a sha out of a url by hand — use `attachmentShaFromUrl` / `tempAttachmentNameFromUrl`.
- **Drafts are not CAS.** Composer/queue attachments live under `cacheDir/upload_tmp`
  (`FileFolders.UPLOAD_TEMP`) with display name/MIME/sha only in an in-process map; they are neither
  registered nor content-addressed. Before a conversation is persisted, call
  `FilesManager.commitConversationFiles`: it streams each temp file to its `upload/<sha256>` entity
  (reusing an existing one) and returns `ConversationCommitResult` — replace the in-memory conversation
  with its `conversation` and pass its `infos` to `updateAttachmentIndex`.
  `stripUncommittedTemporaryAttachments` is the guard: if a temp url would still be written, it drops
  the attachment and logs a warning — never silently repair or store a temp path.
- **Content-dedup on add.** New composer attachments go through `dedupeTempAttachments`, and
  `commitTempAttachment` reuses an already-committed entity for the same content.
- **Deletion is reference-counted.** Delete through `FilesManager.deleteChatFiles` (optionally with a
  `removedUsage`) or `deleteUploadEntity`; after removing a usage the remaining usages are re-checked
  and the entity + row are only deleted when nothing else references the content. Never delete
  `upload/<sha256>` directly. Deleting a conversation/assistant must first drop its usages
  (`removeConversationFromAttachmentIndex` / `updateSettingsAttachmentUsages`).
- **Every attachment-bearing field counts.** `UIMessagePart.Image/Video/Audio/Document`, nested
  tool-result parts, and assistant avatar/background + user avatar. When adding a new attachment field
  or part type, extend `collectAttachmentRefs` / `mapLocalUrl` / `applyCommittedTempUrls`,
  `Settings.attachmentUrls()` and the settings-usages rebuild together.
- **Byte-level work goes through `AttachmentCas`.** Use `writeTemporary` / `commitTemporary` (hash
  computed while writing), `sha256`, `isSha256Name`, and `isTemporary` / `deleteStaleTemporaryFiles`
  for `upload-*.tmp` residue. Any listing, sizing, counting, startup sync
  (`FilesManager.syncManagedFiles`) or backup must skip `AttachmentCas.isTemporary` files so a
  half-written temp file never leaks into the UI, stats or an archive.
- **Backup/restore is CAS-aware.** Archives declare a schema version; only pre-`CAS_SCHEMA_VERSION`
  (100) archives at or above `MIN_SUPPORTED_SCHEMA_VERSION` (30 / 31, the upstream schema versions) are migrated
  (`ManagedFilesArchive.normalizeUploadDir` renames `upload` to sha256 and `convertLegacyDatabase`
  converts the old `managed_files` table); older archives are rejected. Never
  re-normalize an archive that is already CAS, and never copy upstream files over our CAS layout.
- **`FilesManager` is the single entry point** (`data/files/FilesManager.kt`). UI/service/repository
  code must use its APIs rather than opening `upload` files directly, so registration, usages and
  cleanup stay consistent.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # Build Debug APK
./gradlew test                   # Run all JVM tests
./gradlew lint                   # Run Android Lint
./gradlew spotlessApply          # Format all Kotlin files with ktlint (-PspotlessRatchet=<ref> limits to files changed since that ref)
./gradlew spotlessCheck          # Check formatting without modifying files
```

Build configuration is unified across the project: JDK 21 for the project, the Gradle daemon,
toolchain declarations and CI; `compileSdk` 37.2 (Android SDK Platform 37) and `targetSdk` 37,
`minSdk` 28; Java and Kotlin `jvmTarget` 21; Build-Tools pinned to 37.0.0. Library modules get this
from the `rikkahub.android.library` / `rikkahub.android.library.compose` convention plugins in the
`build-logic` included build; app-level keep rules live in `app/src/main/keepRules/rikkahub.keep`.
Do not raise `minSdk` or change the Java/Kotlin target per module.

## Agent Change Workflow

- After any code, resource, or configuration change, the agent MUST **FULLY** READ (and update
  `docs/NyanwChanges.md` if necessary), recording the change in the appropriate place in the document — do not
  keep appending to the end.
- After making changes, scan the repository for unwanted code or locale strings and delete them if
  found.
- The agent must NOT run Gradle, Android Studio, `pnpm`, or any other build, test, or Lint commands on its own. After
  completing changes and static checks, leave compilation and runtime verification to the user.
- Do NOT commit anything until the user has manually compiled and verified the changes; never commit
  before user confirmation.

## Upstream Sync Policy (IMPORTANT)
This repo is a **hard fork** (~30k lines diverged). We record upstream
merges in git history, but NEVER let git auto-merge file contents.

### Absolute rules
- The ONLY allowed merge command is:
  `git merge -s ours upstream/master --no-commit --no-ff`
  (`-s ours` keeps our tree untouched; it only records ancestry.)
- NEVER use default `git merge`, `git rebase`, `git cherry-pick`, `git pull`.
- NEVER resolve conflict markers (`<<<<<<<`). With `-s ours` they cannot appear; if you see any,
  abort immediately and stop.
- NEVER copy upstream files wholesale over ours.

### Workflow: one sync = one merge commit
1. List unported commits: `git log --oneline HEAD..upstream/master`
2. Start the merge (this changes NO files — verify `git diff HEAD` is empty):
   `git merge -s ours upstream/master --no-commit --no-ff`
3. Now port EACH upstream commit's changes, oldest first:
   a. `git show <hash>` — read the diff AND the commit message. Understand the *intent*, not just
      the text.
   b. Hand-edit OUR files to re-implement the change. Our code may be renamed, moved, or rewritten —
      locate by behavior, not by path. Adapt the change to our architecture.
   If you are unsure how to adapt the change — e.g. the relevant code has diverged too much, or the
      upstream change conflicts with a deliberate design choice in this fork — STOP and ask the
      user. Do not guess.
4. Commit ONCE. Message format:

   merge(upstream): sync to <newest-hash> (skipped: <hash> <subject>, <hash> <subject>)(if any)

5. Verify `git log HEAD..upstream/master` is now empty.

### Do not commit partway through
The entire sync lands as ONE merge commit. If the range is too large
to port safely in one session, STOP and ask the user before starting.

### NyanwChanges.md
- Only record things that actually differ from upstream in the appropriate place in the document.
- Do NOT open a separate "sync upstream" section in `docs/NyanwChanges.md`; ported upstream changes
  are recorded only in the merge commit message.

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **ui**: Reusable Compose UI components that do not depend on app logic
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.
- **mediagen**: Media (image/video) generation SDK with a provider abstraction and built-in providers.
- **oauth**: OAuth 2.0 authorization helpers (loopback callback server, callback foreground service) used by providers.
- **build-logic**: Included Gradle build that supplies the shared `rikkahub.android.library` and
  `rikkahub.android.library.compose` convention plugins to the library modules.
- **app:baselineprofile**: Baseline profile generator for the app.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings.
  Once a conversation is persisted it also holds a `ConversationConfig` snapshot (chat model, reasoning level, search,
  MCP servers, workspace, skills) taken from the assistant; from then on chat-page changes to those settings stay on
  the conversation, and code should read them through `Settings.getAssistantOf(conversation)` /
  `Settings.getChatModelOf(conversation)` instead of the assistant directly. A fork-only `followAssistant` flag on that
  snapshot makes a conversation follow the live assistant settings instead. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt,
  app/src/main/java/me/rerere/rikkahub/data/model/ConversationConfig.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- Repeated action labels are merged into generic resources (`common_cancel`, `common_confirm_action`,
  `common_save`, `common_delete`, `common_close`, `common_ok`, `common_yes`, `common_no`, ...). Do not add
  page-specific duplicates. `common_ok` only acknowledges known information, while
  `common_confirm_action` confirms performing an action.
- Keep every `values-*/strings.xml` aligned to the English `values/strings.xml`: identical keys and the same
  key order/line structure. `values/strings.xml` is grouped into functional sections, each introduced by an
  English XML comment (e.g. `<!-- App -->`, `<!-- Chat page -->`, `<!-- Assistant settings -->`); keys inside a
  section are alphabetical, and a key must sit at the same position in every locale.
- `scripts/strings_grouping.py` owns that layout and is the source of truth for it. After editing strings run
  `python3 scripts/strings_grouping.py check`; run `python3 scripts/strings_grouping.py reorder` to re-sort and
  re-align every locale. New keys must never be appended to the end of the file.
- To add a key, add the English entry and every hand-translated locale entry, then run `reorder`: the script
  places it in the section matching its feature/page and mirrors that position across all locales. If the key
  belongs to a whole new feature/page that fits no section, add a new `("Section", [patterns])` entry to
  `GROUPS` in the script at the logically correct position, then run `reorder` (do not hand-edit section
  comments).
- Always keep `scripts/strings_grouping.py` in sync with the resources in the same change: adding, renaming,
  splitting or removing a section, or adding a key that no pattern matches, requires editing `GROUPS`
  accordingly. Run `check` to confirm, and never leave the script stale relative to `strings.xml`.
- Current sections, in order: App; Common actions; Media and attachments; Avatars, emoji and tags; Export and
  formatting utilities; Thinking; Chat page; Chat history and favorites; Message search; Chat input and
  attachments; Chat message; Chat message tools; Chat charts; Chat queue; Voice mode; Chat history compression;
  Translation; Pickers; Assistant memory; Assistant settings; Prompt extensions; Skills; Workspace; Media
  creation; Image generation; Settings home; Display settings; Theme settings; Model settings; Provider
  settings; MCP settings; Search settings; TTS settings; ASR settings; File settings; Media settings;
  Notification settings; Notifications; Backup; Request and permission logs; Permissions; Web server;
  Statistics; Debug; Errors; Delete dialogs.
- In Simplified and Traditional Chinese, separate adjacent CJK and Latin text with a space; do not add spaces
  around 「」『』“”‘’ quotes and their contents.
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)

### Manual Localization Workflow

- Confirm the target module (e.g. `app`), then add or update the English source entry in `values/strings.xml`
  plus the hand-translated entry in every `values-*/strings.xml` (for `app`, run
  `python3 scripts/strings_grouping.py reorder` afterwards to place and align them).
- Hand-translate the entry into every existing `values-*/strings.xml` in the module; do not rely on
  fallback English.
- Preserve placeholders, escapes, markup, and formatting exactly across locales.
- Verify every locale has the same key and the XML remains well-formed.
