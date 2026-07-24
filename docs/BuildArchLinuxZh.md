# Arch Linux 构建 arm64 Release APK

当前分支已移除 Firebase，将应用 ID 改为 `moe.nyanw.nyanchat`，并且只打包
`arm64-v8a`，并且改成用 OpenJDK 21。下面按实际下载顺序列出构建所需内容。

## 下载清单

| 下载内容 | 下载源 | 下载量 | 安装后占用 | 下载命令 |
|---|---|---:|---:|---|
| Java 21、Node.js、pnpm 等 | Arch 软件源；是否为国内镜像取决于本机 pacman 配置 | 约 150–300 MB | 约 500 MB–1 GB | `sudo pacman -S --needed git jdk21-openjdk nodejs pnpm unzip wget` |
| Android Command-line Tools | Google，无可靠的新版本国内镜像 | 173 MB | 166 MB | 见下方命令 1 |
| Android Platform、Build Tools、Platform Tools | Google `sdkmanager`，无可靠 API 37 国内镜像 | 约 143 MB | 327 MB | 见下方命令 2 |
| NDK 28.2、CMake 3.22.1 | Google `sdkmanager`，无可靠新版本国内镜像 | 约 744 MB | 约 2.3 GB | 见下方命令 2 |
| Material Color Utilities 子模块 | GitHub | 数 MB | 6 MB | `git submodule update --init --recursive` |
| Web UI npm 依赖 | npmmirror 国内镜像 | 约 100–300 MB | 407 MB | 见下方命令 3 |
| Gradle 9.4.1 | 阿里云镜像，项目已配置 | 132 MB | 291 MB | 运行 `./gradlew` 时自动下载 |
| Android/Kotlin/Compose 等 Gradle 依赖 | 阿里云优先，缺包时回退 Google、Maven Central、JitPack | 约 1–2 GB | 本机缓存约 1.8 GB | 构建时自动下载 |

Android SDK 相关内容合计下载约 1 GB，安装后约占 2.7 GB；首次完整构建结束后，
`~/.gradle` 通常还会占约 2 GB。

### 命令 1：下载 Android Command-line Tools

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
cd /tmp
wget -c \
  https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
unzip commandlinetools-linux-14742923_latest.zip
mv cmdline-tools "$HOME/Android/Sdk/cmdline-tools/latest"
```

Command-line Tools 只是 SDK 管理器，主要提供 `sdkmanager`；真正用于编译的 Platform、
Build Tools、NDK 和 CMake 由下一条命令下载。

### 命令 2：下载 Android SDK、NDK 和 CMake

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

这里必须使用 `android-37.0`，而不是 `android-37`。`workspace` 模块包含 C/C++ 源码，
因此 NDK 和 CMake 不能省略。

如果 NDK 下载中断后出现 `did not have a source.properties file`：

```bash
rm -rf "$ANDROID_HOME/ndk/28.2.13676358"
sdkmanager --sdk_root="$ANDROID_HOME" "ndk;28.2.13676358"
```

### 命令 3：下载 Web UI 依赖

```bash
pnpm --dir web-ui install \
  --frozen-lockfile \
  --registry=https://registry.npmmirror.com
```
