#!/usr/bin/env python3
"""Static dead-code scanner for NyanChat.

The whole repository is read once, indexed into per-file/global word counters, and
every check is answered from that index, so a full run takes a couple of seconds.

Checks (all on by default, pick a subset with --only):

  declarations  Kotlin declarations that are never referenced anywhere else
  files         Kotlin files whose every top-level declaration is unreferenced
  imports       unused Kotlin imports (property delegations / operators excluded)
  strings       string resources (app, search, oauth, ui) that nothing references
  res           unreferenced res/ files and <color|dimen|bool|integer|array|style> entries
  enums         enum entries that are never referenced by name (iterated enums skipped)
  web           web-ui (TS/TSX) modules that are never imported

Intentionally NOT reported (they are used outside normal call sites):

  * @Preview composables and their preview-data helpers (IDE tooling)
  * @SerialName DTO fields, @Serializable constructor properties (kotlinx.serialization)
  * @TypeConverter functions, @Entity/@Dao members (Room)
  * enum entries of enums that are iterated via `entries` / `values()`
  * res entries whose name is not a plain identifier (e.g. `Theme.Rikkahub`)

Usage:
  python3 tools/scan_deadcode.py                 # run every check
  python3 tools/scan_deadcode.py --only imports,strings
  python3 tools/scan_deadcode.py --root /path/to/repo

Review every finding before deleting it: a public library API or an entry point may be unused
inside this repository on purpose, and the filters above only cover the well-known tooling cases.

Exit code is 1 when the selected checks found anything, 0 otherwise.
"""

from __future__ import annotations

import argparse
import collections
import os
import re
import sys
import xml.etree.ElementTree as ET

SKIP_DIRS = {
    ".agents", ".git", ".gradle", ".idea", ".kotlin", "build", "captures", "docs",
    "node_modules", "web-ui",
}
TEXT_EXT = (".kt", ".kts", ".xml", ".java", ".json", ".html", ".js", ".ts", ".tsx",
            ".gradle", ".pro", ".md", ".txt")
BINARY_EXT = (".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".jar", ".aar", ".so",
              ".ttf", ".otf", ".zip", ".db", ".bin", ".mp3", ".wav")
WORD_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
IMPORT_RE = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$")
DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |internal |private |protected |abstract |open |sealed |data |enum |value |"
    r"annotation |inline |suspend |external |actual |expect |override |operator |infix |"
    r"tailrec |const |lateinit |vararg |noinline |crossinline )*"
    r"(class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)"
)
# Imports whose symbol is used as syntax (property delegation `by`, operator `+=`, ...).
SYNTAX_IMPORTS = {
    "getValue", "setValue", "plus", "minus", "times", "div", "rem", "iterator",
    "provideDelegate", "invoke", "get", "set", "contains", "compareTo", "rangeTo",
    "unaryPlus", "unaryMinus", "not", "inc", "dec",
    "component1", "component2", "component3", "component4", "component5", "main",
}
# Annotations that make a declaration live without a Kotlin call site.
KEEP_ANNOTATIONS = (
    "@Preview", "@SerialName", "@Serializable", "@TypeConverter", "@PreviewParameter",
    "@Keep", "@Entity", "@Dao", "@Database", "@Embedded", "@Relation", "@TypeConverters",
    "@Suppress",
)
ROOM_TYPE_DECL_RE = re.compile(r"\b(data class|class|object|interface)\s+(\w+)")
RES_FILE_TYPES = {
    "drawable", "mipmap", "layout", "anim", "animator", "xml", "raw", "font", "menu",
    "navigation", "transition", "color",
}
STRINGS_MODULES = ("app", "search", "oauth", "ui", "ai", "common", "speech", "mediagen",
                   "workspace", "highlight", "document", "material3", "web")
VALUE_TAGS = {"color", "dimen", "bool", "integer", "array", "string-array", "style", "plurals"}


class Index:
    """One pass over the repository, answering all word lookups."""

    def __init__(self, root: str):
        self.root = root
        self.files: list[str] = []
        self.words: dict[str, collections.Counter] = {}
        self.global_words: collections.Counter = collections.Counter()
        self._corpus: str | None = None
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
            for filename in sorted(filenames):
                lower = filename.lower()
                if lower.endswith(BINARY_EXT) or not lower.endswith(TEXT_EXT):
                    continue
                path = os.path.join(dirpath, filename)
                try:
                    text = open(path, encoding="utf-8", errors="replace").read()
                except OSError:
                    continue
                counter = collections.Counter(WORD_RE.findall(text))
                self.files.append(path)
                self.words[path] = counter
                self.global_words.update(counter)

    def rel(self, path: str) -> str:
        return os.path.relpath(path, self.root)

    def count(self, name: str) -> int:
        return self.global_words.get(name, 0)

    def count_in(self, path: str, name: str) -> int:
        return self.words.get(path, collections.Counter()).get(name, 0)

    def read(self, path: str) -> str:
        try:
            return open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            return ""

    def contains_literal(self, needle: str) -> bool:
        if self._corpus is None:
            self._corpus = "\n".join(self.read(path) for path in self.files)
        return needle in self._corpus

    def kotlin_files(self) -> list[str]:
        """Production Kotlin sources (unit/instrumented test and profile sources excluded)."""
        return [
            path for path in self.files
            if path.endswith(".kt")
            and "/src/test/" not in _slash(path)
            and "/androidTest/" not in _slash(path)
            and "/baselineprofile/" not in _slash(path)
        ]


