"""Grapes: the crop for anything later than a month or so, one vine on a small wooden T trellis in the middle of the bed
(art/concepts/grape/ripe.png).

The trellis stands in the bed from the day it's planted: a post with its crossbar on top, a T rather than a cross.
Every stage comes from one model with three settings, as for the other crops: growth (0 seed to 1 ripe), thirst
(0 watered to 1 drooping) and old (overripe: leaves turn yellow and orange, the bunches shrivel into raisins and a few
drop onto the soil). While growing, the vine is partway along the bar and carries small hard green clusters. The
harvest takes the bunches; the trellis and the vine sink back into the soil.
"""
import math
import random

import bmesh
import bpy
from mathutils import Vector

from .. import bed, style, toon
from . import parts
from .parts import ACROSS, DEPTH, TO_CAMERA, UP

STAGES = {
    "seed": {"growth": 0.0},
    "sprout": {"growth": 0.15},
    "growing": {"growth": 0.55},
    "ripe": {"growth": 1.0},
    "overripe": {"growth": 1.0, "old": True},
}

POST_HEIGHT = 0.62  # soil to the top of the crossbar
BAR_LENGTH = 0.64
WOOD = 0.064  # the post and bar's thickness
# Where the bunches hang from the vine's arms along the bar: (share of the arm, side).
BUNCHES = [(0.5, -1), (0.5, 1)]
# Leaves as (share of the vine's path from the foot of the post to the ends of the bar, size, lift): up to 0.5 on the
# post, then along the arms; lift turns a leaf up (+) or down (-).
LEAVES = [(0.22, 0.17, -0.2), (0.34, 0.19, 0.1), (0.46, 0.2, 0.5), (0.55, 0.22, 0.9), (0.62, 0.21, -0.1),
          (0.7, 0.23, 0.7), (0.78, 0.2, -0.3), (0.86, 0.22, 0.5), (0.95, 0.19, 0.0), (1.0, 0.18, 0.8)]
GRAPE_RADIUS = 0.034
# When the bunches are picked these sink back into the soil instead of flying off with them.
LEFT_BEHIND = ("Trellis", "TrellisBar", "Vine", "Leaves", "OldLeaves", "Fallen")


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    _plant(collection, "Grape", settings["growth"], 0.0 if wet else 1.0, settings.get("old", False),
           random.Random(29))


def _plant(collection, name: str, growth: float, thirst: float, old: bool, rng: random.Random) -> None:
    centre = _ground(0.0, 0.04)
    foot = centre + DEPTH * -0.07  # the vine grows from just in front of the post
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.11 if growth <= 0.0 else 0.09, 0.07 if growth <= 0.0 else 0.035)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = foot
    toon.outline(mound)
    trellis(collection, f"{name}.Trellis", centre)
    if growth <= 0.0:
        return
    green = "leaf_broad" if thirst < 0.5 else "leaf_dry"
    if growth < 0.3:
        parts.sprout(collection, name, foot + UP * 0.02, thirst, scale=1.4)
        return

    # The vine: up the front of the post with a twist round it, then along the bar both ways.
    reach = 0.45 + 0.55 * growth
    top = centre + UP * (POST_HEIGHT - WOOD * 0.5)
    trunk = [foot + UP * 0.02]
    for k in range(1, 9):
        t = k / 8
        a = t * 1.6 * math.pi
        trunk.append(centre + UP * ((POST_HEIGHT - WOOD) * t)
                     + (-DEPTH * math.cos(a) + ACROSS * math.sin(a)) * (WOOD * 0.9) * (1 - 0.5 * t))
    path = trunk + [top - DEPTH * WOOD * 0.6]
    stems = bmesh.new()
    climbed = [parts.along(path, min(1.0, reach * 1.6) * i / 10) for i in range(11)]
    parts.tube(stems, climbed, 0.026, taper=0.55)
    arms = []
    if reach * 1.6 > 1.0:
        for side in (-1, 1):
            out = [top - DEPTH * WOOD * 0.6 + ACROSS * side * (BAR_LENGTH * 0.5 * s) + UP * (0.012 * math.sin(s * 9))
                   for s in (i / 8 for i in range(9))]
            arm = [parts.along(out, min(1.0, (reach - 0.62) / 0.38) * i / 8) for i in range(9)]
            parts.tube(stems, arm, 0.016, taper=0.6)
            arms.append(arm)

    leaves, old_leaves = bmesh.new(), bmesh.new()
    for k, (at, size, lift) in enumerate(LEAVES):
        if at > reach + 0.01:
            continue
        side = 1 if k % 2 else -1
        if at <= 0.5 or not arms:
            spot = parts.along(climbed, min(1.0, at / 0.5))
        else:
            spot = parts.along(arms[0 if side < 0 else 1], min(1.0, (at - 0.5) / 0.5))
        out = (ACROSS * side + DEPTH * rng.uniform(-0.3, 0.3)).normalized()
        facing = (UP * 0.3 + TO_CAMERA * 0.7 + ACROSS * side * 0.2).normalized()
        stalk_top = spot + UP * (0.03 + 0.03 * lift) + out * 0.04 - DEPTH * 0.04
        parts.tube(stems, [spot, stalk_top], 0.007)
        target = old_leaves if old and k % 3 != 1 else leaves
        parts.palmate(target, stalk_top, (out + UP * (lift - 0.6 * thirst)).normalized(), facing,
                      size * (0.65 + 0.35 * growth), lobes=5, cup=0.1 - 0.08 * thirst, sag=0.3 * thirst, tip=0.25)
    for at in (0.3, 0.7, 0.95):
        if at <= reach:
            start = parts.along(climbed, min(1.0, at / 0.5)) if at <= 0.5 or not arms else parts.along(arms[0], (at - 0.5) / 0.5)
            parts.tendril(stems, start, (UP * 0.8 + ACROSS * rng.uniform(-0.5, 0.5)).normalized(), 0.04)
    toon.outline(parts.mesh_object(f"{name}.Vine", stems, collection, "leaf_dry" if old else green),
                 style.LEAF_OUTLINE_WIDTH)
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))
    if old_leaves.faces:
        parts.flat(parts.mesh_object(f"{name}.OldLeaves", old_leaves, collection, "leaf_autumn"))
    else:
        old_leaves.free()
    if not arms:
        return

    ripe = growth >= 0.8
    for k, (share, side) in enumerate(BUNCHES):
        arm = arms[0 if side < 0 else 1]
        hang = parts.along(arm, share)
        bunch(collection, f"{name}.Bunch{k}", hang - DEPTH * 0.08 - UP * 0.02,
              "grape_old" if old else ("grape" if ripe else "grape_young"),
              size=1.0 if ripe else 0.5, shrivel=0.75 if old else 1.0, rng=random.Random(k * 7 + 1))
    if old:
        fallen = bmesh.new()
        for across, depth in ((-0.24, -0.2), (0.12, -0.27), (0.3, -0.12)):
            parts.sphere(fallen, _ground(across, depth) + UP * 0.012, GRAPE_RADIUS * 0.7, squash=0.7)
        toon.outline(parts.mesh_object(f"{name}.Fallen", fallen, collection, "grape_old"), style.LEAF_OUTLINE_WIDTH)


