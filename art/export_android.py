"""Turn the Blender renders into the app's images and the Kotlin that looks them up.

    python3 art/export_android.py   # after farmkit.render.render_all("art/build/renders") and, for the
                                    # animations, farmkit.anim.render_water / render_plant / render_harvest
                                    # ("art/build/anim")

Writes WebPs (with transparency) to app/src/main/res/drawable-nodpi/, generates CropArt.kt (the still cards and
seed packets) and CropMotion.kt (the animation frames), all in the day light (LIGHT), writes the launcher icon to the mipmap folders, checks that
every image the app can ask for exists, and reports the total size. Harvest frames keep only what differs from the
empty bed, so they play over its card. Props come from farmkit.stills.render_all("art/build/props"), and the home
screen's field (FieldArt.kt) from farmkit.field.render_all("art/build/field"). Needs Pillow.
"""
import json
import re
import sys
from functools import reduce
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ART = Path(__file__).resolve().parent
REPO = ART.parent
RENDERS = ART / "build" / "renders"
DRAWABLES = REPO / "app/src/main/res/drawable-nodpi"
KOTLIN = REPO / "app/src/main/java/com/example/projdiic/CropArt.kt"
QUALITY = 88  # lossy colour; the transparency is kept lossless
BUDGET_MB = 12.0  # all the farm art in the app: still cards, animations and (later) props
# The app shows the farm in one light. farmkit still renders every light in style.LIGHTS (the showroom and the
# review sheets use them); the export takes this one and leaves the light out of the app's names.
LIGHT = "day"
ANIM = ART / "build" / "anim"
MOTION_KOTLIN = REPO / "app/src/main/java/com/example/projdiic/CropMotion.kt"
MOTION_QUALITY = 72  # moving frames hide more compression; 72 looks the same as 82 and is 15% smaller
# Harvest pixels count as changed when they differ from the empty bed by more than MATTE_STRONG (0-255), or by
# more than MATTE_WEAK within MATTE_REACH pixels of one that does: render noise (up to ~16 at night) never
# reaches MATTE_STRONG on its own, while soft shadow edges next to a crop are kept.
MATTE_WEAK, MATTE_STRONG, MATTE_REACH = 10, 24, 7
PROPS = ART / "build" / "props"
RES = REPO / "app/src/main/res"
PROP_QUALITY = 90
ICON_BACKGROUND = "#2B5E16"  # the launcher icon's green, behind the rendered bed and sprout
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
FIELD = ART / "build" / "field"
FIELD_KOTLIN = REPO / "app/src/main/java/com/example/projdiic/FieldArt.kt"
FIELD_IMAGES = ["field_meadow", "field_plot_marked", "field_bed_shadow"]

sys.path.insert(0, str(ART))
from farmkit import style  # noqa: E402  (style.py has no Blender imports)

def _stages() -> dict[str, list[str]]:
    """Stages per crop, as farmkit.crops.<crop>.STAGES defines them (those modules need Blender, so read the text)."""
    found = {}
    for path in sorted((ART / "farmkit/crops").glob("*.py")):
        block = re.search(r"^STAGES = \{\n(.*?)\n\}", path.read_text(), re.S | re.M)
        if block:
            found[path.stem] = re.findall(r'^    "(\w+)": \{', block.group(1), re.M)
    return found


STAGES = _stages()


def expected_images() -> list[str]:
    """The app's card names; each is rendered as <name>_<LIGHT>.png."""
    names = []
    for wet in ("wet", "dry"):
        names.append(f"bed_empty_{wet}")
        for crop, stages in STAGES.items():
            names += [f"crop_{crop}_{stage}_{wet}" for stage in stages]
    return names


def export() -> None:
    if LIGHT not in style.LIGHTS:
        raise SystemExit(f"LIGHT {LIGHT!r} is not one of farmkit's lights: {list(style.LIGHTS)}")
    cards = export_cards()
    motion = export_motion()
    props = export_props()
    field = export_field()
    mb = (cards + motion + props + field) / 1_000_000
    print(f"Total {mb:.1f} MB (budget {BUDGET_MB:.0f} MB)")
    if mb > BUDGET_MB:
        print("Over budget: lower QUALITY / MOTION_QUALITY, or the render sizes in farmkit.")


