"""Carrot: This week's crop, two per bed.

Every stage comes from one model with three settings: growth (0 seed to 1 ripe), thirst (0 watered to
1 drooping) and bolting (gone to seed: yellowing leaves and a stalk of white flowers). Only the shoulder
of the root shows above the soil, but the whole root is modeled for the harvest animation.
"""
import math
import random

import bmesh
import bpy
from mathutils import Vector

from .. import bed, style, toon
from . import parts

STAGES = {
    "seed": {"growth": 0.0},
    "sprout": {"growth": 0.15},
    "growing": {"growth": 0.65},
    "ripe": {"growth": 1.0},
    "overripe": {"growth": 1.0, "bolting": True},
}

# Two carrots side by side across the middle of the picture.
POSITIONS = [(-0.17, -0.17), (0.17, 0.17)]

ROOT_LENGTH = 0.36
ROOT_RADIUS = 0.105
FRONDS = 5
TO_CAMERA = Vector(style.TO_CAMERA)
UP = Vector((0.0, 0.0, 1.0))


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    for i, (x, y) in enumerate(POSITIONS):
        rng = random.Random(i * 31 + 5)
        base = Vector((x, y, bed.soil_height(x, y)))
        _plant(collection, f"Carrot.{i}", base, settings["growth"], 0.0 if wet else 1.0,
               settings.get("bolting", False), rng)


def _plant(collection, name: str, base: Vector, growth: float, thirst: float, bolting: bool,
           rng: random.Random) -> None:
    # Every plant sits in a little mound of freshly turned (lighter) soil; a seed is only the mound.
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.1 if growth <= 0.0 else 0.09, 0.075 if growth <= 0.0 else 0.04)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = base
    toon.outline(mound)
    top = base + Vector((0, 0, 0.03))
    if growth <= 0.0:
        return
    if growth < 0.3:
        _sprout(collection, name, top, thirst)
        return

    # Root: the shoulder pokes out of the soil once ripe; younger roots stay just under it.
    shoulder = 0.085 * max(0.0, (growth - 0.6) / 0.4)
    length = ROOT_LENGTH * (0.35 + 0.65 * growth)
    radius = ROOT_RADIUS * (0.4 + 0.6 * growth)
    profile = [(radius * 0.5, 0.0), (radius * 0.9, -0.015), (radius, -0.045)]
    profile += [(radius * (1 - t) ** 1.3, -0.045 - t * (length - 0.045)) for t in (0.25, 0.5, 0.75, 0.97)]
    bm = bmesh.new()
    parts.lathe(bm, profile)
    root = parts.mesh_object(f"{name}.Root", bm, collection, "carrot")
    root.location = top + Vector((0, 0, shoulder - 0.02))
    toon.outline(root)

    crown = root.location.copy()
    _fronds(collection, name, crown, growth, thirst, bolting, rng)
    if bolting:
        _flower_stalk(collection, name, crown, rng)


