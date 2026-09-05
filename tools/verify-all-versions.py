#!/usr/bin/env python3
"""Runs the mixin reference verification over every source tree in the repository.

The root directory holds the 26.1.2 implementation and `versions/` holds one
directory per additional maintained Minecraft target. This sweep reports, in one
table, which trees would apply cleanly and which carry a mixin reference that no
longer exists in their Minecraft version.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
VERIFIER = REPOSITORY_ROOT / "tools/verify-mixin-targets.py"


def trees() -> list[tuple[str, Path]]:
    found = [("26.1.2 (root)", REPOSITORY_ROOT)]
    versions = REPOSITORY_ROOT / "versions"
    if versions.is_dir():
        for tree in sorted(versions.glob("kohs-inventory-tweaks-*")):
            found.append((tree.name.replace("kohs-inventory-tweaks-", ""), tree))
    return found


def main() -> int:
    failures = 0
    reports: list[tuple[str, str, list[str]]] = []
    for label, tree in trees():
        result = subprocess.run(
            [sys.executable, str(VERIFIER), "--source-root", str(tree)],
            capture_output=True,
            text=True,
        )
        detail = [line for line in result.stdout.splitlines() if line.startswith("FAIL")]
        detail += [line.strip() for line in result.stderr.splitlines() if line.strip()]
        if result.returncode == 0:
            status = "ok"
        elif result.returncode == 2:
            status = "skipped"
        else:
            status = "FAIL"
            failures += 1
        reports.append((label, status, detail))
        print(f"{status:<8} {label}")

    print()
    for label, status, detail in reports:
        if status == "ok" or not detail:
            continue
        print(f"{label}:")
        for line in detail:
            print(f"  {line}")
        print()

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