def export_cards() -> int:
    """The still cards and CropArt.kt. Returns the bytes written."""
    names = expected_images()
    missing = [name for name in names if not (RENDERS / f"{name}_{LIGHT}.png").exists()]
    if missing:
        raise SystemExit(f"{len(missing)} renders missing from {RENDERS}, e.g. {missing[:3]}")
    DRAWABLES.mkdir(parents=True, exist_ok=True)
    total = 0
    written = set()
    for name in names:
        png, out = RENDERS / f"{name}_{LIGHT}.png", DRAWABLES / f"{name}.webp"
        if not out.exists() or out.stat().st_mtime < png.stat().st_mtime:  # unchanged renders keep their WebP
            Image.open(png).convert("RGBA").save(out, "WEBP", quality=QUALITY, alpha_quality=100, method=6)
        total += out.stat().st_size
        written.add(out)
    for old in (set(DRAWABLES.glob("crop_*.webp")) | set(DRAWABLES.glob("bed_empty_*.webp"))) - written:
        old.unlink()  # cards the app no longer uses, such as other lights
    KOTLIN.write_text(kotlin(names + seed_packets()))
    print(f"{len(names)} cards, {total / 1_000_000:.1f} MB -> {DRAWABLES.relative_to(REPO)}; generated {KOTLIN.name}")
    return total


TEMPLATE = """// Generated by art/export_android.py from the Blender renders. Don't edit by hand: re-run the export.
package com.example.projdiic

import androidx.annotation.DrawableRes

/** The farm's pre-rendered beds, in day light: each crop's stages, watered or dry. */
object CropArt {
    /** Each crop's growth stages, youngest first. */
    val STAGES: Map<String, List<String>> = mapOf(
STAGE_MAP
    )

    @DrawableRes
    fun bed(crop: String, stage: String, wet: Boolean): Int = images.getValue("crop_${crop}_${stage}_${wetness(wet)}")

    @DrawableRes
    fun emptyBed(wet: Boolean): Int = images.getValue("bed_empty_${wetness(wet)}")

    /** The seed packet a crop is planted from. */
    @DrawableRes
    fun seedPacket(crop: String): Int = images.getValue("prop_seed_$crop")

    private fun wetness(wet: Boolean) = if (wet) "wet" else "dry"

    private val images: Map<String, Int> = mapOf(
IMAGE_MAP
    )
}
"""


def kotlin(names: list[str]) -> str:
    quoted = lambda items: ", ".join(f'"{item}"' for item in items)  # noqa: E731
    pieces = {
        "STAGE_MAP": "\n".join(f'        "{crop}" to listOf({quoted(stages)}),' for crop, stages in STAGES.items()),
        "IMAGE_MAP": "\n".join(f'        "{name}" to R.drawable.{name},' for name in names),
    }
    text = TEMPLATE
    for key, value in pieces.items():
        text = text.replace(key, value)
    return text


def _anim_constants() -> dict[str, int]:
    """Timing from farmkit/anim.py (it needs Blender, so read the text)."""
    text = (ART / "farmkit/anim.py").read_text()
    wet = re.search(r"^WET_FROM, WET_TO = (\d+), (\d+)", text, re.M)
    seed = re.search(r"^SEED_FROM, SEED_TO = (\d+), (\d+)", text, re.M)
    number = lambda name: int(re.search(rf"^{name} = (\d+)", text, re.M).group(1))  # noqa: E731
    return {"FPS": number("FPS"), "WATER_FRAMES": number("WATER_FRAMES"), "PLANT_FRAMES": number("PLANT_FRAMES"),
            "HARVEST_FRAMES": number("HARVEST_FRAMES"), "WET_FROM": int(wet.group(1)), "WET_TO": int(wet.group(2)),
            "SEED_FROM": int(seed.group(1)), "SEED_TO": int(seed.group(2))}


def harvest_stages() -> dict[str, list[str]]:
    """As farmkit.anim.harvest_stages: ripe, or overripe when late; a crop without those from its last stage."""
    return {crop: [s for s in ("ripe", "overripe") if s in stages] or [stages[-1]] for crop, stages in STAGES.items()}


