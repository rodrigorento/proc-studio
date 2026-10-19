"""Review sheets for the farm art, made from the Blender renders in art/build, and a check of art/assets.yaml.

    python3 art/review.py stages radish            # every stage, watered and dry, above its concept strip
    python3 art/review.py lights radish            # every stage, watered and dry, in each light
    python3 art/review.py frames harvest_radish_ripe_day    # an animation's frames, in order
    python3 art/review.py check                    # every file art/assets.yaml lists exists, and every
                                                   # exported image belongs to an asset in it

Sheets are written to art/build/review/; add --open to open them. Needs Pillow (and PyYAML for check).
"""
import argparse
import glob
import subprocess
import sys
from pathlib import Path

from PIL import Image

ART = Path(__file__).resolve().parent
REPO = ART.parent
RENDERS = ART / "build" / "renders"
ANIM = ART / "build" / "anim"
OUT = ART / "build" / "review"
BACKGROUND = (22, 23, 26)  # the app's #16171A
TILE = 300

sys.path.insert(0, str(ART))
from export_android import STAGES  # noqa: E402
from farmkit import style  # noqa: E402


def stages(crop: str) -> Path:
    """Watered and dry rows of the crop's stages in daylight, above the concept strip they were modelled from."""
    rows = [[RENDERS / f"crop_{crop}_{stage}_{wet}_day.png" for stage in STAGES[crop]] for wet in ("wet", "dry")]
    sheet = _grid(rows)
    concept = ART / "concepts" / crop / "stages_wet.png"
    if concept.exists():
        strip = Image.open(concept).convert("RGB")
        strip = strip.resize((sheet.width, round(strip.height * sheet.width / strip.width)), Image.Resampling.LANCZOS)
        both = Image.new("RGB", (sheet.width, sheet.height + strip.height), BACKGROUND)
        both.paste(sheet, (0, 0))
        both.paste(strip, (0, sheet.height))
        sheet = both
    return _save(sheet, f"{crop}_stages.png")


def lights(crop: str) -> Path:
    """Each stage, watered then dry, across the four lights."""
    rows = [[RENDERS / f"crop_{crop}_{stage}_{wet}_{light}.png" for light in style.LIGHTS]
            for stage in STAGES[crop] for wet in ("wet", "dry")]
    return _save(_grid(rows, tile=220), f"{crop}_lights.png")


def frames(name: str) -> Path:
    """An animation's frames, eight to a row. Watering plays over the dry carrot card of its light, as in the app;
    a harvest is already a whole card."""
    paths = sorted(ANIM.glob(f"{name}_[0-9][0-9].png"))
    if not paths:
        raise SystemExit(f"No frames named {name}_NN.png in {ANIM}")
    under = None
    if name.startswith("water_"):
        under = RENDERS / f"crop_carrot_ripe_dry_{name.rsplit('_', 1)[1]}.png"
    rows = [paths[k:k + 8] for k in range(0, len(paths), 8)]
    return _save(_grid(rows, tile=240, under=under), f"{name}.png")


def check() -> int:
    """Problems with art/assets.yaml: listed files that don't exist, and exported images no asset claims."""
    import yaml

    assets = yaml.safe_load((ART / "assets.yaml").read_text())
    problems, claimed = [], set()
    for group, entries in assets.items():
        for name, asset in entries.items():
            for key in ("reference", "model"):
                if asset.get(key) and not (REPO / asset[key]).exists():
                    problems.append(f"{group}.{name}: {key} {asset[key]} is missing")
            for path in asset.get("concepts", []) + asset.get("options", []):
                if not (REPO / path).exists():
                    problems.append(f"{group}.{name}: concept {path} is missing")
            for pattern in asset.get("outputs", []):
                found = set(glob.glob(str(REPO / pattern)))
                if not found:
                    problems.append(f"{group}.{name}: nothing matches {pattern}")
                claimed |= found
    exported = set(glob.glob(str(REPO / "app/src/main/res/drawable-nodpi/*.webp")))
    exported |= set(glob.glob(str(REPO / "app/src/main/res/mipmap-*/ic_launcher*.webp")))
    for path in sorted(exported - claimed):
        problems.append(f"{Path(path).relative_to(REPO)} is not listed in art/assets.yaml")
    for problem in problems:
        print(problem)
    count = sum(len(entries) for entries in assets.values())
    print(f"{count} assets, {len(claimed)} exported files: " + (f"{len(problems)} problems" if problems else "all good"))
    return 1 if problems else 0


def _grid(rows: list[list[Path]], tile: int = TILE, under: Path | None = None) -> Image.Image:
    columns = max(len(row) for row in rows)
    sheet = Image.new("RGB", (columns * tile, len(rows) * tile), BACKGROUND)
    base = Image.open(under).convert("RGBA") if under else None
    for r, row in enumerate(rows):
        for c, path in enumerate(row):
            if not path.exists():
                raise SystemExit(f"Missing {path.relative_to(REPO)}: render it first")
            image = Image.open(path).convert("RGBA")
            card = Image.new("RGBA", image.size, BACKGROUND + (255,))
            if base:
                card.alpha_composite(base.resize(image.size))
            card.alpha_composite(image)
            sheet.paste(card.convert("RGB").resize((tile, tile), Image.Resampling.LANCZOS), (c * tile, r * tile))
    return sheet


def _save(sheet: Image.Image, name: str) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    sheet.save(path)
    print(path.relative_to(REPO))
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    opening = argparse.ArgumentParser(add_help=False)
    opening.add_argument("--open", action="store_true", help="open the sheet when it is made")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("stages", "lights"):
        commands.add_parser(command, parents=[opening]).add_argument("crop", choices=list(STAGES))
    commands.add_parser("frames", parents=[opening]).add_argument(
        "name", help="e.g. water_can_day or harvest_carrot_ripe_night")
    commands.add_parser("check")
    args = parser.parse_args()
    if args.command == "check":
        return check()
    path = {"stages": lambda: stages(args.crop), "lights": lambda: lights(args.crop),
            "frames": lambda: frames(args.name)}[args.command]()
    if args.open:
        subprocess.run(["open", str(path)], check=False)
    return 0


if __name__ == "__main__":
    sys.exit(main())
