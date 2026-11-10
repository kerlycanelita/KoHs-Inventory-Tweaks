#!/usr/bin/env python3
"""Writes rpr.json, Minecraft's pre-reform Russian, from ru_ru.json.

    python tools/lang/make_rpr.py

Run it after every change to ru_ru.json so the two stay in step;
tools/verify-translations.py then checks that both carry every key.

Only the spelling rules that need no dictionary are applied, so nothing is guessed:
  - и before a vowel or й is written і;
  - a word ending in a hard consonant takes ъ, except abbreviations;
  - ё is written е.
ѣ, ѳ, ѵ and the old adjective endings depend on etymology and grammar, so they are
left in modern spelling rather than risked.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

LANG = Path(__file__).resolve().parents[2] / "src/main/resources/assets/kohs_inventory_tweaks/lang"
VOWELS = set("аеёиоуыэюяіАЕЁИОУЫЭЮЯІ")
HARD_ENDINGS = set("бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ")
# Shortened words written with a dot (or in capitals) that keep no ъ.
ABBREVIATIONS = {"вкл", "выкл", "сек"}
WORD = re.compile(r"[А-Яа-яЁё]+")


def convert_word(word: str) -> str:
    letters = list(word.replace("ё", "е").replace("Ё", "Е"))
    for index in range(len(letters) - 1):
        following = letters[index + 1]
        if letters[index] in "иИ" and (following in VOWELS or following in "йЙ"):
            letters[index] = "і" if letters[index] == "и" else "І"
    word = "".join(letters)
    if word[-1] not in HARD_ENDINGS:
        return word
    # A word of several letters with no vowel is an abbreviation (МБ, ПКМ, бл);
    # one-letter prepositions (в, к, с) do take ъ.
    if (len(word) > 1 and not any(letter in VOWELS for letter in word)) or word.lower() in ABBREVIATIONS:
        return word
    return word + ("Ъ" if len(word) > 1 and word.isupper() else "ъ")


def convert(text: str) -> str:
    return WORD.sub(lambda match: convert_word(match.group(0)), text)


def main() -> int:
    source = LANG / "ru_ru.json"
    raw = source.read_bytes().decode("utf-8")
    newline = "\r\n" if "\r\n" in raw else "\n"
    table = json.loads(raw)
    lines = [f"  {json.dumps(key)}: {json.dumps(convert(value), ensure_ascii=False)}" for key, value in table.items()]
    text = "{" + newline + ("," + newline).join(lines) + newline + "}" + newline
    (LANG / "rpr.json").write_bytes(text.encode("utf-8"))
    print(f"rpr.json: {len(table)} entries from ru_ru.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
