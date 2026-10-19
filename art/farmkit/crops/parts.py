"""Mesh pieces the crops are made of: tubes for stems, long and hand-shaped leaves, lathed roots, mounds, sprouts."""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from .. import style, toon

TO_CAMERA = Vector(style.TO_CAMERA)
UP = Vector((0.0, 0.0, 1.0))
ACROSS = Vector((0.7071, 0.7071, 0.0))  # left to right in the picture
DEPTH = Vector((-0.7071, 0.7071, 0.0))  # front to back


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


def sprout(collection: bpy.types.Collection, name: str, base: Vector, thirst: float, scale: float = 1.0) -> None:
    """Two round seed leaves on a short stem."""
    stem_top = base + Vector((0, 0, (0.12 - 0.05 * thirst) * scale))
    stems, leaves = bmesh.new(), bmesh.new()
    tube(stems, [base, base.lerp(stem_top, 0.5), stem_top], 0.013 * scale)
    for hand in (-1, 1):
        direction = (ACROSS * hand + UP * (0.6 - 1.1 * thirst)).normalized()
        leaf(leaves, stem_top, direction, TO_CAMERA, 0.11 * scale, 0.075 * scale)
    material = "leaf" if thirst < 0.5 else "leaf_dry"
    toon.outline(mesh_object(f"{name}.Stems", stems, collection, material), style.LEAF_OUTLINE_WIDTH)
    flat(mesh_object(f"{name}.Leaves", leaves, collection, material))


def along(points: list[Vector], t: float) -> Vector:
    """The point a fraction t of the way along a polyline."""
    position = t * (len(points) - 1)
    i = min(int(position), len(points) - 2)
    return points[i].lerp(points[i + 1], position - i)


def flat(obj: bpy.types.Object) -> None:
    """Give a flat leaf mesh a little thickness, then its outline."""
    thickness = obj.modifiers.new("Thickness", "SOLIDIFY")
    thickness.thickness = 0.008
    thickness.offset = 0.0
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH)


def palmate(bm: bmesh.types.BMesh, base: Vector, along: Vector, up: Vector, size: float, lobes: int = 5,
            cup: float = 0.12, sag: float = 0.0) -> None:
    """A broad hand-shaped leaf (pumpkin, squash) whose stalk joins at `base`, reaching `along`, facing `up`.

    The lobes spread from the middle of the blade, and the notch between the two back lobes holds the stalk.
    Cup lifts the middle above the edges; sag drops the edges towards the ground.
    """
    along = along.normalized()
    side = along.cross(up).normalized()
    normal = side.cross(along).normalized()
    radius = size / 2
    centre = base + along * (radius * 0.32)
    middle = bm.verts.new(centre + normal * (cup * radius))
    ring = []
    steps = 72
    for k in range(steps):
        a = -math.pi + 2 * math.pi * k / steps  # 0 points along, +-pi is the notch
        r = radius * (0.74 + 0.26 * abs(math.cos(lobes * a / 2)) ** 0.7) * (1 + 0.04 * math.sin(14 * a))
        r *= 1 - 0.5 * math.exp(-((abs(a) - math.pi) / 0.3) ** 2)
        ring.append(bm.verts.new(centre + (along * math.cos(a) + side * math.sin(a)) * r - UP * (sag * r)))
    for k in range(steps):
        bm.faces.new((middle, ring[k], ring[(k + 1) % steps]))
