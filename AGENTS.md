# Repository Guidelines

## Project Overview

NyanChat is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

## Agent Change Workflow

- Agent 对仓库完成任何代码、资源或配置修改后，都必须完整阅读并更新 `docs/NyanwChanges.md`，在文档的恰当位置简要记录本次修改内容，不要把修改的内容一直加到文档最后。
- 修改完成后需要扫描仓库里是否有不需要的代码或者 locale string。如果有，那么把它们删掉。
- Agent 不得自行运行 Gradle、Android Studio、`pnpm` 或其他编译、构建、测试、Lint 命令。完成修改和静态检查后，应将编译与运行验证留给用户手动执行。

## Upstream Sync Policy (IMPORTANT)

This repo is a **hard fork** (~20k+ lines diverged). We record upstream
merges in git history, but NEVER let git auto-merge file contents.

### Absolute rules
- The ONLY allowed merge command is:
  `git merge -s ours upstream/main --no-commit --no-ff`
  (`-s ours` keeps our tree untouched; it only records ancestry.)
- NEVER use default `git merge`, `git rebase`, `git cherry-pick`, `git pull`.
- NEVER resolve conflict markers (`<<<<<<<`). With `-s ours` they cannot appear; if you see any, abort immediately and stop.
- NEVER copy upstream files wholesale over ours.

### Workflow: one sync = one merge commit
1. List unported commits: `git log --oneline HEAD..upstream/main`
2. Start the merge (this changes NO files — verify `git diff HEAD` is empty):
   `git merge -s ours upstream/main --no-commit --no-ff`
3. Now port EACH upstream commit's changes, oldest first:
   a. `git show <hash>` — read the diff AND the commit message. Understand the *intent*, not just the text.
   b. Hand-edit OUR files to re-implement the change. Our code may be renamed, moved, or rewritten — locate by behavior, not by path. Adapt the change to our architecture.
   If you are unsure how to adapt the change — e.g. the relevant code has diverged too much, or the upstream change conflicts with a deliberate design choice in this fork — STOP and ask the user. Do not guess.
4. Commit ONCE. Message format:

   merge(upstream): sync to <newest-hash>

   ported:
   - <hash> <subject>
   - <hash> <subject>
   skipped:
   - <hash> <subject>

5. Verify `git log HEAD..upstream/main` is now empty.

### Do not commit partway through
The entire sync lands as ONE merge commit. If the range is too large
to port safely in one session, STOP and ask the user before starting.

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

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
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.
