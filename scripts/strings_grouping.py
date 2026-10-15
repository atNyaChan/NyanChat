#!/usr/bin/env python3
"""Keep the app's localized string resources grouped and aligned.

`app/src/main/res/values/strings.xml` is organised into functional sections, each
introduced by an English XML comment such as ``<!-- Assistant settings -->``.
Keys inside a section are kept in alphabetical order, and every
``values-*/strings.xml`` file mirrors the exact same section / key order so a
given key always sits on the same line in every locale.

This script owns that layout:

* ``check`` (default) verifies that the section list below matches the English
  file, that every English key is covered by a section, and that all locales
  share the same key set and key order. It changes nothing.
* ``reorder`` rebuilds every locale from the English key set using the section
  list below, then rewrites the files in place.

Add a genuinely new feature/page by adding a section to ``GROUPS`` (at the
logically correct position) and running ``reorder``; a new key that fits an
existing feature simply belongs in that feature's section. New keys must never
be appended to the end of the file.

Usage (from the repository root):
    python3 scripts/strings_grouping.py check
    python3 scripts/strings_grouping.py reorder
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

# --------------------------------------------------------------------------- #
# Layout definition: (section comment, [regex patterns])
# A key is assigned to the first section whose pattern fully matches it.
# --------------------------------------------------------------------------- #
GROUPS: list[tuple[str, list[str]]] = [
    ("App", [
        r"^app_name$", r"^settings$", r"^dev_mode_badge$",
        r"^about_page_", r"^title_activity_safe_mode$", r"^safe_mode_",
        r"^create_fork$", r"^share_handler_page_title$", r"^share_model_title$",
        r"^menu_page_", r"^user_default_name$",
    ]),
    ("Common actions", [
        r"^common_", r"^back$", r"^send$", r"^stop$", r"^more_options$",
        r"^confirm_delete$", r"^cancel_edit$", r"^copied$", r"^copy$",
        r"^edit$", r"^editing$", r"^calculating$",
    ]),
    ("Media and attachments", [
        r"^(photo|video|audio|file)$", r"^take_picture$", r"^sketch",
        r"^image_sav", r"^video_player_", r"^shortcut_take_picture$",
        r"^ocr_processing$",
    ]),
    ("Avatars, emoji and tags", [
        r"^avatar_", r"^emoji_picker_", r"^tag_input_",
    ]),
    ("Export and formatting utilities", [
        r"^export_import_", r"^export_share_", r"^export_to_file", r"^export_image_warning$",
        r"^text_area_", r"^mermaid_export_", r"^code_block_", r"^markdown_table_title$",
        r"^citations_count$",
    ]),
    ("Thinking", [
        r"^deep_thinking", r"^reasoning_title$", r"^reasoning_picker_",
    ]),

    # ---------------- Chat ----------------
    ("Chat page", [
        r"^chat_page_new_chat$", r"^chat_page_no_conversations$", r"^chat_page_scroll_to_bottom$",
        r"^chat_page_edit_title", r"^chat_page_edit_nickname$", r"^chat_page_nickname_placeholder$",
        r"^chat_page_auto_generate_title$", r"^chat_page_today$", r"^chat_page_yesterday$",
        r"^chat_page_message_count$", r"^chat_page_check_fast_model_settings$",
        r"^chat_page_clear_all_errors$", r"^chat_page_dismiss_error$",
        r"^chat_page_follow_assistant_settings", r"^chat_page_select_model_first$",
        r"^chat_page_stop_generation_before_delete$", r"^chat_page_context_message_limit_current$",
        r"^chat_page_conversation_system_prompt", r"^chat_page_menu_", r"^use_web_search$",
        r"^chat_page_export_",
    ]),
    ("Chat history and favorites", [
        r"^chat_page_uncategorized$", r"^chat_page_current_location$",
        r"^chat_page_create_folder", r"^chat_page_rename_folder$", r"^chat_page_folder_",
        r"^chat_page_delete_folder_generating$", r"^chat_page_confirm_move",
        r"^chat_page_confirm_single_move_desc$", r"^chat_page_move_", r"^chat_page_sort_",
        r"^conversation_", r"^pin_chat$", r"^pinned_chats$", r"^unpin_chat$",
        r"^favorite_page_", r"^history_page_",
    ]),
    ("Message search", [
        r"^search_page_",
    ]),
    ("Chat input and attachments", [
        r"^chat_input_", r"^chat_size_", r"^tools_warning$", r"^asr_button_",
    ]),
    ("Chat message", [
        r"^chat_message_add_favorite$", r"^chat_message_remove_favorite$",
        r"^chat_message_attachment_deleted$", r"^chat_message_cannot_edit_tool_call$",
        r"^regenerate", r"^chat_generation_",
    ]),
    ("Chat message tools", [
        r"^chat_message_tool_", r"^tool_ui_", r"^local_tool_",
    ]),
    ("Chat charts", [
        r"^chart_mode_", r"^chart_series",
    ]),
    ("Chat queue", [
        r"^chat_page_queue_",
    ]),
    ("Voice mode", [
        r"^chat_page_voice_",
    ]),
    ("Chat history compression", [
        r"^chat_page_compress", r"^error_title_compress_conversation$",
    ]),
    ("Translation", [
        r"^translate$", r"^translating$", r"^translation_", r"^translator_page_",
        r"^collapse_translation$", r"^expand_translation$",
    ]),

    # ---------------- Pickers ----------------
    ("Pickers", [
        r"^model_list_", r"^mcp_picker_", r"^search_picker_title$", r"^assistant_picker_title$",
        r"^extension_selector_", r"^extension_content_",
    ]),

    # ---------------- Assistant ----------------
    ("Assistant memory", [
        r"^assistant_page_memory_", r"^memory_dream", r"^memory_file_",
    ]),
    ("Assistant settings", [
        r"^assistant_page_", r"^assistant_detail_", r"^assistant_delete_",
        r"^assistant_importer_", r"^assistant_picker_title$",
    ]),

    # ---------------- Extensions ----------------
    ("Prompt extensions", [
        r"^prompt_page_", r"^lorebook_page_title$", r"^mode_injection_page_title$",
        r"^quick_messages_page_", r"^extensions_page_", r"^placeholder_",
    ]),
    ("Skills", [
        r"^skills_page_", r"^skill_detail_",
    ]),

    # ---------------- Workspace ----------------
    ("Workspace", [
        r"^workspace_",
    ]),

    # ---------------- Media creation ----------------
    ("Media creation", [
        r"^media_creation_(?!notification_)", r"^media_kind_", r"^media_provider_type_",
        r"^shortcut_media_creation$",
    ]),
    ("Image generation", [
        r"^imggen_page_",
    ]),

    # ---------------- Settings ----------------
    ("Settings home", [
        r"^setting_general_", r"^setting_page_about_desc$", r"^setting_page_assistant_desc$",
        r"^setting_page_built_in_tools", r"^setting_page_chat_storage", r"^setting_page_config",
        r"^setting_page_chat_and_prompts$", r"^setting_page_data_backup_desc$",
        r"^setting_page_data_settings$", r"^setting_page_default_model_desc$",
        r"^setting_page_mode_injection_desc$", r"^setting_page_lorebook_desc$",
        r"^setting_page_preferences", r"^setting_page_dynamic_color",
        r"^setting_page_mcp_desc$", r"^setting_page_font_settings$",
        r"^setting_page_formula_code_settings$", r"^setting_page_model_and_services$",
        r"^setting_page_providers_desc$", r"^setting_page_search_service_desc$",
        r"^setting_page_tts_service", r"^setting_page_theme_setting$",
        r"^setting_page_chat_settings$", r"^setting_page_interface_settings$",
        r"^setting_page_system_settings$", r"^setting_page_more_settings$",
        r"^setting_page_media", r"^setting_page_color_mode",
    ]),
    ("Display settings", [
        r"^setting_display_page_(?!notification_message_generated)",
    ]),
    ("Theme settings", [
        r"^setting_theme_page_", r"^theme_name_",
    ]),
    ("Model settings", [
        r"^setting_model_page_", r"^share_model_title$",
    ]),
    ("Provider settings", [
        r"^setting_provider_", r"^default_provider_",
    ]),
    ("MCP settings", [
        r"^setting_mcp_",
    ]),
    ("Search settings", [
        r"^setting_page_search_", r"^search_detail_", r"^search_ability_", r"^built_in_search_title$",
    ]),
    ("TTS settings", [
        r"^setting_tts_page_", r"^setting_page_tts_settings$", r"^test_tts$", r"^tts$",
        r"^speech_tab_tts$",
    ]),
    ("ASR settings", [
        r"^setting_asr_", r"^setting_page_asr_", r"^speech_tab_asr$",
    ]),
    ("File settings", [
        r"^setting_files_",
    ]),
    ("Media settings", [
        r"^setting_media_page_",
    ]),
    ("Notification settings", [
        r"^setting_display_page_notification_message_generated",
        r"^setting_notification_",
    ]),
    ("Notifications", [
        r"^notification_channel_", r"^notification_live_", r"^notification_web_",
        r"^media_creation_notification_",
    ]),

    # ---------------- Data ----------------
    ("Backup", [
        r"^backup_page_",
    ]),
    ("Request and permission logs", [
        r"^log_page_", r"^setting_page_request_logs", r"^setting_page_permission_logs_desc$",
        r"^permission_log_page_", r"^request_intercept_",
    ]),
    ("Permissions", [
        r"^permission_",
    ]),
    ("Web server", [
        r"^setting_page_web_server",
    ]),
    ("Statistics", [
        r"^stats_page_",
    ]),
    ("Debug", [
        r"^debug_page_",
    ]),

    # ---------------- Errors / dialogs ----------------
    ("Errors", [
        r"^error_",
    ]),
    ("Delete dialogs", [
        r"^delete_dialog_", r"^delete_type_",
    ]),
]

# --------------------------------------------------------------------------- #
# Files
# --------------------------------------------------------------------------- #
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")
LOCALES = ["values", "values-ar", "values-ja", "values-ko-rKR", "values-ru", "values-zh",
           "values-zh-rTW"]
ENGLISH = "values/strings.xml"

STRING_RE = re.compile(r'\s*<string name="([^"]+)"')
COMMENT_RE = re.compile(r'\s*<!--\s*(.*?)\s*-->\s*$')


def resources_path(locale: str) -> str:
    return os.path.join(RES_DIR, locale, "strings.xml")


def parse(text: str) -> tuple[dict[str, str], list[str], list[str]]:
    """Return (key -> raw line, key order, section comments in file order)."""
    entries: dict[str, str] = {}
    order: list[str] = []
    comments: list[str] = []
    for line in text.splitlines():
        m = STRING_RE.match(line)
        if m:
            key = m.group(1)
            entries[key] = line
            order.append(key)
            continue
        c = COMMENT_RE.match(line)
        if c:
            comments.append(c.group(1))
    return entries, order, comments


def compute_layout(entries: dict[str, str]) -> list[tuple[str, str]]:
    """Map every key to a section and return the ordered layout.

    Returns a list of ("comment", text) and ("key", key) tuples.
    """
    assigned: dict[str, str] = {}
    layout: list[tuple[str, str]] = []
    for comment, patterns in GROUPS:
        regexes = [re.compile(p) for p in patterns]
        bucket = [k for k in entries if k not in assigned and any(r.search(k) for r in regexes)]
        if bucket:
            bucket.sort()
            layout.append(("comment", comment))
            layout.extend(("key", k) for k in bucket)
            for k in bucket:
                assigned[k] = comment
    unmatched = [k for k in entries if k not in assigned]
    if unmatched:
        raise SystemExit(
            "Keys not covered by any section in scripts/strings_grouping.py:\n  "
            + "\n  ".join(sorted(unmatched))
            + "\nAdd a matching section (or pattern) to GROUPS."
        )
    return layout


def render(layout: list[tuple[str, str]], entries: dict[str, str]) -> str:
    out = ["<?xml version='1.0' encoding='UTF-8'?>", "<resources>"]
    for kind, value in layout:
        if kind == "comment":
            out.append(f"  <!-- {value} -->")
        else:
            out.append(entries[value])
    out.append("</resources>")
    return "\n".join(out) + "\n"


def read(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def collect_all() -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    english_entries, _, _ = parse(read(os.path.join(RES_DIR, ENGLISH)))
    locale_entries: dict[str, dict[str, str]] = {}
    for locale in LOCALES:
        entries, _, _ = parse(read(resources_path(locale)))
        locale_entries[locale] = entries
    return english_entries, locale_entries


def check() -> int:
    english_entries, locale_entries = collect_all()
    layout = compute_layout(english_entries)
    expected_order = [value for kind, value in layout if kind == "key"]
    expected_comments = [value for kind, value in layout if kind == "comment"]

    problems: list[str] = []

    _, english_order, english_comments = parse(read(os.path.join(RES_DIR, ENGLISH)))
    if english_comments != expected_comments:
        problems.append(
            "English section comments differ from scripts/strings_grouping.py:\n"
            f"  file : {english_comments}\n  script: {expected_comments}"
        )
    if english_order != expected_order:
        problems.append("English key order does not match the computed layout.")

    for locale in LOCALES:
        entries = locale_entries[locale]
        _, order, comments = parse(read(resources_path(locale)))
        missing = set(english_entries) - set(entries)
        extra = set(entries) - set(english_entries)
        if missing:
            problems.append(f"{locale}: missing keys: {sorted(missing)}")
        if extra:
            problems.append(f"{locale}: unknown keys: {sorted(extra)}")
        if order != expected_order:
            problems.append(f"{locale}: key order differs from the layout.")
        if comments != expected_comments:
            problems.append(f"{locale}: section comments differ from the layout.")

    # Every file must stay parseable as XML.
    for locale in LOCALES:
        try:
            ET.parse(resources_path(locale))
        except ET.ParseError as e:
            problems.append(f"{locale}: XML parse error: {e}")

    if problems:
        print("strings grouping check FAILED:")
        for p in problems:
            print(" -", p)
        return 1

    print(f"OK: {len(english_entries)} keys in {len(expected_comments)} sections across "
          f"{len(LOCALES)} locales.")
    return 0


def reorder() -> int:
    english_entries, locale_entries = collect_all()
    layout = compute_layout(english_entries)

    # Validate every locale before writing anything so a mismatch cannot cause a
    # partially rewritten tree.
    for locale in LOCALES:
        missing = set(english_entries) - set(locale_entries[locale])
        extra = set(locale_entries[locale]) - set(english_entries)
        if missing or extra:
            print(f"Refusing to write: {locale} key set differs from English.")
            if missing:
                print("  missing:", sorted(missing))
            if extra:
                print("  unknown:", sorted(extra))
            return 1

    for locale in LOCALES:
        path = resources_path(locale)
        with open(path, "w", encoding="utf-8") as f:
            f.write(render(layout, locale_entries[locale]))
        print("wrote", os.path.relpath(path, REPO_ROOT))

    print(f"Reordered {len(english_entries)} keys into {len(GROUPS)} sections.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", nargs="?", choices=["check", "reorder"], default="check")
    args = parser.parse_args()
    return check() if args.command == "check" else reorder()


if __name__ == "__main__":
    sys.exit(main())
