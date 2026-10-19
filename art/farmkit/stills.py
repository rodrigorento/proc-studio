"""The farm's still props, rendered once in daylight: the watering-can button, a seed packet per crop, the
harvest basket, the gate (paused until tomorrow), the mailbox (neighbour check-in) and the app icon.

Each prop is built in its own collection and the camera is fitted to it, so props of any size render to the same
square with the same margin. File names: prop_<name>.png, and icon_foreground.png for the launcher icon.
"""
import math
import os

import bmesh
import bpy
from mathutils import Matrix, Vector

from . import bed, props, scene, style, toon
from .crops import parts, pumpkin, sunflower
from .crops.parts import ACROSS, DEPTH, TO_CAMERA, UP

SIZE = 512  # pixels; props show at button and badge sizes
MARGIN = 0.08  # of the frame, on each side
COLLECTION = "Prop"
SEEDS = ("radish", "lettuce", "carrot", "pumpkin", "sunflower")
FRONT = Matrix.Rotation(math.pi / 4, 4, "Z")  # turns a model's -Y front to face the camera
TURNS = {"gate": -28.0}  # degrees about the vertical, for props that read better a little side on


def render_all(out_dir: str, names: list[str] | None = None) -> list[str]:
    os.makedirs(out_dir, exist_ok=True)
    builders = {"watering_can": _can, "basket": _basket, "gate": _gate, "mailbox": _mailbox, "icon": _icon}
    builders.update({f"seed_{crop}": (lambda c: lambda col: _packet(col, c))(crop) for crop in SEEDS})
    card = scene.card_scene()
    toon.reset()
    scene.collection(card, "Bed")  # an empty bed collection: nothing of the field in the frame
    scene.set_light(card, "day")
    card.render.resolution_x = card.render.resolution_y = SIZE
    written = []
    try:
        for name in names or list(builders):
            collection = scene.collection(card, COLLECTION)
            builders[name](collection)
            _turn(collection, TURNS.get(name, 0.0))
            _frame(card, collection)
            file = "icon_foreground.png" if name == "icon" else f"prop_{name}.png"
            written.append(scene.render(card, os.path.join(out_dir, file)))
    finally:
        _remove(COLLECTION)
        scene.card_scene()  # back to the card camera and size
    return written


# --- Props -------------------------------------------------------------------------------------------------------

def _can(collection) -> None:
    """The watering button: the can tipped as if about to pour, with a few drops falling from its rose."""
    spout_to = -ACROSS
    m = (Matrix.Rotation(math.atan2(spout_to.y, spout_to.x), 4, "Z") @ Matrix.Rotation(math.radians(22), 4, "Y")
         @ Matrix.Scale(2.6, 4))
    props.watering_can(collection, m)
    rose = m @ props.CAN_ROSE
    props.drops(collection, "Drops", [rose + Vector((0, 0, -0.12)) - ACROSS * 0.03, rose + Vector((0, 0, -0.22)),
                                      rose + Vector((0, 0, -0.18)) - ACROSS * 0.08], radius=0.03)


def _packet(collection, crop: str) -> None:
    """A chunky paper seed packet in the crop's colour with a folded top, and a picture of the crop on a label."""
    paper = toon.material(f"packet_{crop}")
    face = Matrix.Rotation(math.pi / 4, 4, "Z")  # boxes are built along X (width) and Y (depth); turn X to ACROSS
    body = bed.box(f"Packet.{crop}", (0.74, 0.2, 0.92), collection, paper, bevel=0.06, segments=4)
    body.matrix_world = face @ Matrix.Translation((0, 0, 0.46))
    fold = bed.box(f"Packet.{crop}.Fold", (0.78, 0.23, 0.11), collection, paper, bevel=0.04, segments=3)
    fold.matrix_world = face @ Matrix.Translation((0, 0, 0.9)) @ Matrix.Rotation(math.radians(-8), 4, "X")
    label = bmesh.new()
    parts.lathe(label, [(0.25, 0.03), (0.27, 0.0), (0.25, -0.03)], sides=48)
    label_obj = parts.mesh_object(f"Packet.{crop}.Label", label, collection, "label")
    # The label lies flat on the packet's front: its Z turned to face the camera, centred low on the packet.
    on_front = Matrix.Translation(-DEPTH * 0.105 + UP * 0.43) @ FRONT @ Matrix.Rotation(math.pi / 2, 4, "X")
    label_obj.matrix_world = on_front
    toon.outline(label_obj, style.LEAF_OUTLINE_WIDTH)
    # The crop picture: a small model pressed flat against the label, like a printed relief.
    _emblem(collection, crop, Matrix.Translation(-DEPTH * 0.14 + UP * 0.25) @ FRONT @ Matrix.Diagonal((0.9, 0.3, 0.9, 1)))


