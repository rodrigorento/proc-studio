"""Lettuce: Tomorrow's crop, four per bed in a 2x2 grid.

Every stage comes from one model with three settings, as for the other crops: growth (0 seed to 1 ripe), thirst
(0 watered to 1 drooping) and bolting (gone to seed: the head stretches into a tall cone of pointed leaves topped by
small yellow flowers). A young lettuce is an open rosette lying on the soil; the leaves cup up and a pale heart closes
in the middle as it ripens.
"""
import math
import random

import bmesh
import bpy
from mathutils import Vector

from .. import bed, style, toon
from . import parts
from .parts import UP

STAGES = {
    "seed": {"growth": 0.0},
    "sprout": {"growth": 0.15},
    "growing": {"growth": 0.55},
    "ripe": {"growth": 1.0},
    "overripe": {"growth": 1.0, "bolting": True},
}

# A diamond in the picture: back, left, right and front.
POSITIONS = [(x, y) for y in (-0.2, 0.2) for x in (-0.2, 0.2)]

LEAF_LENGTH = 0.17
HEART_RADIUS = 0.07
CONE_HEIGHT = 0.42
GOLDEN_ANGLE = 2.39996


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    for i, (x, y) in enumerate(POSITIONS):
        rng = random.Random(i * 31 + 11)
        base = Vector((x, y, bed.soil_height(x, y)))
        _plant(collection, f"Lettuce.{i}", base, settings["growth"], 0.0 if wet else 1.0,
               settings.get("bolting", False), rng)


def _plant(collection, name: str, base: Vector, growth: float, thirst: float, bolting: bool,
           rng: random.Random) -> None:
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.1 if growth <= 0.0 else 0.09, 0.065 if growth <= 0.0 else 0.03)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = base
    toon.outline(mound)
    top = base + Vector((0, 0, 0.02))
    if growth <= 0.0:
        return
    if growth < 0.3:
        parts.sprout(collection, name, top, thirst, scale=0.9)
        return
    turn = rng.uniform(0, 2 * math.pi)
    if bolting:
        _cone(collection, name, top, thirst, turn, rng)
        return

    green = "leaf" if thirst < 0.5 else "leaf_dry"
    closing = max(0.0, (growth - 0.6) / 0.4)  # 0 for an open rosette, 1 once the head has closed
    length = LEAF_LENGTH * (0.5 + 0.5 * growth)
    droop = math.radians(12 * thirst)
    # Three layers: a skirt flaring over the soil, a cupped middle ring, and pale leaves wrapped round the heart.
    leaves = bmesh.new()
    _ring(leaves, top, 6 + round(closing), turn, 0.025, math.radians(78 - 18 * closing) + droop,
          length, length * 0.95, curl=0.35, sag=0.5 * thirst)
    if closing > 0:
        _ring(leaves, top, 7, turn + 0.5, 0.035, math.radians(28) + droop, length * closing,
              length * 0.95 * closing, curl=0.25, sag=0.4 * thirst)
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))
    if closing <= 0:
        return
    pale = "lettuce_heart" if thirst < 0.5 else "lettuce_heart_dry"
    heart = bmesh.new()
    parts.sphere(heart, top + UP * (0.075 * closing), HEART_RADIUS * closing, squash=0.9)
    toon.outline(parts.mesh_object(f"{name}.Heart", heart, collection, pale), style.LEAF_OUTLINE_WIDTH)
    wrap = bmesh.new()
    _ring(wrap, top, 5, turn + 0.9, 0.025, math.radians(12) + droop, length * 0.8 * closing,
          length * 0.8 * closing, curl=-0.8, sag=0.3 * thirst)
    parts.flat(parts.mesh_object(f"{name}.Wrap", wrap, collection, pale))


def _ring(bm: bmesh.types.BMesh, centre: Vector, count: int, turn: float, radius: float, tilt: float,
          length: float, width: float, curl: float, sag: float) -> None:
    """Round, ruffled leaves around a centre, each leaning out by `tilt` from upright with its back to the centre.

    Positive curl flares the tips outwards; negative curl cups them over the middle.
    """
    for k in range(count):
        a = turn + 2 * math.pi * k / count
        out = Vector((math.cos(a), math.sin(a), 0.0))
        along = out * math.sin(tilt) + UP * math.cos(tilt)
        parts.leaf(bm, centre + out * radius, along, out, length, width, lobes=5, lobe_depth=0.12, curl=curl,
                   sag=sag, peak=1.4)


def _cone(collection, name: str, base: Vector, thirst: float, turn: float, rng: random.Random) -> None:
    """Gone to seed: a tall stalk clad in pointed leaves, smaller towards the top, with yellow flowers on top."""
    top = base + UP * (CONE_HEIGHT - 0.05 * thirst) + Vector((rng.uniform(-0.02, 0.02), rng.uniform(-0.02, 0.02), 0))
    stalk = bmesh.new()
    parts.tube(stalk, [base, base.lerp(top, 0.5), top], 0.022, taper=0.35)
    green = "leaf" if thirst < 0.5 else "leaf_dry"
    toon.outline(parts.mesh_object(f"{name}.Stalk", stalk, collection, green), style.LEAF_OUTLINE_WIDTH)

    leaves, old_leaves = bmesh.new(), bmesh.new()
    # The old head's outer leaves, still round, at the foot of the stalk.
    _ring(leaves, base, 6, turn, 0.03, math.radians(62 + 10 * thirst), 0.1, 0.095, curl=0.3, sag=0.5 * thirst)
    count = 26
    for k in range(count):
        t = k / (count - 1)  # 0 at the bottom of the stalk, 1 near the top
        a = turn + k * GOLDEN_ANGLE
        out = Vector((math.cos(a), math.sin(a), 0.0))
        tilt = math.radians(50 - 32 * t + 22 * thirst)
        length = 0.2 * (1 - 0.65 * t)
        along = out * math.sin(tilt) + UP * math.cos(tilt)
        target = old_leaves if t < 0.25 and k % 2 == 0 else leaves
        parts.leaf(target, base.lerp(top, 0.03 + 0.8 * t), along, out, length, length * 0.55, peak=0.8, curl=0.25,
                   sag=0.5 * thirst)
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))
    parts.flat(parts.mesh_object(f"{name}.OldLeaves", old_leaves, collection, "leaf_old"))

    # A cluster of small yellow flowers at the tip and on two short sprigs just below it.
    flowers, sprigs = bmesh.new(), bmesh.new()
    clusters = [top + UP * 0.02]
    for hand in (-1, 1):
        a = turn + hand * 1.9
        start = base.lerp(top, 0.82)
        tip = start + Vector((math.cos(a), math.sin(a), 0.0)) * 0.06 + UP * 0.07
        parts.tube(sprigs, [start, start.lerp(tip, 0.5) + UP * 0.01, tip], 0.007, taper=0.7)
        clusters.append(tip)
    for centre in clusters:
        for k in range(7):
            a = k * GOLDEN_ANGLE
            r = 0.03 * math.sqrt(k / 6)
            parts.sphere(flowers, centre + Vector((math.cos(a) * r, math.sin(a) * r, 0.012 * (1 - k / 6))), 0.016)
    toon.outline(parts.mesh_object(f"{name}.Sprigs", sprigs, collection, green), style.LEAF_OUTLINE_WIDTH)
    toon.outline(parts.mesh_object(f"{name}.Flowers", flowers, collection, "yellow_flower"), style.LEAF_OUTLINE_WIDTH)
