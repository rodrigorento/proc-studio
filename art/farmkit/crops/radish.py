"""Radish: Today's crop, nine per bed in a 3x3 grid on the three ridges.

Every stage comes from one model with three settings, as for the carrot: growth (0 seed to 1 ripe), thirst
(0 watered to 1 drooping) and bolting (gone to seed: yellowing leaves, and on five of the nine a thin stalk of
pink flowers). The round red root sits mostly above the soil once ripe; the whole root, with its white tail,
is modeled for the harvest animation.
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
    "growing": {"growth": 0.6},
    "ripe": {"growth": 1.0},
    "overripe": {"growth": 1.0, "bolting": True},
}

# Three along each ridge. The corners and the centre are the ones that bolt.
POSITIONS = [(x, y) for y in bed.ridge_centres() for x in (-0.26, 0.0, 0.26)]
BOLTS = {0, 2, 4, 6, 8}

BULB_RADIUS = 0.068
LEAF_LENGTH = 0.19
LEAVES = 5
OUTLINE_WIDTH = 0.012  # the bulbs are small; the bed's width would swallow them


def build(collection: bpy.types.Collection, stage: str, wet: bool = True) -> None:
    settings = STAGES[stage]
    bolting = settings.get("bolting", False)
    for i, (x, y) in enumerate(POSITIONS):
        rng = random.Random(i * 31 + 7)
        base = Vector((x, y, bed.soil_height(x, y)))
        _plant(collection, f"Radish.{i}", base, settings["growth"], 0.0 if wet else 1.0, bolting,
               bolting and i in BOLTS, rng)


def _plant(collection, name: str, base: Vector, growth: float, thirst: float, yellowing: bool, bolting: bool,
           rng: random.Random) -> None:
    soil = "pebble_wet" if thirst < 0.5 else "pebble_dry"
    bm = bmesh.new()
    parts.mound(bm, 0.07 if growth <= 0.0 else 0.06, 0.055 if growth <= 0.0 else 0.025)
    mound = parts.mesh_object(f"{name}.Mound", bm, collection, soil)
    mound.location = base
    toon.outline(mound, OUTLINE_WIDTH)
    top = base + Vector((0, 0, 0.02))
    if growth <= 0.0:
        return
    if growth < 0.3:
        parts.sprout(collection, name, top, thirst, scale=0.75)
        return

    # Bulb: a red ball that rises out of the soil as it grows, a little more than half of it showing when ripe.
    radius = BULB_RADIUS * (0.3 + 0.7 * growth)
    showing = 2 * radius * (0.3 + 0.3 * max(0.0, (growth - 0.4) / 0.6))
    profile = [(radius * math.sin(a), -radius * (1 - math.cos(a))) for a in (0.3, 0.7, 1.1, 1.5, 1.9, 2.3, 2.7, 3.0)]
    bm = bmesh.new()
    parts.lathe(bm, profile)
    bulb = parts.mesh_object(f"{name}.Bulb", bm, collection, "radish")
    bulb.location = top + Vector((0, 0, showing - 0.02))
    toon.outline(bulb, OUTLINE_WIDTH)
    bottom = bulb.location + Vector((0, 0, -2 * radius * 0.98))
    tail = bmesh.new()
    parts.tube(tail, [bottom + Vector((0.004 * k * k, 0, -0.03 * k * growth)) for k in range(5)], radius * 0.14,
               taper=0.15)
    toon.outline(parts.mesh_object(f"{name}.Tail", tail, collection, "radish_tail"), style.LEAF_OUTLINE_WIDTH)

    crown = bulb.location + Vector((0, 0, -0.004))
    _leaves(collection, name, crown, growth, thirst, yellowing, rng)
    if bolting:
        _flower_stalk(collection, name, crown, rng)


def _leaves(collection, name: str, crown: Vector, growth: float, thirst: float, yellowing: bool,
            rng: random.Random) -> None:
    """A fan of leaves facing the camera: a bare stalk, a pair of small lobes, then a big round-tipped blade."""
    stems, leaves, old_leaves = bmesh.new(), bmesh.new(), bmesh.new()
    turn = rng.uniform(-0.12, 0.12)
    for k in range(LEAVES):
        spread = (k - (LEAVES - 1) / 2) / ((LEAVES - 1) / 2)  # -1 at the left .. 1 at the right
        size = LEAF_LENGTH * growth * (1 - 0.18 * abs(spread)) * rng.uniform(0.92, 1.05)
        angle = math.radians(spread * (24 + 22 * thirst)) + turn + rng.uniform(-0.08, 0.08)
        direction = (UP * math.cos(angle) + ACROSS * math.sin(angle) + DEPTH * rng.uniform(-0.2, 0.2)).normalized()
        sideways = (ACROSS * math.copysign(1.0, spread or 1.0)) * abs(spread)
        points = [crown + direction * (t * size) + sideways * (0.12 * t * t * size) - UP * (0.7 * thirst * t * t * size)
                  for t in (i / 6 for i in range(7))]
        parts.tube(stems, points, 0.008 * (0.6 + 0.4 * growth))
        target = old_leaves if yellowing and k % 2 == 0 else leaves
        at = parts.along(points, 0.35)
        stem_dir = (parts.along(points, 0.45) - at).normalized()
        side = stem_dir.cross(TO_CAMERA).normalized()
        for hand in (-1, 1):
            lobe_dir = (stem_dir * 0.6 + side * hand - UP * (0.4 * thirst)).normalized()
            parts.leaf(target, at, lobe_dir, TO_CAMERA, size * 0.24, size * 0.17, peak=1.3, sag=0.4 * thirst)
        blade_from = parts.along(points, 0.42)
        blade_dir = (points[-1] - blade_from).normalized()
        parts.leaf(target, blade_from, blade_dir, TO_CAMERA, size * 0.72, size * 0.44, lobes=2, lobe_depth=0.18,
                   peak=1.5, sag=0.6 * thirst)
    green = "leaf" if thirst < 0.5 else "leaf_dry"
    toon.outline(parts.mesh_object(f"{name}.Stems", stems, collection, green), style.LEAF_OUTLINE_WIDTH)
    for label, bm, material in (("Leaves", leaves, green), ("OldLeaves", old_leaves, "leaf_old")):
        if not bm.faces:
            bm.free()
            continue
        parts.flat(parts.mesh_object(f"{name}.{label}", bm, collection, material))


def _flower_stalk(collection, name: str, crown: Vector, rng: random.Random) -> None:
    """Gone to seed: a thin stalk with a big pink four-petal flower at its tip and buds on two side branches."""
    top = crown + Vector((rng.uniform(-0.02, 0.02), rng.uniform(-0.02, 0.02), 0.36))
    middle = crown.lerp(top, 0.5) + ACROSS * rng.uniform(-0.015, 0.015)
    stalks = bmesh.new()
    parts.tube(stalks, [crown, middle, top], 0.009, taper=0.7)
    tips = [top]
    for hand, height in ((-1, 0.55), (1, 0.75)):
        start = crown.lerp(top, height)
        tip = start + ACROSS * (0.07 * hand) + UP * 0.05
        parts.tube(stalks, [start, start.lerp(tip, 0.5) + UP * 0.01, tip], 0.006, taper=0.7)
        tips.append(tip)
    toon.outline(parts.mesh_object(f"{name}.Stalk", stalks, collection, "leaf"), style.LEAF_OUTLINE_WIDTH)
    # Flat petals in a cross, facing the camera.
    petals, centres = bmesh.new(), bmesh.new()
    facing_up = TO_CAMERA.cross(ACROSS).normalized()  # up in the picture
    for tip, size in zip(tips, (1.0, 0.55, 0.55)):
        flower = tip + UP * 0.03 * size
        for k in range(4):
            a = math.pi / 4 + k * math.pi / 2
            out = ACROSS * math.cos(a) + facing_up * math.sin(a)
            parts.leaf(petals, flower, out, TO_CAMERA, 0.068 * size, 0.06 * size, peak=1.5)
        parts.sphere(centres, flower + TO_CAMERA * 0.012, 0.018 * size)
    parts.flat(parts.mesh_object(f"{name}.Petals", petals, collection, "blossom"))
    toon.outline(parts.mesh_object(f"{name}.FlowerCentres", centres, collection, "leaf_old"), style.LEAF_OUTLINE_WIDTH)