def _basket(collection) -> None:
    """A round wicker basket with two handles, heaped with one of each crop."""
    wicker = [(0.33, 0.16), (0.4, 0.3), (0.42, 0.34)]  # the inside: a floor below the rim, then up the wall
    for k in range(9):  # outside, bands of weave bulging out between the rows
        z = 0.34 * (1 - k / 8)
        r = 0.27 + 0.17 * (z / 0.34) ** 0.8
        wicker += [(r, z), (r + 0.018, z - 0.019)] if k < 8 else [(r, z)]
    bm = bmesh.new()
    parts.lathe(bm, wicker, sides=32)
    body = parts.mesh_object("Basket", bm, collection, "wicker")
    toon.outline(body)
    rim = bmesh.new()
    parts.tube(rim, [Vector((math.cos(a) * 0.45, math.sin(a) * 0.45, 0.34)) for a in
                     (2 * math.pi * k / 40 for k in range(41))], 0.035, sides=8, taper=1.0)
    for hand in (-1, 1):  # handles on the left and right of the picture
        ends = [ACROSS * (hand * 0.36) + UP * 0.33, ACROSS * (hand * 0.52) + UP * 0.47, ACROSS * (hand * 0.43) + UP * 0.62,
                ACROSS * (hand * 0.3) + UP * 0.6]
        parts.tube(rim, ends, 0.03, sides=8, taper=1.0)
    toon.outline(parts.mesh_object("Basket.Rim", rim, collection, "wicker"))
    # The heap: big things at the back, small ones at the front, all facing the camera.
    heap = [("sunflower", 0.2, 0.18, 0.12, 1.6), ("pumpkin", 0.02, 0.05, 0.0, 1.7), ("lettuce", -0.22, 0.02, 0.0, 1.6),
            ("carrot", 0.2, -0.12, -0.02, 1.45), ("radish", -0.12, -0.2, 0.0, 1.5)]
    for crop, across, depth, lift, scale in heap:
        _emblem(collection, crop, Matrix.Translation(ACROSS * across + DEPTH * depth + UP * (0.18 + lift))
                @ FRONT @ Matrix.Scale(scale, 4))


def _gate(collection) -> None:
    """A closed farm gate of the beds' chunky rounded planks between two posts, with a moon plaque: closed until
    tomorrow."""
    wood = toon.material("wood")
    face = Matrix.Rotation(math.pi / 4, 4, "Z")  # the gate's width runs left to right in the picture
    for side in (-1, 1):
        post = bed.box(f"Gate.Post{side}", (0.2, 0.2, 1.05), collection, wood, bevel=0.07, segments=4)
        post.matrix_world = Matrix.Translation(ACROSS * (side * 0.66) + UP * 0.525) @ face
    for k, height in enumerate((0.25, 0.55, 0.85)):
        plank = bed.box(f"Gate.Rail{k}", (1.12, 0.09, 0.15), collection, wood, bevel=0.045, segments=3)
        plank.matrix_world = Matrix.Translation(UP * height) @ face
    for side in (-1, 1):
        stile = bed.box(f"Gate.Stile{side}", (0.12, 0.1, 0.8), collection, wood, bevel=0.04, segments=3)
        stile.matrix_world = Matrix.Translation(ACROSS * (side * 0.5) + UP * 0.55 - DEPTH * 0.01) @ face
    brace = bed.box("Gate.Brace", (1.12, 0.08, 0.13), collection, wood, bevel=0.04, segments=3)
    brace.matrix_world = (Matrix.Translation(UP * 0.55 - DEPTH * 0.05) @ face
                          @ Matrix.Rotation(math.atan2(0.6, 1.0), 4, "Y"))
    iron = toon.material("metal_dark")
    for k, height in enumerate((0.25, 0.85)):
        hinge = bed.box(f"Gate.Hinge{k}", (0.24, 0.12, 0.06), collection, iron, bevel=0.015, segments=2)
        hinge.matrix_world = Matrix.Translation(ACROSS * -0.56 + UP * height - DEPTH * 0.03) @ face
    latch = bed.box("Gate.Latch", (0.22, 0.12, 0.06), collection, iron, bevel=0.015, segments=2)
    latch.matrix_world = Matrix.Translation(ACROSS * 0.56 + UP * 0.7 - DEPTH * 0.03) @ face
    # The plaque hangs from the top rail on two cords.
    plaque = bed.box("Gate.Plaque", (0.46, 0.05, 0.36), collection, wood, bevel=0.035, segments=3)
    centre = ACROSS * 0.15 + UP * 0.56 - DEPTH * 0.08
    plaque.matrix_world = Matrix.Translation(centre) @ face
    cords = bmesh.new()
    for side in (-1, 1):
        a = centre + ACROSS * (side * 0.16) + UP * 0.16
        top = UP * 0.86 + ACROSS * 0.15 - DEPTH * 0.06
        parts.tube(cords, [a, a.lerp(top, 0.5), top], 0.009, taper=1.0)
    toon.outline(parts.mesh_object("Gate.Cords", cords, collection, "metal_dark"), style.LEAF_OUTLINE_WIDTH)
    painted = bmesh.new()
    _crescent(painted, centre - DEPTH * 0.035 - ACROSS * 0.05, 0.12)
    for offset, size in ((ACROSS * 0.12 + UP * 0.08, 0.045), (ACROSS * 0.14 - UP * 0.07, 0.032)):
        _star(painted, centre - DEPTH * 0.035 + offset, size)
    parts.flat(parts.mesh_object("Gate.Moon", painted, collection, "petal"))