def motion_sequences() -> dict[str, list[Path]]:
    """Every animation the app can play, as name -> its frame files in order (rendered as <name>_<LIGHT>_<nn>.png).

    Each must have exactly the frame count farmkit/anim.py renders, numbered from 00, so a missing or left-over
    frame can't shift the timing.
    """
    constants = _anim_constants()
    counts = {f"water_{kind}": constants["WATER_FRAMES"] for kind in ("can", "rain")}
    for crop in STAGES:
        counts[f"plant_{crop}"] = constants["PLANT_FRAMES"]
    for crop, stages in harvest_stages().items():
        for stage in stages:
            counts[f"harvest_{crop}_{stage}"] = constants["HARVEST_FRAMES"]
    sequences, wrong = {}, []
    for name, count in counts.items():
        frames = [ANIM / f"{name}_{LIGHT}_{i:02d}.png" for i in range(count)]
        found = set(ANIM.glob(f"{name}_{LIGHT}_[0-9][0-9].png"))
        if found != set(frames):
            wrong.append(name)
        sequences[name] = frames
    if wrong:
        raise SystemExit(f"{len(wrong)} animations in {ANIM} don't have the frames farmkit/anim.py renders, "
                         f"e.g. {wrong[:3]}: render them again")
    return sequences


def export_motion() -> int:
    """The animation frames and CropMotion.kt. Returns the bytes written."""
    sequences = motion_sequences()
    base = Image.open(ANIM / f"harvest_base_{LIGHT}.png").convert("RGBA")
    written = set()
    total = 0
    for name, frames in sequences.items():
        for i, png in enumerate(frames):
            image = Image.open(png).convert("RGBA")
            if name.startswith("harvest_"):
                image = _matte(image, base)
            out = DRAWABLES / f"anim_{name}_{i:02d}.webp"
            image.save(out, "WEBP", quality=MOTION_QUALITY, alpha_quality=100, method=4)
            total += out.stat().st_size
            written.add(out)
    MOTION_KOTLIN.write_text(motion_kotlin(sequences))
    for old in set(DRAWABLES.glob("anim_*.webp")) - written:
        old.unlink()  # frames of animations that no longer exist
    print(f"{len(written)} animation frames in {len(sequences)} animations, {total / 1_000_000:.1f} MB; "
          f"generated {MOTION_KOTLIN.name}")
    return total


def _matte(frame: Image.Image, base: Image.Image) -> Image.Image:
    """Keep only the pixels of a harvest frame that differ from the empty bed, grown by a pixel to keep edges."""
    biggest = reduce(ImageChops.lighter, ImageChops.difference(frame, base).split())
    strong = biggest.point(lambda v: 255 if v > MATTE_STRONG else 0).filter(ImageFilter.MaxFilter(2 * MATTE_REACH + 1))
    weak = biggest.point(lambda v: 255 if v > MATTE_WEAK else 0)
    keep = ImageChops.darker(weak, strong).filter(ImageFilter.MaxFilter(3))
    frame.putalpha(ImageChops.darker(frame.getchannel("A"), keep))
    return frame


MOTION_TEMPLATE = """// Generated by art/export_android.py from the Blender animation frames. Don't edit by hand: re-run the export.
package com.example.projdiic

/**
 * The farm's rendered moments, as frames to play over a bed card at [FPS]: the watering can, the rain cloud (only in
 * the art preview), each crop's seed packet planting it, and each crop's harvest.
 */
object CropMotion {
    const val FPS = FPS_VALUE

    /** While watering plays, the card under it fades from dry to wet between these frames. */
    const val WET_FROM = WET_FROM_VALUE
    const val WET_TO = WET_TO_VALUE

    /** While planting plays, the card under it fades from the empty bed to the crop's seed stage between these frames. */
    const val SEED_FROM = SEED_FROM_VALUE
    const val SEED_TO = SEED_TO_VALUE

    /** The stages each crop is harvested from, the usual one first. */
    val HARVEST_STAGES: Map<String, List<String>> = mapOf(
HARVEST_STAGE_MAP
    )

    /** The watering can pouring, played over any bed. */
    fun water(): List<Int> = sequences.getValue("water_can")

    /** The rain cloud, played over any bed. */
    fun rain(): List<Int> = sequences.getValue("water_rain")

    /** The crop's seed packet pouring seeds into the bed, played over any bed. */
    fun plant(crop: String): List<Int> = sequences.getValue("plant_$crop")

    /** The crop flying out of its bed, played over the watered [CropArt.emptyBed]. */
    fun harvest(crop: String, stage: String): List<Int> = sequences.getValue("harvest_${crop}_$stage")

    /** The stage a crop at [stage] is harvested from: its own if it can be, otherwise it ripens to the usual one. */
    fun harvestedAs(crop: String, stage: String): String {
        val stages = HARVEST_STAGES.getValue(crop)
        return if (stage in stages) stage else stages.first()
    }

    private val sequences: Map<String, List<Int>> = mapOf(
SEQUENCE_MAP
    )
}
"""


