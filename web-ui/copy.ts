import {
  copyFileSync,
  mkdirSync,
  readdirSync,
  rmSync,
  statSync,
} from "node:fs";
import { join, dirname, extname } from "node:path";

const SOURCE_DIR = "./build/client";
const TARGET_DIR = "../web/src/main/resources/static";

// KaTeX 的 @font-face 依次提供 woff2/woff/ttf 三种格式，而 Android WebView 等现代浏览器
// 只取首个受支持的 woff2，因此跳过体积更大的 woff/ttf 兜底字体，避免 APK 内重复打包。
const SKIPPED_FONT_EXTENSIONS = new Set([".woff", ".ttf"]);

function copyDirectory(src: string, dest: string) {
  // 确保目标目录存在
  mkdirSync(dest, { recursive: true });

  const entries = readdirSync(src, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);

    if (entry.isDirectory()) {
      copyDirectory(srcPath, destPath);
    } else {
      if (SKIPPED_FONT_EXTENSIONS.has(extname(entry.name).toLowerCase())) {
        continue;
      }
      // 确保父目录存在
      mkdirSync(dirname(destPath), { recursive: true });
      copyFileSync(srcPath, destPath);
    }
  }
}

try {
  console.log("📦 Starting build output copy...");
  console.log(`   Source: ${SOURCE_DIR}`);
  console.log(`   Target: ${TARGET_DIR}`);

  // 检查源目录是否存在
  try {
    statSync(SOURCE_DIR);
  } catch {
    console.error(`❌ Source directory not found: ${SOURCE_DIR}`);
    console.error("   Please run build first.");
    process.exit(1);
  }

  // 清理目标目录（如果存在）
  try {
    rmSync(TARGET_DIR, { recursive: true, force: true });
    console.log("🧹 Cleaned target directory");
  } catch (err) {
    // 目标目录不存在，忽略错误
  }

  // 复制文件
  copyDirectory(SOURCE_DIR, TARGET_DIR);

  console.log("✅ Build output copied successfully!");
} catch (error) {
  console.error("❌ Copy failed:", error);
  process.exit(1);
}
