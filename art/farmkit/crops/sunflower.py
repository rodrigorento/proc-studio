"""Sunflower: the Anytime crop, one plant in the middle of the bed with a big head and two side blooms.

It has no due date, so no overripe stage: it grows to bloom and stays there. Two settings make every stage:
growth (0 seed to 1 bloom) and thirst (0 watered to 1 drooping: leaves sag and the heads nod). While growing,
the heads are closed green buds.
"""
import math

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
    "bloom": {"growth": 1.0},
}

STEM_HEIGHT = 0.58
# The heads as (across, height as a share of the stem, petal radius); the first is the big one on the main stem.
HEADS = [(0.0, 1.0, 0.25), (-0.27, 0.6, 0.14), (0.28, 0.68, 0.13)]
# Leaves as (height as a share of the stem, direction in the picture as (across, depth), size).
LEAVES = [(0.08, (-1.0, -0.5), 0.32), (0.12, (1.0, -0.4), 0.32), (0.3, (-0.8, 0.6), 0.25), (0.36, (0.9, 0.5), 0.24)]
PETALS = 16


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    _plant(collection, "Sunflower", STAGES[stage]["growth"], 0.0 if wet else 1.0)


def _plant(collection, name: str, growth: float, thirst: float) -> None:
    p = DEPTH * -0.02
    centre = Vector((p.x, p.y, bed.soil_height(p.x, p.y)))
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.11 if growth <= 0.0 else 0.1, 0.07 if growth <= 0.0 else 0.035)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = centre
    toon.outline(mound)
    if growth <= 0.0:
        return
    if growth < 0.3:
        parts.sprout(collection, name, centre + UP * 0.02, thirst, scale=1.8)
        return

    green = "leaf_broad" if thirst < 0.5 else "leaf_dry"
    height = STEM_HEIGHT * (0.3 + 0.7 * growth)
    blooming = growth >= 0.8
    # The main stem; thirst bends its top forwards so the big head nods.
    nod = 0.35 * thirst
    stem = [centre + UP * (height * t) - DEPTH * (nod * height * t ** 3) for t in (i / 8 for i in range(9))]
    stems = bmesh.new()
    parts.tube(stems, stem, 0.03 * (0.6 + 0.4 * growth), sides=8, taper=0.6)

    leaves = bmesh.new()
    for at, (across, depth), size in LEAVES:
        foot = parts.along(stem, at)
        out = (ACROSS * across + DEPTH * depth).normalized()
        stalk_top = foot + out * 0.09 + UP * (0.02 - 0.04 * thirst)
        parts.tube(stems, [foot, foot.lerp(stalk_top, 0.5) + UP * 0.01, stalk_top], 0.011)
        facing = (UP * 0.5 + TO_CAMERA * 0.5).normalized()
        parts.palmate(leaves, stalk_top, out + UP * (0.3 - 0.7 * thirst), facing, size * (0.55 + 0.45 * growth),
                      lobes=1, cup=0.15 - 0.1 * thirst, sag=0.4 * thirst, tip=0.35)

    for k, (across, share, radius) in enumerate(HEADS):
        if k == 0:
            top = stem[-1]
        else:
            # Side branches leave the main stem lower down and curve out to their bloom.
            start = parts.along(stem, share * 0.75)
            top = start + ACROSS * (across * (0.5 + 0.5 * growth)) + UP * (height * share * 0.3) - DEPTH * (nod * 0.1)
            bend = start.lerp(top, 0.5) + UP * 0.03
            parts.tube(stems, [start, bend, top], 0.017 * (0.6 + 0.4 * growth), taper=0.7)
        facing = (TO_CAMERA * 0.85 + UP * (0.2 - 0.6 * thirst) + ACROSS * (across * 0.25)).normalized()
        if blooming:
            head(collection, f"{name}.Head{k}", top, facing, radius)
        else:
            _bud(collection, f"{name}.Bud{k}", top, facing, radius * 0.45 * growth / 0.55, green)

    toon.outline(parts.mesh_object(f"{name}.Stems", stems, collection, green), style.LEAF_OUTLINE_WIDTH)
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))


def head(collection, name: str, centre: Vector, facing: Vector, radius: float) -> None:
    """An open flower facing `facing`: a domed seed disc dotted with seeds, two rings of petals and a green back."""
    rotation = facing.to_track_quat("Z", "Y")
    disc_radius = radius * 0.48
    built = []

    disc = bmesh.new()
    parts.sphere(disc, Vector((0, 0, 0)), disc_radius, squash=0.38)
    built.append((disc, "seed_disc", style.OUTLINE_WIDTH * radius / 0.2))
    dots = bmesh.new()
    for ring, count in ((0.5, 10), (0.8, 16)):
        for j in range(count):
            a = 2 * math.pi * (j + 0.5 * ring) / count
            r = disc_radius * ring
            z = disc_radius * 0.38 * math.sqrt(max(0.0, 1 - ring * ring))
            parts.sphere(dots, Vector((math.cos(a) * r, math.sin(a) * r, z)), disc_radius * 0.075, squash=0.6)
    built.append((dots, "seed_dot", None))

    front, back = bmesh.new(), bmesh.new()
    for ring, (target, z, length) in enumerate(((front, 0.0, 1.0), (back, -0.012, 1.08))):
        for j in range(PETALS):
            a = 2 * math.pi * (j + 0.5 * ring) / PETALS
            out = Vector((math.cos(a), math.sin(a), 0.0))
            parts.leaf(target, out * (disc_radius * 0.85) + Vector((0, 0, z)), out + Vector((0, 0, 0.12)),
                       Vector((0, 0, 1)), (radius - disc_radius * 0.85) * length, radius * 0.34, peak=1.3)
    built += [(front, "petal", "flat"), (back, "petal_back", "flat")]

    green_back = bmesh.new()
    parts.sphere(green_back, Vector((0, 0, -0.035)), disc_radius * 0.9, squash=0.45)
    built.append((green_back, "leaf_broad", style.LEAF_OUTLINE_WIDTH))

    for bm, material, outline in built:
        obj = parts.mesh_object(f"{name}.{material}", bm, collection, material)
        obj.location = centre
        obj.rotation_euler = rotation.to_euler()
        if outline == "flat":
            parts.flat(obj)
        elif outline:
            toon.outline(obj, outline)


def _bud(collection, name: str, centre: Vector, facing: Vector, radius: float, green: str) -> None:
    """A closed bud: a green ball wrapped in pointed sepals, tip towards `facing`."""
    rotation = facing.to_track_quat("Z", "Y")
    ball = bmesh.new()
    parts.sphere(ball, Vector((0, 0, 0)), radius, squash=0.9)
    sepals = bmesh.new()
    for j in range(7):
        a = 2 * math.pi * j / 7
        out = Vector((math.cos(a), math.sin(a), 0.0))
        parts.leaf(sepals, out * (radius * 0.5) - Vector((0, 0, radius * 0.6)), out * 0.45 + Vector((0, 0, 1)),
                   out, radius * 1.8, radius * 0.9, peak=0.8, curl=-0.4)
    for bm, label in ((ball, "Ball"), (sepals, "Sepals")):
        obj = parts.mesh_object(f"{name}.{label}", bm, collection, green)
        obj.location = centre
        obj.rotation_euler = rotation.to_euler()
        if label == "Sepals":
            parts.flat(obj)
        else:
            toon.outline(obj, style.LEAF_OUTLINE_WIDTH)