def _slash(path: str) -> str:
    return path.replace(os.sep, "/")


def _lines(path: str) -> list[str]:
    try:
        return open(path, encoding="utf-8", errors="replace").read().split("\n")
    except OSError:
        return []


def _annotation_window(lines: list[str], index: int, size: int = 6) -> str:
    return "\n".join(lines[max(0, index - size):index])


def _enclosing_type_annotations(lines: list[str], index: int, size: int = 12) -> str:
    """Annotations attached to the nearest enclosing class/data class/object/interface."""
    for i in range(index, max(-1, index - 200), -1):
        line = lines[i]
        if line and line[0] not in " \t}":
            match = ROOM_TYPE_DECL_RE.search(line)
            if match:
                return _annotation_window(lines, i, size)
            return ""
    return ""


def _is_kept_declaration(lines: list[str], index: int) -> bool:
    window = _annotation_window(lines, index)
    if any(annotation in window for annotation in KEEP_ANNOTATIONS):
        return True
    text = lines[index].strip()
    # Constructor property of a serialized / Room entity data class.
    if text.endswith(",") or text.endswith(") {"):
        enclosing = _enclosing_type_annotations(lines, index)
        if "@Serializable" in enclosing or "@Entity" in enclosing:
            return True
    return False


def check_declarations(index: Index) -> list[str]:
    findings = []
    for path in index.kotlin_files():
        lines = _lines(path)
        for i, line in enumerate(lines):
            stripped = line.lstrip()
            if not stripped:
                continue
            match = DECL_RE.match(stripped)
            if not match:
                continue
            kind, name = match.group(1), match.group(2)
            if name in ("if", "for", "while", "when", "try", "catch", "return", "else", "init"):
                continue
            prefix = stripped.split(kind)[0]
            if "override " in prefix or "actual " in prefix or "expect " in prefix:
                continue
            if len(name) <= 2:
                continue
            total = index.count(name)
            own = index.count_in(path, name)
            if total == own and total <= 1 and not _is_kept_declaration(lines, i):
                findings.append(
                    f"{index.rel(path)}:{i + 1}: [{kind}] {name}\n"
                    f"      {stripped[:110]}"
                )
    return findings


def check_files(index: Index) -> list[str]:
    findings = []
    kept_markers = ("@Preview", "@Serializable", "@Entity", "@Dao", "@Database", "@Keep",
                    "@file:Suppress")
    for path in index.kotlin_files():
        text = index.read(path)
        if any(marker in text for marker in kept_markers):
            continue
        names = []
        for line in text.split("\n"):
            if not line or line[0] in " \t}":
                continue
            match = DECL_RE.match(line)
            if not match:
                continue
            name = match.group(2)
            if name in ("if", "for", "while", "when", "try", "catch", "return", "else", "init"):
                continue
            names.append(name)
        if not names:
            continue
        if all(index.count(name) == index.count_in(path, name) for name in names):
            findings.append(f"{index.rel(path)}: {', '.join(names)}")
    return findings


def check_imports(index: Index) -> list[str]:
    findings = []
    for path in index.files:
        if not path.endswith(".kt"):
            continue
        lines = _lines(path)
        imports = []
        for i, line in enumerate(lines):
            match = IMPORT_RE.match(line)
            if match:
                imports.append((i + 1, match.group(1), match.group(2) or match.group(1).split(".")[-1]))
        if not imports:
            continue
        body_lines = [line for line in lines if not IMPORT_RE.match(line)]
        body = "\n".join(body_lines)
        for line_number, fq_name, name in imports:
            if name == "*" or name in SYNTAX_IMPORTS:
                continue
            if re.search(r"\b" + re.escape(name) + r"\b", body):
                continue
            findings.append(f"{index.rel(path)}:{line_number}: {fq_name}")
    return findings


def check_strings(index: Index) -> list[str]:
    findings = []
    for module in STRINGS_MODULES:
        path = os.path.join(index.root, module, "src", "main", "res", "values", "strings.xml")
        if not os.path.exists(path):
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            findings.append(f"{index.rel(path)}: cannot parse ({error})")
            continue
        dead = [element.get("name") for element in root.findall("string")
                if index.count(element.get("name")) == 0]
        for name in dead:
            findings.append(f"{module}: {name}")
    return findings


