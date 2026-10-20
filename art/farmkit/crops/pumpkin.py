"""Pumpkin: This month's crop, one in the middle of the bed with its vine, leaves and curly tendrils.

Every stage comes from one model with three settings, as for the other crops: growth (0 seed to 1 ripe), thirst
(0 watered to 1 drooping) and old (overripe: the orange dulls and gets pale speckles, the leaves yellow and the
vine dries). While growing, the vine is shorter and carries a yellow flower and a small green pumpkin.
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

RADIUS = 0.235
HEIGHT = 0.195
RIBS = 10
# The vine wraps round the back of the pumpkin, as (across, depth) in the picture: + is right and back.
VINE = [(-0.3, 0.02), (-0.2, 0.2), (0.02, 0.28), (0.24, 0.17), (0.31, -0.04), (0.2, -0.26)]
# Leaves as (position along the vine 0..1, size), and where the tendrils curl from.
LEAVES = [(0.02, 0.34), (0.45, 0.28), (0.72, 0.32), (1.0, 0.3)]
TENDRILS = [0.25, 0.6, 0.88]
SPECKLES = 16
# When the pumpkin is picked these shrink back into the soil instead of flying off with it.
LEFT_BEHIND = ("Vine", "Leaves", "OldLeaves")


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    _plant(collection, "Pumpkin", settings["growth"], 0.0 if wet else 1.0, settings.get("old", False),
           random.Random(13))


def _plant(collection, name: str, growth: float, thirst: float, old: bool, rng: random.Random) -> None:
    centre = _ground(0.0, -0.03)
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.12 if growth <= 0.0 else 0.11, 0.07 if growth <= 0.0 else 0.035)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = centre
    toon.outline(mound)
    if growth <= 0.0:
        return
    if growth < 0.3:
        parts.sprout(collection, name, centre + UP * 0.02, thirst, scale=1.8)
        return

    green = "leaf_broad" if thirst < 0.5 else "leaf_dry"
    vine_green = "leaf_dry" if old else green
    reach = 0.45 + 0.55 * growth  # how much of the vine has grown
    vine = [_ground(*p) + UP * 0.02 for p in VINE]
    vine = [parts.along(vine, reach * i / 12) for i in range(13)]
    stems = bmesh.new()
    parts.tube(stems, vine, 0.021, taper=0.5)
    leaves, old_leaves = bmesh.new(), bmesh.new()
    for k, (at, size) in enumerate(LEAVES):
        if at > reach + 0.01:
            continue
        foot = parts.along(vine, at / reach)
        out = (foot - centre).normalized()
        out = Vector((out.x, out.y, 0.0)).normalized()
        stalk_top = foot + UP * (0.13 - 0.07 * thirst) + out * 0.03
        parts.tube(stems, [foot, foot.lerp(stalk_top, 0.5) + out * 0.01, stalk_top], 0.012)
        facing = (UP * 0.4 + TO_CAMERA * 0.6).normalized()
        target = old_leaves if old and k % 2 == 1 else leaves
        parts.palmate(target, stalk_top, out + UP * (0.25 - 0.5 * thirst), facing, size * (0.6 + 0.4 * growth),
                      cup=0.12 - 0.1 * thirst, sag=0.35 * thirst)
    for at in TENDRILS:
        if at <= reach:
            start = parts.along(vine, at / reach)
            parts.tendril(stems, start, (UP * 0.8 + ACROSS * rng.uniform(-0.4, 0.4)).normalized(), 0.05 + 0.04 * growth)
    toon.outline(parts.mesh_object(f"{name}.Vine", stems, collection, vine_green), style.LEAF_OUTLINE_WIDTH)
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))
    if old_leaves.faces:
        parts.flat(parts.mesh_object(f"{name}.OldLeaves", old_leaves, collection, "leaf_old"))
    else:
        old_leaves.free()

    if growth < 0.8:
        _young(collection, name, centre, vine)
        return
    fruit(collection, name, centre, RADIUS, HEIGHT, "pumpkin_old" if old else "pumpkin", vine_green,
           "pumpkin_old_groove" if old else "pumpkin_groove")
    if old:
        spots = bmesh.new()
        for _ in range(SPECKLES):
            theta = rng.uniform(0.35, 1.4)
            phi = rng.uniform(0, 2 * math.pi)
            point = _surface(theta, phi, RADIUS, HEIGHT)
            parts.sphere(spots, centre + UP * (HEIGHT * 0.85) + point, rng.uniform(0.008, 0.014), squash=0.6)
        parts.mesh_object(f"{name}.Speckles", spots, collection, "flower")


def _young(collection, name: str, centre: Vector, vine: list[Vector]) -> None:
    """A small green pumpkin where the big one will be, and a yellow trumpet flower further along the vine."""
    fruit(collection, f"{name}.Young", centre, 0.09, 0.075, "pumpkin_young", "leaf_broad")
    # Five pointed yellow petals facing the camera, on a short stalk.
    at = parts.along(vine, 0.5)
    stalk = bmesh.new()
    parts.tube(stalk, [at, at + UP * 0.07, at + UP * 0.12], 0.009)
    toon.outline(parts.mesh_object(f"{name}.FlowerStalk", stalk, collection, "leaf"), style.LEAF_OUTLINE_WIDTH)
    petals = bmesh.new()
    parts.palmate(petals, at + UP * 0.07, UP, TO_CAMERA, 0.16, cup=0.25)
    parts.flat(parts.mesh_object(f"{name}.Flower", petals, collection, "yellow_flower"))


def fruit(collection, name: str, centre: Vector, radius: float, height: float, material: str, stem: str,
           groove: str | None = None) -> None:
    """A ribbed, squashed ball sitting a little into the soil, with a curved stalk and, if given, groove lines."""
    bm = bmesh.new()
    rings, segments = 24, 80
    grid = [[bm.verts.new(_surface(math.pi * i / rings, 2 * math.pi * j / segments, radius, height))
             for j in range(segments)] for i in range(1, rings)]
    top = bm.verts.new((0.0, 0.0, height * 0.72))
    bottom = bm.verts.new((0.0, 0.0, -height))
    for a, b in zip(grid, grid[1:]):
        for j in range(segments):
            bm.faces.new((a[j], b[j], b[(j + 1) % segments], a[(j + 1) % segments]))
    for j in range(segments):
        bm.faces.new((top, grid[0][j], grid[0][(j + 1) % segments]))
        bm.faces.new((bottom, grid[-1][(j + 1) % segments], grid[-1][j]))
    body = parts.mesh_object(f"{name}.Body", bm, collection, material)
    body.location = centre + UP * (height * 0.85)
    toon.outline(body)

    crown = body.location + UP * (height * 0.68)
    bend = (-ACROSS * 0.6 + DEPTH * 0.4).normalized()
    stalk = bmesh.new()
    parts.tube(stalk, [crown, crown + UP * (height * 0.35), crown + UP * (height * 0.6) + bend * (height * 0.2),
                       crown + UP * (height * 0.7) + bend * (height * 0.45)], radius * 0.15, sides=8, taper=0.7)
    toon.outline(parts.mesh_object(f"{name}.Stalk", stalk, collection, stem))
    if groove is None:
        return
    # A crease down each groove between the ribs, as the concept draws them.
    lines = bmesh.new()
    for k in range(RIBS):
        phi = (2 * k + 1) * math.pi / RIBS
        parts.tube(lines, [body.location + _surface(t, phi, radius, height) for t in (0.3, 0.7, 1.1, 1.5, 1.9, 2.3)],
                   radius * 0.022, taper=0.8)
    parts.mesh_object(f"{name}.Grooves", lines, collection, groove)


def _surface(theta: float, phi: float, radius: float, height: float) -> Vector:
    """A point on the pumpkin: theta from the top (0) to the bottom (pi), phi round it. Grooves between the ribs."""
    rib = (1 - 0.1) + 0.1 * abs(math.cos(RIBS * phi / 2)) ** 0.5
    dimple = 0.28 * height * math.exp(-(theta / 0.45) ** 2)
    r = radius * math.sin(theta) * rib
    return Vector((r * math.cos(phi), r * math.sin(phi), height * math.cos(theta) - dimple))


def _ground(across: float, depth: float) -> Vector:
    """The soil surface at a spot given in picture directions."""
    p = ACROSS * across + DEPTH * depth
    return Vector((p.x, p.y, bed.soil_height(p.x, p.y)))
