"""Validate the transparent KoHs tab icon package for the active client source set."""
from __future__ import annotations

import json
from pathlib import Path
import sys

try:
    from PIL import Image
except ImportError as error:  # pragma: no cover - useful diagnostic for release machines
    print(f"Pillow is required: {error}", file=sys.stderr)
    raise SystemExit(2)


ROOT = Path(__file__).resolve().parents[2]
ICON_DIR = ROOT / "src/main/resources/assets/kohs_inventory_tweaks/textures/gui/icons"
EXPECTED = {
    "cursor", "tweaks", "issues", "customization", "highlighter", "scaler",
    "accessibility", "performance", "safety",
    "inventory", "chest", "shulker", "ender_chest", "barrel",
}


def main() -> int:
    errors: list[str] = []
    for icon_id in sorted(EXPECTED):
        path = ICON_DIR / f"{icon_id}.png"
        if not path.is_file():
            errors.append(f"missing {path.name}")
            continue
        try:
            with Image.open(path) as image:
                image.load()
                if image.size != (64, 64):
                    errors.append(f"{path.name}: expected 64x64, got {image.size}")
                if image.mode != "RGBA":
                    errors.append(f"{path.name}: expected RGBA, got {image.mode}")
                alpha = image.getchannel("A")
                if alpha.getbbox() is None:
                    errors.append(f"{path.name}: icon is fully transparent")
                corners = [alpha.getpixel(point) for point in ((0, 0), (63, 0), (0, 63), (63, 63))]
                if any(corner != 0 for corner in corners):
                    errors.append(f"{path.name}: opaque corner suggests a background rectangle")
        except Exception as error:  # malformed PNGs are reported with their filename
            errors.append(f"{path.name}: {error}")
        metadata = path.with_name(path.name + ".mcmeta")
        if not metadata.is_file():
            errors.append(f"missing {metadata.name}")
        else:
            try:
                settings = json.loads(metadata.read_text(encoding="utf-8"))
                if settings.get("texture", {}).get("blur") is not False:
                    errors.append(f"{metadata.name}: blur must be false for pixel art")
            except Exception as error:
                errors.append(f"{metadata.name}: invalid JSON ({error})")

    if errors:
        for error in errors:
            print(f"[ERROR] {error}")
        return 1
    print(f"[OK] {len(EXPECTED)} transparent KoHs tab icons validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