def motion_kotlin(sequences: dict[str, list[Path]]) -> str:
    quoted = lambda items: ", ".join(f'"{item}"' for item in items)  # noqa: E731
    lines = []
    for name, frames in sequences.items():
        ids = [f"R.drawable.anim_{name}_{i:02d}" for i in range(len(frames))]
        rows = [", ".join(ids[k:k + 4]) for k in range(0, len(ids), 4)]
        lines.append(f'        "{name}" to listOf(\n            ' + ",\n            ".join(rows) + ",\n        ),")
    constants = _anim_constants()
    pieces = {
        "FPS_VALUE": str(constants["FPS"]),
        "WET_FROM_VALUE": str(constants["WET_FROM"]),
        "WET_TO_VALUE": str(constants["WET_TO"]),
        "SEED_FROM_VALUE": str(constants["SEED_FROM"]),
        "SEED_TO_VALUE": str(constants["SEED_TO"]),
        "HARVEST_STAGE_MAP": "\n".join(f'        "{crop}" to listOf({quoted(stages)}),'
                                       for crop, stages in harvest_stages().items()),
        "SEQUENCE_MAP": "\n".join(lines),
    }
    text = MOTION_TEMPLATE
    for key, value in pieces.items():
        text = text.replace(key, value)
    return text


def seed_packets() -> list[str]:
    return [f"prop_seed_{crop}" for crop in STAGES]


def export_props() -> int:
    """The props as drawables, and the launcher icon. Returns the bytes written."""
    names = ["prop_watering_can", "prop_basket", "prop_gate", "prop_mailbox", "prop_pulled"] + seed_packets()
    missing = [name for name in names + ["icon_foreground"] if not (PROPS / f"{name}.png").exists()]
    if missing:
        raise SystemExit(f"{len(missing)} props missing from {PROPS}, e.g. {missing[:3]}")
    total = 0
    for name in names:
        out = DRAWABLES / f"{name}.webp"
        Image.open(PROPS / f"{name}.png").convert("RGBA").save(out, "WEBP", quality=PROP_QUALITY, alpha_quality=100,
                                                               method=6)
        total += out.stat().st_size
    total += export_icon()
    print(f"{len(names)} props and the launcher icon, {total / 1_000_000:.2f} MB")
    return total


def export_icon() -> int:
    """The launcher icon at every density: the adaptive icon's foreground (the rendered bed, kept inside the 66 dp
    safe circle, whose silhouette is also the themed monochrome icon) over a flat green, and composited square and
    round icons for Android 7, which has no adaptive icons."""
    art = Image.open(PROPS / "icon_foreground.png").convert("RGBA")
    art = art.crop(art.getbbox())
    total = 0
    for density, scale in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icons = {
            "ic_launcher_foreground": _fit(art, round(108 * scale), round(60 * scale)),
            "ic_launcher": _badge(art, round(48 * scale), round(36 * scale), round(1 * scale), corner=0.22),
            "ic_launcher_round": _badge(art, round(48 * scale), round(34 * scale), round(1 * scale), corner=0.5),
        }
        for name, image in icons.items():
            out = folder / f"{name}.webp"
            image.save(out, "WEBP", lossless=True)
            total += out.stat().st_size
    (RES / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by art/export_android.py: the launcher icon\'s '
        'background. -->\n<resources>\n    <color name="ic_launcher_background">'
        f'{ICON_BACKGROUND}</color>\n</resources>\n')
    return total


