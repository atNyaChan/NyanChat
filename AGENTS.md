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
  title plus card/list layout, keep 8dp between the title bar and the first item.
- **Bottom sheets**: always use `BottomSheetDefaults.DragHandle()` with drag-to-dismiss, wrap the
  content height up to 95% of the available height, and use top padding 0dp / bottom 8dp (plus the
  navigation-bar inset). Fixed bottom button rows keep 8dp above and 8dp below, and use
  "Cancel / Confirm" via `common_confirm_action`.
- **Colors** (`CustomColors`, app/src/main/java/me/rerere/rikkahub/ui/theme/Color.kt): page
  backgrounds, the top bar and all drawers use `surface`; card/list fills use `surfaceContainerLow`
  (`CustomColors.cardColorsOnSurfaceContainer`, `CustomColors.listItemColors`). Every
  `AlertDialog`/`BasicAlertDialog` uses `MaterialTheme.colorScheme.surface`.
- **Shapes**: `OutlinedTextField`s use the theme's `Shapes.extraSmall` (16dp); search fields use the
  fixed 20dp `SearchFieldShape`
  (app/src/main/java/me/rerere/rikkahub/ui/components/ui/SearchFieldShape.kt) instead of the
  screen-corner shape. Large containers (`CardGroup`, page cards, dialogs) adapt to the physical
  screen corner via `rememberScreenEdgeCornerShape`
  (app/src/main/java/me/rerere/rikkahub/ui/theme/ScreenCornerShape.kt), falling back to 24dp (the
  "直角" mode uses 4dp); message bubbles, thinking blocks, shared outlined list cards and file item
  cards keep their own shape.
- **Shared components**: loading uses `AppLoadingIndicator`; color picking uses the shared
  `ColorPicker` (ui/src/main/java/me/rerere/ui/common/ColorPicker.kt); custom request Header/Body
  editing uses `CustomRequestProperties`
  (app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/PropertyEditor.kt) in the provider
  drawer, assistant request settings and model advanced settings; whether a request log is an LLM
  call is decided only by `isLlmRequest(log)`
  (app/src/main/java/me/rerere/rikkahub/data/ai/RequestInterceptController.kt).
- **Feedback**: `LocalToaster` errors open a scrollable, selectable, copyable dialog; success,
  warning and short messages use the Android system Toast. Do not add the old custom colored in-app
  banner.
- All scrollable pages (including the chat message list) use the platform stretch overscroll effect.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # Build Debug APK
./gradlew test                   # Run all  JVM tests
./gradlew lint                   # Run Android Lint
```

Build configuration is unified across the project: JDK 21 for the project, the Gradle daemon,
toolchain declarations and CI; `compileSdk` 37.2 (Android SDK Platform 37) and `targetSdk` 37,
`minSdk` 28; Java and Kotlin `jvmTarget` 21; Build-Tools pinned to 37.0.0. Library modules get this
from the `rikkahub.android.library` / `rikkahub.android.library.compose` convention plugins in the
`build-logic` included build; app-level keep rules live in `app/src/main/keepRules/rikkahub.keep`.
Do not raise `minSdk` or change the Java/Kotlin target per module.

## Agent Change Workflow

- After any code, resource, or configuration change, the agent must fully read and update
  `docs/NyanwChanges.md`, recording the change in the appropriate place in the document — do not
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
  key order/line structure. When adding a key, add it at the same place in every locale.
- In Simplified and Traditional Chinese, separate adjacent CJK and Latin text with a space; do not add spaces
  around 「」『』“”‘’ quotes and their contents.
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)

### Manual Localization Workflow

- Confirm the target module (e.g. `app`), then add/update the English source entry in
  `values/strings.xml`.
- Hand-translate the entry into every existing `values-*/strings.xml` in the module; do not rely on
  fallback English.
- Preserve placeholders, escapes, markup, and formatting exactly across locales.
- Verify every locale has the same key and the XML remains well-formed.