def trellis(collection, name: str, centre: Vector) -> None:
    """A post with its crossbar on top, running across the picture."""
    wood = toon.material("wood")
    post = bed.box(f"{name}", (WOOD, WOOD, POST_HEIGHT), collection, wood, bevel=0.012, segments=2,
                   outline=style.LEAF_OUTLINE_WIDTH * 1.4)
    post.location = centre + UP * (POST_HEIGHT * 0.5 - 0.06)
    bar = bed.box(f"{name}Bar", (BAR_LENGTH, WOOD * 0.9, WOOD * 0.9), collection, wood, bevel=0.012, segments=2,
                  outline=style.LEAF_OUTLINE_WIDTH * 1.4)
    bar.location = centre + UP * (POST_HEIGHT - 0.06 - WOOD * 0.45)
    bar.rotation_euler.z = math.pi / 4  # along ACROSS


def bunch(collection, name: str, top: Vector, material: str, size: float = 1.0, shrivel: float = 1.0,
          rng: random.Random | None = None) -> None:
    """A hanging bunch: rows of round grapes narrowing to a point, on a short stalk from `top`."""
    rng = rng or random.Random(1)
    r = GRAPE_RADIUS * size * shrivel
    stalk = bmesh.new()
    parts.tube(stalk, [top, top - UP * 0.03 * size], 0.006)
    toon.outline(parts.mesh_object(f"{name}Stalk", stalk, collection, "leaf_dry" if shrivel < 1 else "leaf_broad"),
                 style.LEAF_OUTLINE_WIDTH)
    grapes = bmesh.new()
    rows = [4, 5, 4, 3, 2, 1]
    for i, count in enumerate(rows):
        z = -0.03 * size - i * GRAPE_RADIUS * size * 1.45
        ring = GRAPE_RADIUS * size * 1.1 * (count - 1) / 2.4
        for j in range(count):
            a = 2 * math.pi * j / max(1, count) + i * 0.5
            p = top + UP * z + (ACROSS * math.cos(a) + DEPTH * math.sin(a)) * ring
            p += Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1))) * r * 0.15
            parts.sphere(grapes, p, r * rng.uniform(0.92, 1.08), squash=0.8 if shrivel < 1 else 1.05)
    toon.outline(parts.mesh_object(f"{name}", grapes, collection, material), style.LEAF_OUTLINE_WIDTH)


def _ground(across: float, depth: float) -> Vector:
    p = ACROSS * across + DEPTH * depth
    return Vector((p.x, p.y, bed.soil_height(p.x, p.y)))
