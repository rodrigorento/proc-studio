"""Corn: the crop of the week after next, three stalks in a row across the bed (art/concepts/corn/ripe.png).

Every stage comes from one model with three settings, as for the other crops: growth (0 seed to 1 ripe), thirst
(0 watered to 1 drooping) and old (gone past its best: the stalks and leaves dry to straw, the husks brown and the
kernels dull). Ripe, each stalk carries one cob, its husk peeled half back on yellow kernels, and a spike of leaves on top.
A short tuft of roots stays under the soil until the harvest pulls the stalk out.
"""
import math
import random

import bmesh
import bpy
from mathutils import Matrix, Vector

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

# Three stalks in a row from left to right in the picture, as (across, depth).
POSITIONS = [(-0.28, 0.02), (0.0, 0.0), (0.28, -0.02)]
HEIGHT = 0.56  # soil to the top of a ripe stalk, under its top leaves
# Leaves as (height as a share of the stalk, turn about it in degrees from the picture's right, length).
LEAVES = [(0.05, 15, 0.38), (0.14, 195, 0.4), (0.26, 60, 0.4), (0.38, 240, 0.38), (0.52, 320, 0.34),
          (0.66, 140, 0.3), (0.8, 30, 0.26), (0.9, 210, 0.22)]
COB_AT = 0.6  # share of the stalk where the cob grows
COB_LENGTH = 0.2
COB_RADIUS = 0.04
KERNEL_ROWS, KERNEL_RINGS = 12, 10


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    for i, (across, depth) in enumerate(POSITIONS):
        rng = random.Random(i * 17 + 3)
        _plant(collection, f"Corn.{i}", _ground(across, depth), settings["growth"], 0.0 if wet else 1.0,
               settings.get("old", False), rng)


def _plant(collection, name: str, base: Vector, growth: float, thirst: float, old: bool, rng: random.Random) -> None:
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.09 if growth <= 0.0 else 0.075, 0.07 if growth <= 0.0 else 0.035)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = base
    toon.outline(mound)
    if growth <= 0.0:
        return
    green = "straw" if old else ("leaf" if thirst < 0.5 else "leaf_dry")
    if growth < 0.3:
        _shoot(collection, name, base + UP * 0.02, green, thirst)
        return

    height = HEIGHT * (0.25 + 0.75 * growth)
    lean = (ACROSS * rng.uniform(-0.05, 0.05) + DEPTH * rng.uniform(-0.03, 0.03)) * height
    stalk = [base + UP * (height * t) + lean * t * t for t in (i / 10 for i in range(11))]
    stems = bmesh.new()
    parts.tube(stems, stalk, 0.034, sides=8, taper=0.5)
    # Roots under the soil, for the harvest to pull out.
    for k in range(5):
        a = 2 * math.pi * k / 5 + rng.uniform(-0.3, 0.3)
        out = Vector((math.cos(a), math.sin(a), 0.0))
        parts.tube(stems, [base + UP * 0.01, base + out * 0.04 - UP * 0.05, base + out * 0.07 - UP * 0.11], 0.008, taper=0.3)
    toon.outline(parts.mesh_object(f"{name}.Stalk", stems, collection, green), style.LEAF_OUTLINE_WIDTH)

    # Long strap leaves, arching out and down; thirst and age droop them further.
    leaves = bmesh.new()
    droop = 0.45 + 0.9 * thirst + (0.6 if old else 0.0)
    for share, turn, length in LEAVES:
        if share > 0.35 + 0.65 * growth:
            continue
        foot = parts.along(stalk, share)
        out = Matrix.Rotation(math.radians(turn + rng.uniform(-12, 12)), 3, "Z") @ ACROSS
        out = (out * 0.8 + TO_CAMERA * 0.15).normalized()
        parts.leaf(leaves, foot, (out * 0.9 + UP).normalized(), TO_CAMERA, length * (0.6 + 0.4 * growth), 0.085,
                   sag=droop, peak=0.8)
    # The top: a spike of short leaves standing up.
    top = stalk[-1]
    for k in range(3):
        out = Matrix.Rotation(math.radians(120 * k + rng.uniform(-20, 20)), 3, "Z") @ ACROSS
        parts.leaf(leaves, top - UP * 0.03, (out * 0.3 + UP).normalized(), TO_CAMERA, 0.17 * (0.5 + 0.5 * growth), 0.055,
                   sag=0.4 * thirst + (1.2 if old else 0.0))
    parts.flat(parts.mesh_object(f"{name}.Leaves", leaves, collection, green))
    if growth < 0.8:
        return

    # The cob grows on the side facing the camera, so it shows in front of the leaves.
    side = 1 if rng.random() < 0.5 else -1
    front = Vector((TO_CAMERA.x, TO_CAMERA.y, 0.0)).normalized()
    foot = parts.along(stalk, COB_AT)
    out = (ACROSS * side * 0.45 - front * 0.1 + UP).normalized()
    cob(collection, name, foot + front * 0.045 + ACROSS * side * 0.01, out, "corn_old" if old else "corn", "husk_old" if old else "husk", old)