def _mailbox(collection) -> None:
    """A rural mailbox on a post: a rounded metal box with its door open, a letter peeking out, the red flag up."""
    length, half, wall = 0.62, 0.19, 0.19  # half-width; the straight sides' height
    # Built along +X (the door at x=0), then turned so the door faces left of the camera and the flag shows.
    profile = [(half, 0.0), (half, wall)] + [(half * math.cos(a), wall + half * math.sin(a))
                                            for a in (math.pi * k / 12 for k in range(1, 12))] + [(-half, wall), (-half, 0.0)]
    bm = bmesh.new()
    front = [bm.verts.new((0.0, y, z)) for y, z in profile]
    back = [bm.verts.new((length, y, z)) for y, z in profile]
    for k in range(len(profile)):
        j = (k + 1) % len(profile)
        bm.faces.new((front[k], front[j], back[j], back[k]))
    bm.faces.new(back)
    bm.faces.new(list(reversed(front)))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    turn = Matrix.Translation(UP * 0.72) @ Matrix.Rotation(math.radians(90), 4, "Z")
    body = parts.mesh_object("Mailbox", bm, collection, "metal")
    body.matrix_world = turn
    toon.outline(body)
    # The open door hangs down from the front edge, inside showing dark.
    door = bmesh.new()
    face = [door.verts.new((0.0, y, z)) for y, z in profile]
    door.faces.new(face)
    door_obj = parts.mesh_object("Mailbox.Door", door, collection, "metal")
    door_obj.matrix_world = turn @ Matrix.Rotation(math.radians(-140), 4, "Y")
    parts.flat(door_obj)
    hole = bmesh.new()
    face = [hole.verts.new((-0.005, y * 0.88, 0.02 + z * 0.88)) for y, z in profile]
    hole.faces.new(list(reversed(face)))
    parts.mesh_object("Mailbox.Inside", hole, collection, "metal_dark").matrix_world = turn
    letter = bed.box("Mailbox.Letter", (0.26, 0.24, 0.025), collection, toon.material("paper"), bevel=0.008,
                     segments=2, outline=style.LEAF_OUTLINE_WIDTH)
    letter.matrix_world = turn @ Matrix.Translation((-0.03, 0.0, 0.17)) @ Matrix.Rotation(math.radians(-25), 4, "Y")
    # The flag on the side facing the camera, raised.
    red = toon.material("flag")
    flag = bed.box("Mailbox.FlagArm", (0.05, 0.03, 0.34), collection, red, bevel=0.012, segments=2,
                   outline=style.LEAF_OUTLINE_WIDTH)
    flag.matrix_world = turn @ Matrix.Translation((0.42, -half - 0.03, 0.3))
    plate = bed.box("Mailbox.Flag", (0.16, 0.03, 0.11), collection, red, bevel=0.012, segments=2,
                    outline=style.LEAF_OUTLINE_WIDTH)
    plate.matrix_world = turn @ Matrix.Translation((0.36, -half - 0.03, 0.42))
    wood = toon.material("wood")
    post = bed.box("Mailbox.Post", (0.14, 0.14, 0.72), collection, wood, bevel=0.04, segments=3)
    post.matrix_world = Matrix.Translation(UP * 0.36) @ Matrix.Rotation(math.radians(90), 4, "Z") @ Matrix.Translation((0.31, 0, 0))
    shelf = bed.box("Mailbox.Shelf", (0.5, 0.26, 0.05), collection, wood, bevel=0.02, segments=2)
    shelf.matrix_world = turn @ Matrix.Translation((0.31, 0, -0.025))


