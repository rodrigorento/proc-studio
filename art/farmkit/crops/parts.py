"""Mesh pieces the crops are made of: tubes for stems, flat leaves, lathed roots, mounds."""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from .. import toon


def mesh_object(name: str, bm: bmesh.types.BMesh, collection: bpy.types.Collection, material: str,
                smooth: bool = True) -> bpy.types.Object:
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    for poly in mesh.polygons:
        poly.use_smooth = smooth
    obj = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    obj.data.materials.append(toon.material(material))
    return obj


def tube(bm: bmesh.types.BMesh, points: list[Vector], radius: float, sides: int = 6, taper: float = 0.6) -> None:
    """A closed tube along points, narrowing to `taper` of its radius at the end."""
    rings = []
    for i, point in enumerate(points):
        ahead = points[min(i + 1, len(points) - 1)] - points[max(i - 1, 0)]
        rotation = ahead.normalized().to_track_quat("Z", "Y").to_matrix()
        r = radius * (1 - (1 - taper) * i / (len(points) - 1))
        ring = [bm.verts.new(point + rotation @ Vector((math.cos(a) * r, math.sin(a) * r, 0)))
                for a in (2 * math.pi * k / sides for k in range(sides))]
        rings.append(ring)
    for a, b in zip(rings, rings[1:]):
        for k in range(sides):
            bm.faces.new((a[k], a[(k + 1) % sides], b[(k + 1) % sides], b[k]))
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])


def leaf(bm: bmesh.types.BMesh, base: Vector, along: Vector, up: Vector, length: float, width: float,
         lobes: int = 0, lobe_depth: float = 0.28, curl: float = 0.0, sag: float = 0.0, peak: float = 1.0) -> None:
    """A flat leaf from `base` pointing `along`, facing `up`.

    Lobes give it a jagged, carrot-leaf edge. Curl bends it towards its face; sag bends it towards the ground.
    Peak above 1 moves the widest point towards the tip, for a rounded, fan-like leaf.
    """
    along = along.normalized()
    side = along.cross(up).normalized()
    normal = side.cross(along).normalized()
    one_edge, other_edge = [], []
    steps = 48
    for k in range(steps):
        t = k / (steps - 1)  # 0 at the base, 1 at the tip
        w = width * math.sin(math.pi * t ** peak) ** 0.6
        if lobes:
            w *= 1 - lobe_depth + lobe_depth * abs(math.sin(t * lobes * math.pi))
        bend = t * t * length
        centre = base + along * (t * length) + normal * (curl * bend) - Vector((0, 0, sag * bend))
        one_edge.append(centre + side * w / 2)
        other_edge.append(centre - side * w / 2)
    # Both edges meet at the base and the tip (zero width there), so each point is added once.
    ring = [bm.verts.new(v) for v in one_edge[:-1]] + [bm.verts.new(v) for v in reversed(other_edge[1:])]
    face = bm.faces.new(ring)
    bmesh.ops.triangulate(bm, faces=[face])


def lathe(bm: bmesh.types.BMesh, profile: list[tuple[float, float]], sides: int = 16) -> None:
    """A solid of revolution around Z from (radius, z) pairs, top first."""
    rings = []
    for r, z in profile:
        rings.append([bm.verts.new((math.cos(a) * r, math.sin(a) * r, z))
                      for a in (2 * math.pi * k / sides for k in range(sides))])
    for a, b in zip(rings, rings[1:]):
        for k in range(sides):
            bm.faces.new((a[k], b[k], b[(k + 1) % sides], a[(k + 1) % sides]))
    bm.faces.new(rings[0])
    bm.faces.new(list(reversed(rings[-1])))


def mound(bm: bmesh.types.BMesh, radius: float, height: float) -> None:
    """A low dome of soil, as where a seed was just planted."""
    bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=10, radius=1.0)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < -0.05], context="VERTS")
    bmesh.ops.scale(bm, vec=(radius, radius, height), verts=bm.verts)


def sphere(bm: bmesh.types.BMesh, centre: Vector, radius: float, squash: float = 1.0) -> None:
    geom = bmesh.ops.create_icosphere(bm, subdivisions=2, radius=radius)
    verts = geom["verts"]
    bmesh.ops.transform(bm, matrix=Matrix.Diagonal((1.0, 1.0, squash, 1.0)), verts=verts)
    bmesh.ops.translate(bm, vec=centre, verts=verts)
