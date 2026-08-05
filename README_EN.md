<div align="center">
  <img src="docs/icon.svg" alt="App Icon" width="100" />
  <h1>NyanChat</h1>

  An Android LLM chat client based on <a href="https://github.com/rikkahub/rikkahub">RikkaHub</a>, with additional features and UI improvements.

  [简体中文](README.md) | English

</div>

## ✨ Improvements over RikkaHub: (Why NyanChat)

- ⚠️ **Delete Confirmation** — Confirmation dialog before deleting messages or conversations (yes, the original didn't have one)
- 💰 **Message Price Display** — See the cost of each message directly for better budget management *(though price calculation may be inaccurate when caching is enabled)*
- 🔍 **Better Message Search** — Search by title, or precise text matching (the original only had fuzzy search)
- 🖼️ **Better Chat Attachment Management** — View all attachments in chat history and jump directly to the message using that attachment, making storage management easier
- 💼 **Workspace Export/Import** — When exporting backups, workspace files are also fully preserved! (the original only saves chat text and attachments)
- 📂 **Batch Move Conversations** — Multi-select and move chats to different folders for better conversation management
- 📍 **Device Context Awareness** — Can access location, battery level, and more, bringing AI closer to reality
- 🧹 **Refactored settings pages and UI layout** — Cleaner layout, easier to get started, adapted for screen rounded corners
- 🔤 **Character Count** — See how many characters the AI replied with!
- 💾 **Cache Friendly** — Optimized Claude model caching for **cost savings**
- 🔑 **Permission Confirmation** — For each permission, you can individually set `Deny` / `Ask Every Time` / `Always Allow`, for **better privacy protection**. NyanChat also **logs permission usage** for your peace of mind.

> See [NyanwChanges.md](docs/NyanwChanges.md) for the full list of changes.

## 🔮 Planned Future Features
- 🧠 **Experimental [Tidal Memory](https://github.com/0xblewalker/tidal-memory) support** — From [@3nvoy](https://www.xiaohongshu.com/user/profile/67f3dfbe000000000e0113e5)'s open source project! Makes AI memory more human-like and optimizes cache hit rate.
- 📱 **Instant Messaging Style Interaction (?)**: Send multiple short messages continuously; the LLM replies when it sees fit, or responds to multiple messages at once, just like chatting with a real person!

## 🎯 Existing RikkaHub Features

- 🔄 Support for multiple providers and custom APIs.
- 🧠 Precise memory (but consumes more tokens)
- 📦 proot-based Linux workspace
- 🔍 Web search (Bing, Perplexity, etc.)
- 🛠️ MCP support
- 🖥️ Web remote access (chat from your computer while NyanChat is running on your phone!)
- 🖼️ Image generation API support

## 📝 About Issues / PRs
- ✅ If you find a bug or want a small feature, feel free to open an issue!
- ❌ **Please do not submit PRs with large changes. If you want to implement a feature, please propose it in Issues first rather than implementing and submitting code directly.**
- ❌ **Please do not include lengthy code analysis in Issues.** Just describe the problem and how to reproduce it. (Though a brief analysis of the problematic code is fine, just keep it short.)
- NyanChat will not add Agent features that use accessibility permissions, adb (Shizuku), or root to automate phone control, nor will it add bot features (like QQ bots) (as such features are too dangerous and hard to test). For such features, please use [RikkaHub Agent](https://github.com/ExTV/rikkahub-agent) or [Operit](https://github.com/AAswordman/Operit).
> However, features implemented through Android APIs are still welcome!
- ❌ **Please do not request NyanChat to built-in new model providers**: NyanChat will only include providers already present in RikkaHub. For other providers, please use a custom `base_url`.
- ❌ **For issues with specific model requests or image generation, please first download the official RikkaHub to check if the issue exists there.** If it does, please submit Issues / PRs to RikkaHub (with screenshots reproducing the issue in RikkaHub). (Since NyanChat has barely modified code related to constructing or sending LLM requests, except for the cache toggle.)
- NyanChat will not make significant modifications to the web version (web server), nor add new features to it, unless there are obvious bugs.

## 🔄 Compatibility with RikkaHub

- Supports importing data from RikkaHub 2.x and exporting data to RikkaHub Latest (**chat text can be migrated bidirectionally without data loss!**).
- Notes:
  - Due to RikkaHub limitations, some data (such as workspaces, assistant avatars, and backgrounds) cannot be imported from RikkaHub.
  - When exporting to RikkaHub, NyanChat-specific settings and metadata (such as model pricing) will be lost.
  - NyanChat only supports importing data from RikkaHub 2.x. To import data from RikkaHub 1.x, first update RikkaHub to 2.x and complete the database migration. Migrated data cannot be re-imported into RikkaHub 1.x.
  - Older versions of RikkaHub (e.g., `2.3.x`) cannot read NyanChat archives. Please update RikkaHub to `2.4.x` (the latest version) first.
- NyanChat **follows RikkaHub updates** — most future RikkaHub features will be merged into NyanChat, so you can use it with confidence.

## 🔒 Privacy

**Only with user consent**, NyanChat accesses specified information from the device (such as app usage time, clipboard, location, battery level), and **sends it only to the LLM provider you specify** — not anywhere else. API keys are stored locally with minimal risk of leakage.

## ⚠️ Disclaimer

- Improper configuration or inappropriate use may result in unnecessary token consumption. NyanChat is not responsible for any costs incurred.
- NyanChat is not responsible for any content generated by language models, nor does it guarantee the accuracy of generated content. Users should ensure their use complies with applicable laws and regulations, and carefully verify the correctness of content.
- NyanChat does not provide API keys. Users should obtain API keys from appropriate model providers.
- NyanChat is heavily developed with AI assistance and may contain undiscovered bugs. **NyanChat is not responsible for any consequences resulting from bugs**, but issues and PRs are welcome.

## 🚀 Get NyanChat

Due to rapid ongoing development, pre-built APKs are not currently available. Please compile and install it yourself ~~or wait patiently~~.

~~If you want to build it yourself, you can refer to the [build instructions](docs/BuildArchLinuxZh.md) (*though this documentation is quite rough and barely useful*)~~

## 📄 License

Open source under [AGPL-3.0](LICENSE).

> Thanks again to [re-ovo](https://github.com/re-ovo) for creating RikkaHub!