def _icon(collection) -> None:
    """The launcher icon's picture: a watered bed with one big sprout. The background colour is the icon's own."""
    bed.build(collection, wet=True)
    p = Vector((0.0, 0.0, 0.0))
    parts.sprout(collection, "Icon", Vector((p.x, p.y, bed.soil_height(p.x, p.y))) + UP * 0.02, 0.0, scale=5.5)


# --- Crop pictures -----------------------------------------------------------------------------------------------

def _emblem(collection, crop: str, matrix: Matrix) -> None:
    """One crop as a small standing model, front to -Y and base at the origin, placed by `matrix` (which may squash
    it flat). Its parts hang off an empty, so a squash shears rotated parts properly."""
    before = set(collection.objects)
    EMBLEMS[crop](collection)
    handle = bpy.data.objects.new(f"Emblem.{crop}", None)
    collection.objects.link(handle)
    handle.matrix_world = matrix
    bpy.context.view_layer.update()
    for obj in set(collection.objects) - before - {handle}:
        obj.parent = handle
        obj.matrix_parent_inverse = Matrix.Identity(4)


def _radish(collection) -> None:
    bulb = bmesh.new()
    parts.sphere(bulb, Vector((0, 0, 0.11)), 0.1, squash=0.95)
    toon.outline(parts.mesh_object("Emblem.Radish", bulb, collection, "radish"), style.LEAF_OUTLINE_WIDTH)
    tail = bmesh.new()
    parts.tube(tail, [Vector((0, 0, 0.03)), Vector((0.01, 0, -0.04)), Vector((0.03, 0, -0.09))], 0.012, taper=0.2)
    toon.outline(parts.mesh_object("Emblem.RadishTail", tail, collection, "radish_tail"), style.LEAF_OUTLINE_WIDTH)
    _leaf_fan(collection, Vector((0, 0, 0.19)), 3, 0.17, 0.11, spread=32)


def _lettuce(collection) -> None:
    leaves = bmesh.new()
    for k in range(8):
        a = math.pi * (-0.1 + 1.2 * k / 7)  # a fan of round leaves behind the heart, up and to the sides
        out = Vector((math.cos(a), 0.0, math.sin(a)))
        parts.leaf(leaves, Vector((0, 0.02, 0.1)), out, Vector((0, -1, 0)), 0.17, 0.16, lobes=5, lobe_depth=0.12,
                   peak=1.4)
    parts.flat(parts.mesh_object("Emblem.LettuceLeaves", leaves, collection, "leaf"))
    heart = bmesh.new()
    parts.sphere(heart, Vector((0, -0.03, 0.1)), 0.1, squash=0.95)
    toon.outline(parts.mesh_object("Emblem.LettuceHeart", heart, collection, "lettuce_heart"), style.LEAF_OUTLINE_WIDTH)


def _carrot(collection) -> None:
    bm = bmesh.new()
    parts.lathe(bm, [(0.05, 0.0), (0.075, -0.02), (0.07, -0.08), (0.045, -0.17), (0.012, -0.27)])
    root = parts.mesh_object("Emblem.Carrot", bm, collection, "carrot")
    root.location = (0, 0, 0.29)
    toon.outline(root, style.LEAF_OUTLINE_WIDTH)
    _leaf_fan(collection, Vector((0, 0, 0.29)), 3, 0.18, 0.1, spread=28, lobes=3)


def _pumpkin(collection) -> None:
    pumpkin.fruit(collection, "Emblem.Pumpkin", Vector((0, 0, 0)), 0.15, 0.12, "pumpkin", "leaf_broad",
                  "pumpkin_groove")


