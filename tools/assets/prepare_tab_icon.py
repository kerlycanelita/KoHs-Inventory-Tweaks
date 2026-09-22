"""Package an approved transparent source as a crisp, consistently sized tab icon.

Usage: python tools/assets/prepare_tab_icon.py source.png icon_id
Artwork and alpha come from the source; this only crops, pads and resizes it.
"""
from pathlib import Path
import argparse
import json
import re
from PIL import Image


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("icon_id")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9_]*", args.icon_id):
        parser.error("icon_id must be a lowercase resource name")
    with Image.open(args.source) as source:
        if source.mode != "RGBA" or source.getchannel("A").getextrema()[0] != 0:
            parser.error("source needs real RGBA transparency; background removal belongs in the artwork step")
        bounds = source.getchannel("A").getbbox()
        if bounds is None:
            parser.error("source is empty")
        artwork = source.crop(bounds)
        artwork.thumbnail((56, 56), Image.Resampling.NEAREST)
        canvas = Image.new("RGBA", (64, 64))
        canvas.alpha_composite(artwork, ((64 - artwork.width) // 2, (64 - artwork.height) // 2))
        destination = Path(__file__).resolve().parents[2] / "src/main/resources/assets/kohs_inventory_tweaks/textures/gui/icons"
        destination.mkdir(parents=True, exist_ok=True)
        path = destination / f"{args.icon_id}.png"
        canvas.save(path)
        path.with_name(path.name + ".mcmeta").write_text(
            json.dumps({"texture": {"blur": False, "clamp": True}}) + "\n", encoding="utf-8")
        print(f"[OK] {path.name}: 64x64 RGBA, nearest-neighbor")


if __name__ == "__main__":
    main()