def _fronds(collection, name: str, crown: Vector, growth: float, thirst: float, bolting: bool,
            rng: random.Random) -> None:
    """A fan of feathery fronds facing the camera: each stem carries pairs of small lobed leaflets."""
    stems, leaves, old_leaves = bmesh.new(), bmesh.new(), bmesh.new()
    size = 0.46 * growth
    across = Vector((0.7071, 0.7071, 0.0))  # left to right in the picture
    depth = Vector((-0.7071, 0.7071, 0.0))  # front to back
    for k in range(FRONDS):
        spread = (k - (FRONDS - 1) / 2) / ((FRONDS - 1) / 2)  # -1 at the left .. 1 at the right
        angle = math.radians(spread * (30 + 18 * thirst)) + rng.uniform(-0.06, 0.06)
        direction = (UP * math.cos(angle) + across * math.sin(angle) + depth * rng.uniform(-0.25, 0.25)).normalized()
        # The stem arcs outwards, and droops when thirsty.
        sideways = (across * math.copysign(1.0, spread or 1.0)) * abs(spread)
        points = [crown + direction * (t * size) + sideways * (0.15 * t * t * size) - UP * (0.5 * thirst * t * t * size)
                  for t in (i / 6 for i in range(7))]
        parts.tube(stems, points, 0.011 * (0.6 + 0.4 * growth))
        target = old_leaves if bolting and k % 2 == 0 else leaves
        for along_stem, scale in ((0.4, 1.0), (0.55, 0.9), (0.7, 0.8), (0.85, 0.65)):
            at = _along(points, along_stem)
            stem_dir = (_along(points, min(1.0, along_stem + 0.08)) - at).normalized()
            side = stem_dir.cross(TO_CAMERA).normalized()
            for hand in (-1, 1):
                leaf_dir = (stem_dir * 0.8 + side * hand - UP * (0.5 * thirst)).normalized()
                parts.leaf(target, at, leaf_dir, TO_CAMERA, size * 0.3 * scale, size * 0.2 * scale,
                           lobes=3, lobe_depth=0.5, peak=1.2, sag=0.5 * thirst)
        tip_dir = (points[-1] - points[-2]).normalized()
        parts.leaf(target, points[-2], tip_dir, TO_CAMERA, size * 0.3, size * 0.2, lobes=3, lobe_depth=0.5,
                   peak=1.2, sag=0.5 * thirst)
    green = "leaf" if thirst < 0.5 else "leaf_dry"
    stem_obj = parts.mesh_object(f"{name}.Stems", stems, collection, green)
    toon.outline(stem_obj, style.LEAF_OUTLINE_WIDTH)
    for label, bm, material in (("Leaves", leaves, green), ("OldLeaves", old_leaves, "leaf_old")):
        if not bm.faces:
            bm.free()
            continue
        _flat(parts.mesh_object(f"{name}.{label}", bm, collection, material))


def _sprout(collection, name: str, base: Vector, thirst: float) -> None:
    """Two round seed leaves on a short stem."""
    stem_top = base + Vector((0, 0, 0.12 - 0.05 * thirst))
    stems, leaves = bmesh.new(), bmesh.new()
    parts.tube(stems, [base, base.lerp(stem_top, 0.5), stem_top], 0.013)
    across = Vector((0.7071, 0.7071, 0.0))
    for hand in (-1, 1):
        direction = (across * hand + UP * (0.6 - 1.1 * thirst)).normalized()
        parts.leaf(leaves, stem_top, direction, TO_CAMERA, 0.11, 0.075)
    material = "leaf" if thirst < 0.5 else "leaf_dry"
    toon.outline(parts.mesh_object(f"{name}.Stems", stems, collection, material), style.LEAF_OUTLINE_WIDTH)
    _flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, material))


def _flower_stalk(collection, name: str, crown: Vector, rng: random.Random) -> None:
    """Gone to seed: a tall stalk topped by a flat umbrella of small white flowers."""
    top = crown + Vector((rng.uniform(-0.03, 0.03), rng.uniform(-0.03, 0.03), 0.55))
    stalk = bmesh.new()
    parts.tube(stalk, [crown, crown.lerp(top, 0.5) + Vector((0.015, 0, 0)), top], 0.015, taper=0.8)
    toon.outline(parts.mesh_object(f"{name}.Stalk", stalk, collection, "leaf"), style.LEAF_OUTLINE_WIDTH)
    flowers = bmesh.new()
    for ring, (radius, count) in enumerate(((0.0, 1), (0.045, 6), (0.085, 11))):
        for k in range(count):
            a = 2 * math.pi * (k + 0.5 * ring) / count
            centre = top + Vector((math.cos(a) * radius, math.sin(a) * radius, 0.015 - 0.3 * radius))
            parts.sphere(flowers, centre, 0.032, squash=0.75)
    toon.outline(parts.mesh_object(f"{name}.Flowers", flowers, collection, "flower"), style.LEAF_OUTLINE_WIDTH)


def _along(points: list[Vector], t: float) -> Vector:
    position = t * (len(points) - 1)
    i = min(int(position), len(points) - 2)
    return points[i].lerp(points[i + 1], position - i)


def _flat(obj: bpy.types.Object) -> None:
    """Give a flat leaf mesh a little thickness, then its outline."""
    thickness = obj.modifiers.new("Thickness", "SOLIDIFY")
    thickness.thickness = 0.008
    thickness.offset = 0.0
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)