def _fit(art: Image.Image, side: int, content: int) -> Image.Image:
    """The art scaled to fit a content-sized box, centred on a transparent square."""
    scale = content / max(art.size)
    small = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(small, ((side - small.width) // 2, (side - small.height) // 2))
    return canvas


def _badge(art: Image.Image, side: int, content: int, inset: int, corner: float) -> Image.Image:
    """A legacy icon: the green background as a rounded square (or a circle, corner=0.5) with the art on it."""
    big = 4  # draw the shape large and shrink it, for smooth edges
    shape = Image.new("L", (side * big, side * big), 0)
    box = (inset * big, inset * big, (side - inset) * big - 1, (side - inset) * big - 1)
    ImageDraw.Draw(shape).rounded_rectangle(box, radius=round((side - 2 * inset) * big * corner), fill=255)
    badge = Image.new("RGBA", (side, side), ICON_BACKGROUND)
    badge.putalpha(shape.resize((side, side), Image.Resampling.LANCZOS))
    badge.alpha_composite(_fit(art, side, content))
    return badge


def export_field() -> int:
    """The home screen's lawn, the pegs and string of an empty plot and a bed's shadow, and FieldArt.kt with where
    each plot's card goes on the lawn. Returns the bytes written."""
    missing = [f"{name}.png" for name in FIELD_IMAGES if not (FIELD / f"{name}.png").exists()]
    missing += [] if (FIELD / "field.json").exists() else ["field.json"]
    if missing:
        raise SystemExit(f"{len(missing)} field files missing from {FIELD}, e.g. {missing[:3]}")
    total = 0
    for name in FIELD_IMAGES:
        out = DRAWABLES / f"{name}.webp"
        Image.open(FIELD / f"{name}.png").convert("RGBA").save(out, "WEBP", quality=QUALITY, alpha_quality=100, method=6)
        total += out.stat().st_size
    FIELD_KOTLIN.write_text(field_kotlin(json.loads((FIELD / "field.json").read_text())))
    print(f"The field, {total / 1_000_000:.2f} MB; generated {FIELD_KOTLIN.name}")
    return total


FIELD_TEMPLATE = """// Generated by art/export_android.py from the Blender renders. Don't edit by hand: re-run the export.
package com.example.projdiic

/**
 * The home screen's field: a lawn holding four plots, back to front. A bed card or the pegs of an empty plot drawn
 * over a plot's [Plot.card], and a bed's shadow over its [Plot.shadow], sit exactly on that plot. Positions are shares
 * of the lawn image's width (x) and height (y).
 */
object FieldArt {
    val MEADOW: Int = R.drawable.field_meadow

    /** An empty plot: pegs and string marking it out on the grass. */
    val MARKED_PLOT: Int = R.drawable.field_plot_marked

    /** The shadow a bed casts on the grass, drawn under its card. */
    val BED_SHADOW: Int = R.drawable.field_bed_shadow

    /** The lawn image's width over its height. */
    const val ASPECT: Float = @W@f / @H@f

    /** A bed's outline for taps: its footprint's half width and half height, and how far up from it the bed reaches. */
    const val FOOTPRINT_HALF_WIDTH: Float = @HW@
    const val FOOTPRINT_HALF_HEIGHT: Float = @HH@
    const val TAP_HEIGHT: Float = @TH@

    val PLOTS: List<Plot> = listOf(
@PLOTS@
    )

    /** Where a plot's card and its bed's (wider) shadow go, and its middle and front corner on the ground. */
    class Plot(val card: Box, val shadow: Box, val middle: Point, val front: Point)

    class Box(val left: Float, val top: Float, val width: Float, val height: Float)

    class Point(val x: Float, val y: Float)
}
"""


def field_kotlin(layout: dict) -> str:
    width, height = layout["size"]
    f = lambda value: f"{value:.4f}f"  # noqa: E731
    point = lambda p: f"Point({f(p[0] / width)}, {f(p[1] / height)})"  # noqa: E731
    box = lambda b: f"Box({f(b[0] / width)}, {f(b[1] / height)}, {f(b[2] / width)}, {f(b[3] / height)})"  # noqa: E731
    plots = [f"        Plot(card = {box(plot['card'])}, shadow = {box(plot['shadow'])}, "
             f"middle = {point(plot['middle'])}, front = {point(plot['front'])}),"
             for plot in layout["plots"]]
    outline = layout["outline"]
    values = {"@W@": str(width), "@H@": str(height), "@HW@": f(outline["half_width"] / width),
              "@HH@": f(outline["half_height"] / height), "@TH@": f(outline["height"] / height),
              "@PLOTS@": "\n".join(plots)}
    return reduce(lambda text, item: text.replace(*item), values.items(), FIELD_TEMPLATE)


if __name__ == "__main__":
    export()
