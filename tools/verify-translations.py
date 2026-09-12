#!/usr/bin/env python3
"""Checks that every translation key the client asks for exists in every language.

A key that reaches the screen without an entry renders as its own identifier, so
this is the cheapest way to catch a configuration tab that was extended without
its strings. Reports, per language file:

  missing  - referenced by the code, absent from the file
  unused   - present in the file, never referenced by the code
  gaps     - present in the reference language, absent from a translation

Usage:
    python tools/verify-translations.py [--source-root .]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# Component.translatable("..."), addStatus(..., "..."), addIssue(..., "...") and
# every other place a bare key literal is handed to the game.
KEY_LITERAL = re.compile(r"\"((?:screen|notification|key|category|herzium)\.[a-z0-9_.]+)\"(\s*\+)?")
REFERENCE_LANGUAGE = "en_us.json"


def referenced_keys(source_root: Path) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    """Returns (exact keys, dynamic prefixes) with the files that reference them.

    A literal immediately followed by `+` is concatenated with an enum suffix at
    runtime, so it is a prefix rather than a key. Those are checked by requiring
    the language file to hold at least one key under the prefix; a missing suffix
    still shows up as a gap against the reference language.
    """
    keys: dict[str, list[str]] = {}
    prefixes: dict[str, list[str]] = {}
    for path in sorted((source_root / "src").rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for key, concatenated in KEY_LITERAL.findall(text):
            target = prefixes if concatenated else keys
            target.setdefault(key, []).append(path.name)
        # The tweaks metadata builds these suffixes when drawing each card.
        if path.name == "InventoryTweakOption.java":
            for option, key in re.findall(r'^\s*(\w+)\("(screen\.[a-z0-9_.]+)", Category\.', text, re.M):
                for suffix in (".summary", ".description"):
                    keys.setdefault(key + suffix, []).append(path.name)
                if option in {"HELD_MOUSE", "ANIMATIONS"}:
                    for suffix in (".warning.title", ".warning.description"):
                        keys.setdefault(key + suffix, []).append(path.name)
    return keys, prefixes


def language_files(source_root: Path) -> list[Path]:
    lang_dir = source_root / "src/main/resources/assets/kohs_inventory_tweaks/lang"
    return sorted(lang_dir.glob("*.json")) if lang_dir.is_dir() else []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", default=".", type=Path)
    parser.add_argument("--show-unused", action="store_true")
    arguments = parser.parse_args()

    source_root = arguments.source_root.resolve()
    files = language_files(source_root)
    if not files:
        print(f"no language files under {source_root}", file=sys.stderr)
        return 2

    referenced, prefixes = referenced_keys(source_root)
    print(f"source : {source_root}")
    print(f"keys   : {len(referenced)} referenced, {len(prefixes)} built at runtime")
    print()

    tables = {path.name: json.loads(path.read_text(encoding="utf-8")) for path in files}
    reference = tables.get(REFERENCE_LANGUAGE, {})
    failures = 0

    for name, table in tables.items():
        missing = sorted(key for key in referenced if key not in table)
        missing += sorted(
            prefix for prefix in prefixes
            if not any(key.startswith(prefix) for key in table)
        )
        gaps = sorted(key for key in reference if key not in table)
        unused = sorted(
            key for key in table
            if key not in referenced and not any(key.startswith(p) for p in prefixes)
        )

        status = "ok" if not missing and not gaps else "FAIL"
        print(f"{status:<5} {name}: {len(table)} entries")
        if missing:
            failures += 1
            for key in missing:
                sources = referenced.get(key) or prefixes.get(key) or []
                print(f"        missing  {key}   ({', '.join(sorted(set(sources)))})")
        if gaps and name != REFERENCE_LANGUAGE:
            failures += 1
            for key in gaps:
                print(f"        gap      {key}")
        if unused and arguments.show_unused:
            for key in unused:
                print(f"        unused   {key}")

    print()
    if failures:
        print("translation tables are incomplete")
        return 1
    print("every referenced key resolves in every language")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
