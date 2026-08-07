# Arch Linux 构建 arm64 Release APK

本文档面向在 Arch Linux 上从源码构建 NyanChat（better-rikkahub）Release APK 的开发者。

当前分支相对上游 RikkaHub 的构建差异：

- 已移除 Firebase，构建时不需要 `google-services.json`
- 应用 ID 改为 `moe.nyanw.nyanchat`
- 仅打包 `arm64-v8a`
- 统一使用 OpenJDK 21

## 下载清单

| 下载内容 | 下载源 | 下载量 | 安装后占用 | 下载命令 |
|---|---|---:|---:|---|
| Java 21、Node.js、pnpm 等 | Arch 软件源；是否为国内镜像取决于本机 pacman 配置 | 约 150–300 MB | 约 500 MB–1 GB | `sudo pacman -S --needed git jdk21-openjdk nodejs pnpm unzip wget` |
| Android Command-line Tools | Google，无可靠的新版本国内镜像 | 173 MB | 166 MB | 见步骤 2 |
| Android Platform、Build Tools、Platform Tools | Google `sdkmanager`，无可靠 API 37 国内镜像 | 约 143 MB | 327 MB | 见步骤 3 |
| NDK 28.2、CMake 3.22.1 | Google `sdkmanager`，无可靠新版本国内镜像 | 约 744 MB | 约 2.3 GB | 见步骤 3 |
| Material Color Utilities 子模块 | GitHub | 数 MB | 6 MB | 见步骤 1 |
| Web UI npm 依赖 | npmmirror 国内镜像 | 约 100–300 MB | 407 MB | 见步骤 4 |
| Gradle 9.4.1 | 阿里云镜像，项目已配置 | 132 MB | 291 MB | 运行 `./gradlew` 时自动下载 |
| Android/Kotlin/Compose 等 Gradle 依赖 | 阿里云优先，缺包时回退 Google、Maven Central、JitPack | 约 1–2 GB | 本机缓存约 1.8 GB | 构建时自动下载 |

Android SDK 相关内容合计下载约 1 GB，安装后约占 2.7 GB；首次完整构建结束后，
`~/.gradle` 通常还会占约 2 GB。

## 构建步骤

### 步骤 1：安装系统依赖并获取源码

```bash
# 安装必要的系统包
sudo pacman -S --needed git jdk21-openjdk nodejs pnpm unzip wget

# 克隆仓库
git clone https://github.com/jacob-sheng/better-rikkahub.git
cd better-rikkahub

# 初始化子模块（Material Color Utilities）
git submodule update --init --recursive
```

### 步骤 2：下载 Android Command-line Tools

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
cd /tmp
wget -c \
  https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
unzip commandlinetools-linux-14742923_latest.zip
mv cmdline-tools "$HOME/Android/Sdk/cmdline-tools/latest"
cd -
```

Command-line Tools 只是 SDK 管理器，主要提供 `sdkmanager`；真正用于编译的 Platform、
Build Tools、NDK 和 CMake 由下一步下载。

### 步骤 3：下载 Android SDK、NDK 和 CMake

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses

sdkmanager \
  --sdk_root="$ANDROID_HOME" \
  --channel=3 \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;37.0.0" \
  "ndk;28.2.13676358" \
  "cmake;3.22.1"
```

> **注意**：这里必须使用 `android-37.0`，而不是 `android-37`。`workspace` 模块包含
> C/C++ 源码，因此 NDK 和 CMake 不能省略。

### 步骤 4：安装 Web UI 依赖

```bash
pnpm --dir web-ui install \
  --frozen-lockfile \
  --registry=https://registry.npmmirror.com
```

### 步骤 5：持久化环境变量

上述步骤中的环境变量在终端关闭后会丢失。将以下内容追加到 shell 配置文件中
（`~/.bashrc`、`~/.zshrc` 或其他对应的配置文件）：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

追加后执行 `source ~/.bashrc`（或对应文件）使其生效。

### 步骤 6：配置 Release 签名（可选）

Release 构建需要签名证书。在仓库根目录创建或编辑 `local.properties`，添加以下内容：

```properties
storeFile=.signing/release.jks
storePassword=你的密钥库密码
keyAlias=你的密钥别名
keyPassword=你的密钥密码
```

如果没有签名证书，可以用 `keytool` 生成一个：

```bash
mkdir -p .signing
keytool -genkeypair \
  -alias release \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -keystore .signing/release.jks
```

> **提示**：如果只需要 Debug 构建，可以跳过此步骤，直接使用步骤 7 中的
> `assembleDebug` 命令。

### 步骤 7：构建 APK

```bash
# 构建 Release APK（需要先完成步骤 6 的签名配置）
./gradlew assembleRelease

# 或者构建 Debug APK（无需签名配置）
./gradlew assembleDebug
```

首次构建会自动下载 Gradle 9.4.1 和全部 Android/Kotlin/Compose 依赖，耗时较长。

### 步骤 8：获取构建产物

构建完成后，APK 位于：

```
# Release
app/build/outputs/apk/release/app-arm64-v8a-release.apk

# Debug
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 常见问题

### NDK 下载中断后报错 `did not have a source.properties file`

删除残留目录后重新下载：

```bash
rm -rf "$ANDROID_HOME/ndk/28.2.13676358"
sdkmanager --sdk_root="$ANDROID_HOME" "ndk;28.2.13676358"
```

### 构建报错找不到 `ANDROID_HOME`

确认已按步骤 5 持久化环境变量，并且当前终端已 source 过配置文件。可通过
`echo $ANDROID_HOME` 验证。

### Web UI 构建失败

`web` 模块会在 `preBuild` 阶段自动构建 `web-ui/` 并复制静态资源。如果此步骤失败，
检查是否已按步骤 4 安装了 Web UI 依赖，以及 `pnpm` 是否可用。
