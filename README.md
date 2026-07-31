<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>NyanChat</h1>

  一个基于 <a href="https://github.com/rikkahub/rikkahub">RikkaHub</a> 的 Android LLM 聊天客户端，添加了额外功能和 UI 改进。

  简体中文 | [English](README_EN.md)

</div>

## ✨ 在 RikkaHub 上进行的改进：（为什么用 NyanChat）

- 📍 **设备状态感知** — 可获取位置、电量等信息，让 AI 更接近现实
- 💾 **缓存友好** — 对可能破坏 kv cache 的操作加上了提示，并且优化了 Claude 模型的缓存，**更省钱**
- 💰 **消息价格显示** — 直接看到每条消息花的钱，更清楚用量
- 📂 **批量移动对话** — 多选后可以将聊天移动到不同文件夹，更好地管理聊天记录
- 🔑 **权限确认** — 对于每项权限，都可以单独设置`不允许`/`每次都需要手动授权`/`永久允许`，**隐私更安全**。同时，NyanChat 会**记录权限使用日志**，用起来更放心。
- ⚠️ **删除确认** — 删除消息或对话前会出现**确认弹窗**（没错，原版没有）
- 🧹 **重构了部分设置页面和 UI 布局，并适配屏幕圆角** — 更清晰的布局，更容易上手

> 如果想知道 NyanChat 具体改了什么，请看 [NyanwChanges.md](docs/NyanwChanges.md)

## 🎯 RikkaHub 已有的功能

- 🔄 支持多个不同供应商和自定义 API。
- 🧠 精确记忆（但消耗更多 token ）
- 📦 基于 proot 的 Linux 工作区
- 🔍 联网搜索（ Bing、Perplexity 等）
- 🛠️ MCP 支持
- 🖥️ Web 远程访问（打开手机的 NyanChat 后，在电脑端也能聊天！）
- 🖼️ 支持生图 API

## 🔄 与 RikkaHub 的兼容性

- 支持从 RikkaHub 2.x 导入数据，也可以导出数据到 RikkaHub Latest（**聊天记录文字可以双向无损迁移！**）。
- 注意：
  - 由于 RikkaHub 限制，部分数据（如工作区、助手头像和背景）无法从 RikkaHub 导入。且**RikkaHub 导出的数据中可能不含部分聊天文件或图片，因此导入聊天记录后可能丢失图片或文件**。
  - 导出到 RikkaHub 时，NyanChat 独有的设置和元数据（如模型价格）会丢失。
  - NyanChat 仅支持导入 RikkaHub 2.x 的数据。如果要从 RikkaHub 1.x 导入数据，请先把 RikkaHub 更新至 2.x 并完成数据库迁移。迁移后的数据不能重新导入 RikkaHub 1.x。
  - 旧版本 RikkaHub（如`2.3.x`）无法读取 NyanChat 存档，请先将 RikkaHub 更新至 `2.4.x`（目前的最新版）。
- NyanChat **会跟随 RikkaHub 更新**，RikkaHub 未来的大部分新功能都将被合入 NyanChat，可以放心使用。

## 🔒 隐私

**只有经过用户同意**，NyanChat 才会从设备获取指定信息（如应用使用时间、剪贴板、位置、电量），且**仅发送给用户指定的 LLM 供应商**，不会发往其他任何地方。API Key 仅存储在本地，泄露风险低。

## ⚠️ 免责声明

- 配置不当或不恰当的使用可能导致不必要的大量 token 消耗。NyanChat 不对由此产生的费用负责。
- NyanChat 不对语言模型生成的任何内容负责，也不保证模型生成的内容的正确性。用户应自行确保其使用符合相关法律法规，并仔细甄别内容对错。
- NyanChat 不提供 API Key，请用户自行从合适的模型提供商获取 API Key。
- NyanChat 大量使用 AI 辅助开发，可能含有未被发现的 bug。**NyanChat 不对 bug 造成的任何后果负责**，但欢迎提 Issue 或 PR 改进。

## 🚀 获取 NyanChat

请从 [Releases](https://github.com/atNyaChan/NyanChat/releases/latest) 中下载 apk 并安装；**目前不支持 iOS**。

~~如果你想自己编译，那么可以参考[编译说明](docs/BuildArchLinuxZh.md)（*不过这个文档写得很粗糙，有跟没有差不多x*）~~

## 📄 许可证

基于 [AGPL-3.0](LICENSE) 开源。

> 再次感谢 [re-ovo](https://github.com/re-ovo) 编写的 RikkaHub！
