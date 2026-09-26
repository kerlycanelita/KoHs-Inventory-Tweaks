#!/usr/bin/env python3
"""Composes the A/B takes recorded by the debug companion into page media and in-game previews.

    python tools/media/compose_recordings.py --recordings debug/kohs-inventory-debug-26.1.2/run/recordings

Each clip has an "-off" and an "-on" take. Both are aligned on the key press (or
on their first frame when the take has no press) and sampled on a common clock,
so the two halves always show the same instant. Outputs:

- docs/media/<clip>.gif and .webp: side by side, labelled, for the README and Modrinth.
- assets/.../textures/gui/preview/<clip>.png: a vertical strip of small side-by-side
  frames for the hover preview, with its timing in preview/<clip>.json.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "src/main/resources/assets/kohs_inventory_tweaks/textures/gui/preview"
MEDIA = REPO / "docs/media"

CLIPS = {
    # clip: (title, window start ms rel. to press, window end ms rel. to press, source step ms, playback fps)
    "fast": ("Super Fast Inventory", -250, 700, 1000 / 30, 12),   # 2.5x slow motion
    "center": ("Center Mouse Fix", -150, 2500, 1000 / 15, 15),
    "animations": ("Remove inventory animations", 0, 2400, 1000 / 15, 15),
}
PURPLE = (184, 107, 255)
GREY = (140, 131, 152)
BACKGROUND = (16, 8, 24)


def read_take(directory: Path) -> tuple[list[tuple[int, int]], dict[str, int]]:
    frames, events = [], {}
    for line in (directory / "take.txt").read_text(encoding="utf-8").splitlines():
        kind, name, value = line.split()
        if kind == "frame":
            frames.append((int(name), int(value)))
        else:
            events[name] = int(value)
    return frames, events


CROP = (0.22, 0.14, 0.78, 0.86)  # the inventory sits in the middle of the window


def crop(image: Image.Image) -> Image.Image:
    w, h = image.size
    return image.crop((round(w * CROP[0]), round(h * CROP[1]), round(w * CROP[2]), round(h * CROP[3])))


def overlay(canvas: Image.Image, x: int, y: int, t_ms: float, shown_ms: float, events_ms: dict, accent, big, small) -> None:
    """Key cap and timer, drawn crisp at the output resolution.

    The timer only says "opened" once the frame on screen was drawn after the
    opening, so the label never runs ahead of the picture.
    """
    if "press" not in events_ms:
        return
    draw = ImageDraw.Draw(canvas)
    lit = 0 <= t_ms - events_ms["press"] < 180
    draw.rounded_rectangle((x, y, x + 26, y + 26), radius=4, fill=accent if lit else (35, 22, 52), outline=accent)
    draw.text((x + 8, y + 4), "E", font=big, fill=(26, 11, 42) if lit else (215, 196, 242))
    if t_ms < events_ms["press"]:
        return
    if "open" in events_ms and shown_ms > events_ms["open"]:
        text, color = f"opened after {round(events_ms['open'] - events_ms['press'])} ms", (156, 247, 180)
    else:
        text, color = f"waiting {round(t_ms - events_ms['press'])} ms", (255, 210, 122)
    draw.rounded_rectangle((x + 32, y + 2, x + 40 + draw.textlength(text, font=small), y + 24), radius=4, fill=(16, 8, 24))
    draw.text((x + 36, y + 6), text, font=small, fill=color)


def frame_at(directory: Path, frames: list[tuple[int, int]], micros: float) -> tuple[Image.Image, int]:
    chosen, chosen_when = frames[0]
    for index, when in frames:
        if when <= micros:
            chosen, chosen_when = index, when
        else:
            break
    path = directory / f"frame_{chosen:05d}.png"
    while not path.exists() and chosen > 0:
        chosen -= 1
        path = directory / f"frame_{chosen:05d}.png"
    return Image.open(path).convert("RGB"), chosen_when


def font(size: int) -> ImageFont.ImageFont:
    for name in ("arialbd.ttf", "segoeuib.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def compose(recordings: Path, clip: str) -> dict:
    title, start, end, step, fps = CLIPS[clip]
    off_dir, on_dir = recordings / f"{clip}-off", recordings / f"{clip}-on"
    off_frames, off_events = read_take(off_dir)
    on_frames, on_events = read_take(on_dir)
    off_zero = off_events.get("press", off_frames[0][1])
    on_zero = on_events.get("press", on_frames[0][1])
    latency = {
        "off": (off_events["open"] - off_events["press"]) // 1000 if "open" in off_events and "press" in off_events else None,
        "on": (on_events["open"] - on_events["press"]) // 1000 if "open" in on_events and "press" in on_events else None,
    }

    page_width = 960
    label_height = 34
    caption_height = 28
    off_ms = {k: (v - off_zero) / 1000 for k, v in off_events.items()}
    on_ms = {k: (v - on_zero) / 1000 for k, v in on_events.items()}
    big, small = font(18), font(13)
    page_frames, strip_frames = [], []
    t = start
    while t <= end:
        left, left_when = frame_at(off_dir, off_frames, off_zero + t * 1000)
        right, right_when = frame_at(on_dir, on_frames, on_zero + t * 1000)
        left, right = crop(left), crop(right)
        left_shown = (left_when - off_zero) / 1000
        right_shown = (right_when - on_zero) / 1000
        half = page_width // 2
        height = round(left.height * half / left.width)
        canvas = Image.new("RGB", (page_width, label_height + height + caption_height), BACKGROUND)
        canvas.paste(left.resize((half, height), Image.LANCZOS), (0, label_height))
        canvas.paste(right.resize((half, height), Image.LANCZOS), (half, label_height))
        overlay(canvas, 10, label_height + 10, t, left_shown, off_ms, GREY, big, small)
        overlay(canvas, half + 10, label_height + 10, t, right_shown, on_ms, PURPLE, big, small)
        draw = ImageDraw.Draw(canvas)
        draw.rectangle((half - 1, 0, half + 1, canvas.height), fill=PURPLE)
        draw.text((12, 8), "Vanilla", font=big, fill=GREY)
        draw.text((half + 12, 8), "KoHs Inventory Tweaks", font=big, fill=PURPLE)
        note = title + ("  ·  2.5× slow motion" if clip == "fast" else "")
        width = draw.textlength(note, font=small)
        draw.text(((page_width - width) / 2, label_height + height + 7), note, font=small, fill=(215, 196, 242))
        page_frames.append(canvas)

        strip_half = 128
        strip_height = round(left.height * strip_half / left.width)
        # left/right are already cropped to the inventory region
        tile = Image.new("RGB", (strip_half * 2 + 2, strip_height), BACKGROUND)
        tile.paste(left.resize((strip_half, strip_height), Image.LANCZOS), (0, 0))
        tile.paste(right.resize((strip_half, strip_height), Image.LANCZOS), (strip_half + 2, 0))
        ImageDraw.Draw(tile).rectangle((strip_half, 0, strip_half + 1, strip_height), fill=PURPLE)
        strip_frames.append(tile)
        t += step

    MEDIA.mkdir(parents=True, exist_ok=True)
    duration = round(1000 / fps)
    quantized = [frame.quantize(colors=128, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE) for frame in page_frames]
    quantized[0].save(MEDIA / f"{clip}.gif", save_all=True, append_images=quantized[1:], duration=duration, loop=0, optimize=True)
    page_frames[0].save(MEDIA / f"{clip}.webp", save_all=True, append_images=page_frames[1:], duration=duration, loop=0, quality=80, method=6)

    # In-game hover preview: every other frame keeps the sheet small.
    chosen = strip_frames[::2] if len(strip_frames) > 24 else strip_frames
    tile_w, tile_h = chosen[0].size
    sheet = Image.new("RGB", (tile_w, tile_h * len(chosen)), BACKGROUND)
    for index, tile in enumerate(chosen):
        sheet.paste(tile, (0, index * tile_h))
    ASSETS.mkdir(parents=True, exist_ok=True)
    sheet.save(ASSETS / f"{clip}.png", optimize=True)
    meta = {
        "frames": len(chosen),
        "frameWidth": tile_w,
        "frameHeight": tile_h,
        "frameMillis": round(duration * (len(strip_frames) / len(chosen))),
        "latencyOffMillis": latency["off"],
        "latencyOnMillis": latency["on"],
    }
    (ASSETS / f"{clip}.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    return {
        "clip": clip,
        "pageFrames": len(page_frames),
        "gif": (MEDIA / f"{clip}.gif").stat().st_size,
        "webp": (MEDIA / f"{clip}.webp").stat().st_size,
        "sheet": (ASSETS / f"{clip}.png").stat().st_size,
        **meta,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recordings", type=Path, required=True)
    parser.add_argument("--clip", choices=sorted(CLIPS), action="append")
    arguments = parser.parse_args()
    for clip in arguments.clip or list(CLIPS):
        print(json.dumps(compose(arguments.recordings, clip)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