def check_res(index: Index) -> list[str]:
    findings = []
    for dirpath, dirnames, filenames in os.walk(index.root):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        normalised = _slash(dirpath)
        if "/res/" not in normalised:
            continue
        parts = normalised.split("/")
        try:
            res_index = parts.index("res")
        except ValueError:
            continue
        res_type = parts[res_index + 1] if res_index + 1 < len(parts) else ""
        base_type = re.sub(r"-.*$", "", res_type)
        if base_type in RES_FILE_TYPES:
            for filename in filenames:
                name = filename.split(".")[0]
                if name and index.count(name) == 0:
                    findings.append(f"orphan file: {_slash(os.path.join(dirpath, filename))}")
        elif base_type == "values":
            for filename in filenames:
                if not filename.endswith(".xml") or filename == "strings.xml":
                    continue
                path = os.path.join(dirpath, filename)
                try:
                    root = ET.parse(path).getroot()
                except ET.ParseError:
                    continue
                for element in root:
                    if element.tag not in VALUE_TAGS:
                        continue
                    name = element.get("name")
                    if not name:
                        continue
                    if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
                        if index.count(name) > 0:
                            continue
                    elif index.contains_literal(name):
                        continue
                    findings.append(f"orphan value: {index.rel(path)} <{element.tag} name=\"{name}\">")
    return findings


def check_enums(index: Index) -> list[str]:
    findings = []
    entry_re = re.compile(r"^[ \t]+(?:@\w+(?:\([^)]*\))?\s*)*([A-Z][A-Z0-9_]*)\s*(?:[,;(]|$)", re.M)
    for path in index.kotlin_files():
        text = index.read(path)
        for match in re.finditer(r"\benum class (\w+)[^{]*\{", text):
            enum_name = match.group(1)
            if (index.contains_literal(enum_name + ".entries")
                    or index.contains_literal(enum_name + ".values")):
                # Iterated enums are live even when no entry is named explicitly.
                continue
            depth, cursor = 1, match.end()
            while cursor < len(text) and depth > 0:
                if text[cursor] == "{":
                    depth += 1
                elif text[cursor] == "}":
                    depth -= 1
                cursor += 1
            body = text[match.end():cursor - 1]
            for entry in entry_re.finditer(body):
                name = entry.group(1)
                if index.count(name) <= 1:
                    line = text[:match.end() + entry.start()].count("\n") + 1
                    findings.append(f"{index.rel(path)}:{line}: enum {enum_name} entry {name}")
    return findings


def check_web(index: Index) -> list[str]:
    app_dir = os.path.join(index.root, "web-ui", "app")
    if not os.path.isdir(app_dir):
        return []
    sources = []
    for dirpath, dirnames, filenames in os.walk(app_dir):
        dirnames[:] = sorted(d for d in dirnames if d != "node_modules")
        for filename in sorted(filenames):
            if filename.endswith((".ts", ".tsx")):
                sources.append(os.path.join(dirpath, filename))
    extra = [
        os.path.join(index.root, "web-ui", "react-router.config.ts"),
        os.path.join(index.root, "web-ui", "vite.config.ts"),
    ]
    corpus = "\n".join(_read_text(path) for path in sources + extra if os.path.exists(path))
    findings = []
    for path in sources:
        base = os.path.basename(path).rsplit(".", 1)[0]
        if base in ("root", "routes", "entry.client", "entry.server", "index"):
            continue
        if re.search(r"[\"']([^\"']*/)?" + re.escape(base) + r"(\.\w+)?[\"']", corpus):
            continue
        findings.append(f"unimported:{_slash(os.path.relpath(path, os.path.dirname(app_dir)))}")
    return findings


def _read_text(path: str) -> str:
    try:
        return open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return ""


CHECKS = collections.OrderedDict([
    ("declarations", check_declarations),
    ("files", check_files),
    ("imports", check_imports),
    ("strings", check_strings),
    ("res", check_res),
    ("enums", check_enums),
    ("web", check_web),
])


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Static dead-code scanner (see the docstring for what is filtered out).",
    )
    parser.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        help="repository root (default: parent of tools/)")
    parser.add_argument("--only", default="", help="comma separated checks: " + ", ".join(CHECKS))
    args = parser.parse_args()

    selected = [name.strip() for name in args.only.split(",") if name.strip()] or list(CHECKS)
    unknown = [name for name in selected if name not in CHECKS]
    if unknown:
        parser.error(f"unknown check(s): {', '.join(unknown)} (valid: {', '.join(CHECKS)})")

    index = Index(os.path.abspath(args.root))
    total = 0
    for name in selected:
        findings = CHECKS[name](index)
        total += len(findings)
        if findings:
            print(f"== {name} ({len(findings)})")
            for finding in findings:
                print(f"  {finding}")
        else:
            print(f"== {name}: clean")
    print(f"\n{total} finding(s) across {len(index.files)} indexed files")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
