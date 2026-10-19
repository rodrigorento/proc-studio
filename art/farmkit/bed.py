"""The bed: a low edging of thin rounded planks with short corner posts and rivets, round tilled soil in ridges."""
import math
import random

import bmesh
import bpy

from . import style, toon

B = style.BED


def build(collection: bpy.types.Collection, wet: bool = True) -> None:
    """Add the bed and its soil to the collection. Dry soil is paler (cracks come with the soil material)."""
    half = B["size"] / 2
    depth = B["plank_depth"]
    post = B["post_size"]
    run = B["size"] - post  # planks tuck into the posts
    for side in range(4):
        angle = side * math.pi / 2
        for level in range(2):
            z = B["plank_height"] * (level + 0.5)
            y = -(half - depth / 2)
            plank = box(f"Plank.{side}{level}", (run, depth, B["plank_height"] * 0.94), collection,
                         toon.material("wood"), bevel=B["plank_round"], segments=4)
            plank.location = _turn(0.0, y, angle) + (z,)
            plank.rotation_euler.z = angle
            for end in (-1, 1):
                rivet = _rivet(f"Rivet.{side}{level}{end}", collection)
                rx, ry = _turn(end * (run / 2 - 0.07), y - depth / 2 - 0.004, angle)
                rivet.location = (rx, ry, z)
                rivet.rotation_euler = (math.pi / 2, 0.0, angle)
    for corner in range(4):
        centre = half - post / 2 + B["post_out"]
        cx, cy = _turn(centre, -centre, corner * math.pi / 2)
        post_obj = box(f"Post.{corner}", (post, post, B["post_height"]), collection,
                        toon.material("wood"), bevel=B["post_round"], segments=5)
        post_obj.location = (cx, cy, B["post_height"] / 2)
    _soil(collection, wet)


def soil_height(x: float, y: float) -> float:
    """Height of the soil surface at (x, y): rounded, log-like ridges run along X with narrow furrows."""
    phase = (y / (B["size"] - 2 * B["plank_depth"]) + 0.5) * B["ridges"]
    across = (phase % 1.0) * 2 - 1  # -1..1 across one ridge
    ridge = math.sqrt(max(0.0, 1 - across * across)) ** 0.7
    return B["soil_top"] + B["ridge_height"] * ridge


def ridge_centres() -> list[float]:
    """The Y of each ridge's crest, where crops are planted."""
    inner = B["size"] - 2 * B["plank_depth"]
    return [(-0.5 + (i + 0.5) / B["ridges"]) * inner for i in range(B["ridges"])]


def _soil(collection: bpy.types.Collection, wet: bool) -> None:
    inner = B["size"] - 2 * B["plank_depth"] + 0.02
    mesh = bpy.data.meshes.new("Soil")
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=60, y_segments=60, size=inner / 2)
    noise = random.Random(7)
    for v in bm.verts:
        v.co.z = soil_height(v.co.x, v.co.y) + noise.uniform(-0.004, 0.004)
    bm.to_mesh(mesh)
    bm.free()
    for poly in mesh.polygons:
        poly.use_smooth = True
    soil = bpy.data.objects.new("Soil", mesh)
    collection.objects.link(soil)
    soil.data.materials.append(toon.material("soil_wet" if wet else "soil_dry"))
    skirt = box("SoilBody", (inner, inner, B["soil_top"]), collection, toon.material("soil_wet" if wet else "soil_dry"))
    skirt.location = (0.0, 0.0, B["soil_top"] / 2 - 0.002)
    toon.outline(soil)

    pebble_material = toon.material("pebble_wet" if wet else "pebble_dry")
    rng = random.Random(11)
    for i in range(14):  # clods of soil on the ridges
        x = rng.uniform(-inner / 2 + 0.06, inner / 2 - 0.06)
        y = rng.uniform(-inner / 2 + 0.06, inner / 2 - 0.06)
        mesh = bpy.data.meshes.new(f"Pebble.{i}")
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=2, radius=rng.uniform(0.018, 0.032))
        bm.to_mesh(mesh)
        bm.free()
        pebble = bpy.data.objects.new(f"Pebble.{i}", mesh)
        collection.objects.link(pebble)
        pebble.data.materials.append(pebble_material)
        pebble.location = (x, y, soil_height(x, y) + 0.004)
        pebble.scale = (1.0, 1.0, 0.7)
        for poly in mesh.polygons:
            poly.use_smooth = True
        toon.outline(pebble, style.OUTLINE_WIDTH * 0.5)


def box(name: str, size: tuple[float, float, float], collection: bpy.types.Collection,
         material: bpy.types.Material, bevel: float = 0.0, segments: int = 3,
         outline: float = style.OUTLINE_WIDTH) -> bpy.types.Object:
    """A box centred on the origin; with a bevel it is rounded and gets an ink outline `outline` wide."""
    mesh = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=size, verts=bm.verts)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    obj.data.materials.append(material)
    if bevel:
        mod = obj.modifiers.new("Round", "BEVEL")
        mod.width = bevel
        mod.segments = segments
        mod.limit_method = "NONE"
        mod.harden_normals = False
        for poly in mesh.polygons:
            poly.use_smooth = True
        toon.outline(obj, outline)
    return obj


def _rivet(name: str, collection: bpy.types.Collection) -> bpy.types.Object:
    mesh = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=0.017)
    bm.to_mesh(mesh)
    bm.free()
    for poly in mesh.polygons:
        poly.use_smooth = True
    obj = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    obj.data.materials.append(toon.material("rivet"))
    obj.scale = (1.0, 1.0, 0.45)
    return obj


def _turn(x: float, y: float, angle: float) -> tuple[float, float]:
    c, s = math.cos(angle), math.sin(angle)
    return (x * c - y * s, x * s + y * c)
