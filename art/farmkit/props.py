"""Props that are not crops: the toy watering can, the rain cloud, water drops, seeds, soil puffs and sparkles.

Each builder makes its meshes around the origin in a fresh collection-linked object and returns them, so the
animations can place them with one matrix per frame.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from . import style, toon
from .crops import parts
from .crops.parts import ACROSS, TO_CAMERA, UP

PROP_OUTLINE = 0.012

# The can's spout points along +X; its rose (the sprinkler head) sits at the tip.
CAN_ROSE = Vector((0.27, 0.0, -0.01))
CAN_SPOUT = Vector((0.85, 0.0, 0.53)).normalized()


def watering_can(collection: bpy.types.Collection, matrix: Matrix) -> list[bpy.types.Object]:
    """A chunky toy watering can: a rounded drum, an arched handle, and a spout ending in a rose."""
    objects = []
    body = bmesh.new()
    parts.lathe(body, [(0.075, 0.0), (0.098, -0.012), (0.106, -0.035), (0.106, -0.15), (0.097, -0.17),
                       (0.075, -0.178)], sides=24)
    objects.append(_place(parts.mesh_object("Can.Body", body, collection, "can"), matrix))
    spout = bmesh.new()
    start = Vector((0.085, 0.0, -0.13))
    parts.tube(spout, [start, start.lerp(CAN_ROSE, 0.5) + Vector((0.0, 0.0, -0.01)), CAN_ROSE], 0.024, sides=10,
               taper=0.65)
    handle = [Vector((-0.09 + 0.16 * t, 0.0, -0.03 + 0.16 * math.sin(math.pi * t))) for t in (i / 8 for i in range(9))]
    parts.tube(spout, handle, 0.017, sides=8, taper=1.0)
    objects.append(_place(parts.mesh_object("Can.Spout", spout, collection, "can"), matrix))
    rose = bmesh.new()
    parts.sphere(rose, Vector((0, 0, 0)), 0.04, squash=0.45)
    rose_obj = parts.mesh_object("Can.Rose", rose, collection, "can_rose")
    rose_obj.matrix_world = matrix @ Matrix.Translation(CAN_ROSE) @ CAN_SPOUT.to_track_quat("Z", "Y").to_matrix().to_4x4()
    objects.append(rose_obj)
    for obj in objects:
        toon.outline(obj, PROP_OUTLINE)
    return objects


def cloud(collection: bpy.types.Collection, matrix: Matrix) -> bpy.types.Object:
    """A puffy toon rain cloud: a cluster of balls with a flat bottom."""
    bm = bmesh.new()
    for x, y, z, r in ((0.0, 0.0, 0.05, 0.13), (-0.14, 0.02, 0.0, 0.1), (0.15, -0.02, 0.0, 0.1),
                       (-0.07, 0.07, 0.08, 0.09), (0.08, 0.06, 0.07, 0.1), (-0.24, 0.0, -0.03, 0.07),
                       (0.25, 0.0, -0.03, 0.07)):
        parts.sphere(bm, Vector((x, y, z)), r, squash=0.85)
    for v in bm.verts:
        v.co.z = max(v.co.z, -0.07)  # flatten the underside
    obj = parts.mesh_object("Cloud", bm, collection, "cloud")
    obj.matrix_world = matrix
    toon.outline(obj, PROP_OUTLINE)
    return obj


def drops(collection: bpy.types.Collection, name: str, points: list[Vector], radius: float = 0.016,
          stretch: float = 1.6) -> bpy.types.Object | None:
    """Falling water drops, each a ball stretched upright."""
    if not points:
        return None
    bm = bmesh.new()
    for p in points:
        parts.sphere(bm, p, radius, squash=stretch)
    obj = parts.mesh_object(name, bm, collection, "water")
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)
    return obj


def seeds(collection: bpy.types.Collection, name: str, points: list[Vector], radius: float = 0.03) -> bpy.types.Object | None:
    """Seeds falling from a packet, each a plump little oval."""
    if not points:
        return None
    bm = bmesh.new()
    for p in points:
        parts.sphere(bm, p, radius, squash=1.35)
    obj = parts.mesh_object(name, bm, collection, "seed")
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)
    return obj


def streaks(collection: bpy.types.Collection, name: str, segments: list[tuple[Vector, Vector]],
            radius: float = 0.01) -> bpy.types.Object | None:
    """Rain: short thin streaks from each start to its end."""
    if not segments:
        return None
    bm = bmesh.new()
    for a, b in segments:
        parts.tube(bm, [a, a.lerp(b, 0.5), b], radius, sides=6, taper=0.6)
    obj = parts.mesh_object(name, bm, collection, "water")
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)
    return obj


def splashes(collection: bpy.types.Collection, name: str, rings: list[tuple[Vector, float]]) -> bpy.types.Object | None:
    """Little crowns of spray where water lands: (where, how far it has spread, 0..1).

    No outline: they are tiny, and ink would turn them into dark specks where they land on a crop.
    """
    if not rings:
        return None
    bm = bmesh.new()
    for centre, spread in rings:
        for k in range(6):
            a = 2 * math.pi * k / 6
            out = Vector((math.cos(a), math.sin(a), 0.0))
            parts.sphere(bm, centre + out * (0.018 + 0.03 * spread) + UP * (0.025 * math.sin(math.pi * spread)),
                         0.012 * (1.2 - 0.6 * spread))
    return parts.mesh_object(name, bm, collection, "water")


def puff(collection: bpy.types.Collection, name: str, centre: Vector, radius: float, t: float,
         material: str) -> bpy.types.Object | None:
    """A burst of soil clods thrown up round a plant as it pops out; t runs 0..1 and it is gone at 1."""
    if not 0.0 <= t < 1.0:
        return None
    bm = bmesh.new()
    for k in range(8):
        a = 2 * math.pi * (k + 0.3 * (k % 2)) / 8
        out = Vector((math.cos(a), math.sin(a), 0.0))
        lift = 0.12 * math.sin(math.pi * min(1.0, t * 1.2)) * (0.6 + 0.4 * (k % 3) / 2)
        parts.sphere(bm, centre + out * (radius * (0.8 + 1.4 * t)) + UP * lift, radius * 0.38 * (1 - t))
    obj = parts.mesh_object(name, bm, collection, material)
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)
    return obj


def sparkle(collection: bpy.types.Collection, name: str, centres: list[tuple[Vector, float]]) -> bpy.types.Object | None:
    """Four-pointed stars facing the camera: (where, size)."""
    centres = [(c, s) for c, s in centres if s > 0.0]
    if not centres:
        return None
    bm = bmesh.new()
    up = TO_CAMERA.cross(ACROSS).normalized()  # up in the picture
    for centre, size in centres:
        middle = bm.verts.new(centre)
        ring = []
        for k in range(8):
            a = math.pi / 2 + k * math.pi / 4
            r = size * (1.0 if k % 2 == 0 else 0.3)
            ring.append(bm.verts.new(centre + (ACROSS * math.cos(a) + up * math.sin(a)) * r))
        for k in range(8):
            bm.faces.new((middle, ring[k], ring[(k + 1) % 8]))
    obj = parts.mesh_object(name, bm, collection, "sparkle")
    obj.visible_shadow = False  # light, not a thing: no star-shaped shadow on the soil
    parts.flat(obj)
    return obj


def _place(obj: bpy.types.Object, matrix: Matrix) -> bpy.types.Object:
    obj.matrix_world = matrix
    return obj