def _sunflower(collection) -> None:
    stem = bmesh.new()
    parts.tube(stem, [Vector((0, 0, 0)), Vector((0, 0, 0.1)), Vector((0, 0, 0.18))], 0.018)
    toon.outline(parts.mesh_object("Emblem.SunflowerStem", stem, collection, "leaf_broad"), style.LEAF_OUTLINE_WIDTH)
    sunflower.head(collection, "Emblem.Sunflower", Vector((0, 0, 0.2)), Vector((0, -1, 0.15)).normalized(), 0.17)


EMBLEMS = {"radish": _radish, "lettuce": _lettuce, "carrot": _carrot, "pumpkin": _pumpkin, "sunflower": _sunflower}


def _leaf_fan(collection, base: Vector, count: int, length: float, width: float, spread: float, lobes: int = 0) -> None:
    leaves = bmesh.new()
    for k in range(count):
        a = math.radians(spread * (k - (count - 1) / 2))
        along = Vector((math.sin(a), 0.0, math.cos(a)))
        parts.leaf(leaves, base, along, Vector((0, -1, 0)), length, width, lobes=lobes, lobe_depth=0.35, peak=1.3)
    parts.flat(parts.mesh_object("Emblem.Leaves", leaves, collection, "leaf"))


# --- Shapes and framing ------------------------------------------------------------------------------------------

def _crescent(bm: bmesh.types.BMesh, centre: Vector, radius: float) -> None:
    """A crescent moon facing the camera, opening to the right."""
    up = TO_CAMERA.cross(ACROSS).normalized()
    ring = []
    for k in range(25):  # the outer arc, then back along the inner one
        a = math.radians(70 + 220 * k / 24)
        ring.append(centre + (ACROSS * math.cos(a) + up * math.sin(a)) * radius)
    for k in range(25):
        a = math.radians(290 - 220 * k / 24)
        ring.append(centre + ACROSS * (radius * 0.45) + (ACROSS * math.cos(a) + up * math.sin(a)) * radius * 0.82)
    middle = bm.verts.new(centre - ACROSS * (radius * 0.55))
    verts = [bm.verts.new(p) for p in ring]
    for k in range(len(verts) - 1):
        bm.faces.new((middle, verts[k], verts[k + 1]))


def _star(bm: bmesh.types.BMesh, centre: Vector, radius: float) -> None:
    up = TO_CAMERA.cross(ACROSS).normalized()
    middle = bm.verts.new(centre)
    ring = [bm.verts.new(centre + (ACROSS * math.cos(a) + up * math.sin(a)) * (radius if k % 2 == 0 else radius * 0.45))
            for k, a in enumerate(math.pi / 2 + math.pi * j / 5 for j in range(10))]
    for k in range(10):
        bm.faces.new((middle, ring[k], ring[(k + 1) % 10]))


def _frame(card, collection) -> None:
    """Point the camera at the collection's middle and zoom so it fills the frame with MARGIN on each side."""
    camera = card.camera
    bpy.context.view_layer.update()
    right = camera.matrix_world.col[0].xyz.normalized()
    up = camera.matrix_world.col[1].xyz.normalized()
    back = camera.matrix_world.col[2].xyz.normalized()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    xs, ys = [], []
    for obj in collection.all_objects:
        if obj.type != "MESH":
            continue
        evaluated = obj.evaluated_get(depsgraph)
        for corner in evaluated.bound_box:
            p = evaluated.matrix_world @ Vector(corner)
            xs.append(p.dot(right))
            ys.append(p.dot(up))
    width, height = max(xs) - min(xs), max(ys) - min(ys)
    camera.data.ortho_scale = max(width, height) / (1 - 2 * MARGIN)
    middle = right * ((max(xs) + min(xs)) / 2) + up * ((max(ys) + min(ys)) / 2)
    camera.location = middle + back * 10.0


def _turn(collection, degrees: float) -> None:
    """Swing the whole prop about the vertical through the origin."""
    if not degrees:
        return
    pivot = bpy.data.objects.new("Turn", None)
    collection.objects.link(pivot)
    bpy.context.view_layer.update()
    for obj in collection.objects:
        if obj is not pivot and obj.parent is None:
            obj.parent = pivot
            obj.matrix_parent_inverse = Matrix.Identity(4)
    pivot.rotation_euler.z = math.radians(degrees)


def _remove(name: str) -> None:
    collection = bpy.data.collections.get(name)
    if collection:
        for obj in list(collection.all_objects):
            bpy.data.objects.remove(obj)
        bpy.data.collections.remove(collection)