def _shoot(collection, name: str, base: Vector, green: str, thirst: float) -> None:
    """A corn sprout: a spike with two narrow leaves opening in a V."""
    leaves = bmesh.new()
    for hand in (-1, 1):
        direction = (ACROSS * hand * (0.45 + 0.4 * thirst) + UP).normalized()
        parts.leaf(leaves, base, direction, TO_CAMERA, 0.13, 0.035, sag=0.6 * thirst)
    parts.leaf(leaves, base, (UP + DEPTH * 0.1).normalized(), TO_CAMERA, 0.1, 0.028)
    parts.flat(parts.mesh_object(f"{name}.Shoot", leaves, collection, green))


def cob(collection, name: str, foot: Vector, along: Vector, kernel: str, husk: str, old: bool = False,
        length: float = COB_LENGTH, radius: float = COB_RADIUS) -> None:
    """A cob from `foot` pointing `along`: kernels in rows on a darker core, the husk peeled half back around it."""
    along = along.normalized()
    rotation = along.to_track_quat("Z", "Y").to_matrix().to_4x4()
    place = Matrix.Translation(foot + along * 0.03) @ rotation
    core = bmesh.new()
    parts.lathe(core, [(radius * 0.25, length), (radius * 0.75, length * 0.93), (radius * 0.9, length * 0.7),
                       (radius * 0.9, length * 0.25), (radius * 0.6, 0.0)])
    core_obj = parts.mesh_object(f"{name}.Cob", core, collection, "corn_cob" if not old else "husk_old")
    core_obj.matrix_world = place
    toon.outline(core_obj, style.LEAF_OUTLINE_WIDTH)
    kernels = bmesh.new()
    for ring in range(KERNEL_RINGS):
        z = length * (0.3 + 0.62 * ring / (KERNEL_RINGS - 1))
        r = radius * (0.9 - 0.25 * max(0.0, (z / length - 0.75) / 0.25))
        for row in range(KERNEL_ROWS):
            a = 2 * math.pi * (row + 0.5 * (ring % 2)) / KERNEL_ROWS
            parts.sphere(kernels, Vector((math.cos(a) * r, math.sin(a) * r, z)), radius * 0.24, squash=1.2)
    kernel_obj = parts.mesh_object(f"{name}.Kernels", kernels, collection, kernel)
    kernel_obj.matrix_world = place
    # The husk: leaves from the cob's foot, wrapped round its lower half and curling out at their tips.
    sheath = bmesh.new()
    for k in range(4):
        a = 2 * math.pi * k / 4 + 0.3
        out = Vector((math.cos(a), math.sin(a), 0.0))
        parts.leaf(sheath, Vector((0.0, 0.0, 0.0)) + out * radius * 0.5, (Vector((0, 0, 1)) + out * 0.5).normalized(),
                   out, length * 0.72, radius * 1.9, curl=0.5, sag=0.0)
    sheath_obj = parts.mesh_object(f"{name}.Husk", sheath, collection, husk)
    sheath_obj.matrix_world = place
    parts.flat(sheath_obj)


def _ground(across: float, depth: float) -> Vector:
    p = ACROSS * across + DEPTH * depth
    return Vector((p.x, p.y, bed.soil_height(p.x, p.y)))
