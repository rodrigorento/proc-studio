"""The home screen's field: a lawn holding the four plots, the shadow a bed casts on the grass, and the pegs and
string that mark out an empty plot.

The pegs and the shadow are rendered like the beds (the card camera at the cards' scale), so they stack with a bed
card: the pegs on a 640 px card, the shadow on a wider one. The lawn is
rendered with the same camera turned the same way at the cards' scale, only wider, so a card drawn at its plot's
offset sits exactly on that plot. field.json gives those offsets in the lawn's pixels, back plot to front.

The shadow card is wider than a bed card (SHADOW_WIDTH) and centred on the same point. field.json also gives a bed's
outline for taps: its footprint's half width and half height and how far up it reaches, in the lawn's pixels.

Files: field_meadow.png, field_plot_marked.png, field_bed_shadow.png and field.json.
"""
import json
import math
import os
import random

import bmesh
import bpy
from mathutils import Vector

from . import bed, scene, style, toon
from .crops import parts

COLLECTION = "Field"
PITCH = 1.2  # metres between neighbouring plots' middles; a bed is 1 m wide, so 0.2 m of grass between beds
LAWN = 2.7  # the lawn's side, metres: the plots and a strip of grass around them
LAWN_DEPTH = 0.03
# The plots back to front as the camera sees them: back, left, right, front. The camera looks from +X/-Y.
PLOTS = [(-PITCH / 2, PITCH / 2), (-PITCH / 2, -PITCH / 2), (PITCH / 2, PITCH / 2), (PITCH / 2, -PITCH / 2)]
PEG = 0.45  # the marked square's half side, inside the bed's footprint
SHADOW = 0.3  # how dark a bed's shadow is on the grass
SHADOW_WIDTH = 960  # pixels: the day sun throws a bed's shadow past the right side of its 640 px card
TAP_HEIGHT = 0.3  # metres: a bed's outline for taps reaches this far up from its footprint, past the tops of its posts


def lawn(collection: bpy.types.Collection, seed: int = 7) -> None:
    """The lawn, as in art/concepts/field/meadow.png: a slab of turf with a scalloped edge, darker patches and chunky
    tufts. Tufts that stand up only grow inside the plots, where a bed card covers them, and on the edge, where
    nothing stands in front of them: a tuft just in front of a bed would be painted over by the bed's card."""
    rng = random.Random(seed)
    half = LAWN / 2
    slab = bmesh.new()
    bmesh.ops.create_cube(slab, size=1.0)
    bmesh.ops.scale(slab, vec=(LAWN - 0.06, LAWN - 0.06, LAWN_DEPTH), verts=slab.verts)
    bmesh.ops.translate(slab, vec=(0, 0, -LAWN_DEPTH / 2), verts=slab.verts)
    toon.outline(parts.mesh_object("Lawn", slab, collection, "grass", smooth=False))

    # The edge: rounded clumps of grass all round, overlapping like a scalloped hem.
    hem = bmesh.new()
    steps = int(LAWN / 0.09)
    for side in range(4):
        for k in range(steps):
            t = (k + 0.5 + rng.uniform(-0.1, 0.1)) / steps * LAWN - half
            x, y, out = [(t, -half, (0, -1)), (half, t, (1, 0)), (t, half, (0, 1)), (-half, t, (-1, 0))][side]
            _clump(hem, Vector((x - out[0] * 0.05, y - out[1] * 0.05, -0.01)), Vector((*out, 0.0)), rng, 0.11)
    toon.outline(parts.mesh_object("Lawn.Hem", hem, collection, "grass_patch", smooth=False), style.LEAF_OUTLINE_WIDTH)

    patches = bmesh.new()
    for _ in range(16):
        x, y = rng.uniform(-half + 0.25, half - 0.25), rng.uniform(-half + 0.25, half - 0.25)
        _patch(patches, Vector((x, y, 0.001)), rng.uniform(0.12, 0.22), rng)
    parts.mesh_object("Lawn.Patches", patches, collection, "grass_patch", smooth=False)

    # Painted blades all over, flat, so they can sit anywhere: the lawn's texture.
    marks = bmesh.new()
    for _ in range(110):
        x, y = rng.uniform(-half + 0.08, half - 0.08), rng.uniform(-half + 0.08, half - 0.08)
        _mark(marks, Vector((x, y, 0.002)), rng)
    parts.mesh_object("Lawn.Marks", marks, collection, "grass_patch", smooth=False)

    tufts = bmesh.new()
    for px, py in PLOTS:
        for _ in range(6):
            x, y = px + rng.uniform(-0.36, 0.36), py + rng.uniform(-0.36, 0.36)
            _tuft(tufts, Vector((x, y, 0.0)), rng, rng.uniform(0.11, 0.15))
    parts.flat(parts.mesh_object("Lawn.Tufts", tufts, collection, "grass_tuft"))

    # Daisies: flat, so they can sit anywhere without standing in front of a bed.
    petals, hearts = bmesh.new(), bmesh.new()
    for x, y in [(-1.22, 0.4), (-0.3, -1.25), (1.24, -0.55), (0.65, 1.22), (-1.25, -0.9), (1.2, 0.95), (0.0, 0.02),
                 (0.35, -0.55), (-0.5, 0.75)]:
        _daisy(petals, hearts, Vector((x, y, 0.004)), rng)
    toon.outline(parts.mesh_object("Lawn.Petals", petals, collection, "flower", smooth=False), style.LEAF_OUTLINE_WIDTH)
    parts.mesh_object("Lawn.Hearts", hearts, collection, "yellow_flower", smooth=False)


def marked(collection: bpy.types.Collection) -> None:
    """An empty plot: four pegs at its corners and a taut string between them, on a plot at the origin."""
    corners = [Vector((x * PEG, y * PEG, 0.0)) for x, y in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    pegs = bmesh.new()
    for c in corners:
        peg = bmesh.ops.create_cube(pegs, size=1.0)["verts"]
        bmesh.ops.scale(pegs, vec=(0.06, 0.06, 0.15), verts=peg)
        bmesh.ops.translate(pegs, vec=c + Vector((0, 0, 0.075)), verts=peg)
    obj = parts.mesh_object("Plot.Pegs", pegs, collection, "wood", smooth=False)
    bevel = obj.modifiers.new("Round", "BEVEL")
    bevel.width, bevel.segments = 0.012, 2
    toon.outline(obj, style.LEAF_OUTLINE_WIDTH * 1.5)
    string = bmesh.new()
    for a, b in zip(corners, corners[1:] + corners[:1]):
        parts.tube(string, [a + Vector((0, 0, 0.115)), b + Vector((0, 0, 0.115))], 0.011, taper=1.0)
    toon.outline(parts.mesh_object("Plot.String", string, collection, "seed"), style.LEAF_OUTLINE_WIDTH * 0.6)


def shadow(collection: bpy.types.Collection) -> None:
    """The shadow an empty bed casts on the grass, alone: the bed casts it but isn't seen."""
    bed.build(collection, wet=True)
    for obj in collection.all_objects:
        obj.visible_camera = False
    ground = bmesh.new()
    bmesh.ops.create_grid(ground, x_segments=1, y_segments=1, size=1.8)
    catcher = bpy.data.meshes.new("Shadow.Ground")
    ground.to_mesh(catcher)
    ground.free()
    obj = bpy.data.objects.new("Shadow.Ground", catcher)
    obj.location.z = 0.001
    collection.objects.link(obj)
    catcher.materials.append(_shadow_material())


def layout(camera: bpy.types.Object, lawn_objects) -> dict:
    """Frame the lawn with every plot's card in view, at the cards' scale. Returns the lawn image's size and, for
    each plot, its card's rectangle and its middle and front corner on the ground, in the lawn image's pixels."""
    per_pixel = style.CAMERA["ortho_scale"] / style.CAMERA["resolution"]
    half_card = style.CAMERA["ortho_scale"] / 2
    right, up = (camera.matrix_world.col[i].xyz.normalized() for i in range(2))
    target = Vector(style.CAMERA["target"])

    def view(p: Vector) -> tuple[float, float]:
        return p.dot(right), p.dot(up)

    left, right_edge, bottom, top = scene.extent(camera, lawn_objects)
    for x, y in PLOTS:
        cx, cy = view(target + Vector((x, y, 0)))
        left, right_edge = min(left, cx - half_card), max(right_edge, cx + half_card)
        bottom, top = min(bottom, cy - half_card), max(top, cy + half_card)
    width, height = math.ceil((right_edge - left) / per_pixel), math.ceil((top - bottom) / per_pixel)
    camera.data.ortho_scale = max(width, height) * per_pixel
    middle = Vector((left + width * per_pixel / 2, bottom + height * per_pixel / 2))
    back = camera.matrix_world.col[2].xyz.normalized()
    camera.location = right * middle.x + up * middle.y + back * 10.0

    def pixels(vx: float, vy: float) -> list[float]:
        return [round((vx - left) / per_pixel, 1), round((bottom + height * per_pixel - vy) / per_pixel, 1)]

    plots = []
    resolution = style.CAMERA["resolution"]
    for x, y in PLOTS:
        cx, cy = view(target + Vector((x, y, 0)))
        card = pixels(cx - half_card, cy + half_card)
        plots.append({
            "card": card + [resolution] * 2,
            "shadow": [round(card[0] - (SHADOW_WIDTH - resolution) / 2, 1), card[1], SHADOW_WIDTH, resolution],
            "middle": pixels(*view(Vector((x, y, 0)))),
            "front": pixels(*view(Vector((x + style.BED["size"] / 2, y - style.BED["size"] / 2, 0)))),
        })
    corner = style.BED["size"] / 2
    outline = {
        "half_width": round(view(Vector((corner, corner, 0)))[0] / per_pixel, 1),
        "half_height": round(view(Vector((-corner, corner, 0)))[1] / per_pixel, 1),
        "height": round(view(Vector((0, 0, TAP_HEIGHT)))[1] / per_pixel, 1),
    }
    return {"size": [width, height], "plots": plots, "outline": outline}


def render_all(out_dir: str) -> list[str]:
    os.makedirs(out_dir, exist_ok=True)
    card = scene.card_scene()
    toon.reset()
    scene.collection(card, "Bed")  # nothing of the bed cards in the frame
    scene.set_light(card, "day")
    written = []
    try:
        for name, build, width in (("field_plot_marked", marked, style.CAMERA["resolution"]),
                                   ("field_bed_shadow", shadow, SHADOW_WIDTH)):
            build(scene.collection(card, COLLECTION))
            # Wider at the cards' scale: the same metres per pixel, centred on the same point.
            card.render.resolution_x = width
            card.camera.data.ortho_scale = style.CAMERA["ortho_scale"] * width / style.CAMERA["resolution"]
            written.append(scene.render(card, os.path.join(out_dir, f"{name}.png")))
            scene.card_scene()
        collection = scene.collection(card, COLLECTION)
        lawn(collection)
        spots = layout(card.camera, collection.all_objects)
        card.render.resolution_x, card.render.resolution_y = spots["size"]
        written.append(scene.render(card, os.path.join(out_dir, "field_meadow.png")))
        with open(os.path.join(out_dir, "field.json"), "w") as f:
            json.dump(spots, f, indent=1)
        written.append(f.name)
    finally:
        scene.remove(COLLECTION)
        scene.card_scene()  # back to the card camera and size
    return written


def _tuft(bm: bmesh.types.BMesh, base: Vector, rng: random.Random, length: float) -> None:
    """A chunky tuft: five broad blades fanned up from one spot, turned to the camera like the crops' leaves."""
    side = parts.ACROSS
    facing = -parts.DEPTH
    for k in range(-2, 3):
        along = (Vector((0, 0, 1)) + side * (0.5 * k) + facing * rng.uniform(-0.15, 0.15)).normalized()
        parts.leaf(bm, base + side * (0.015 * k), along, facing, length * (1.0 - 0.16 * abs(k)), 0.065, peak=0.8)


def _clump(bm: bmesh.types.BMesh, base: Vector, out: Vector, rng: random.Random, size: float) -> None:
    """A rounded clump of grass leaning out over the lawn's edge."""
    side = Vector((-out.y, out.x, 0))
    for k in (-1, 0, 1):
        along = (out + Vector((0, 0, 0.25)) + side * (0.6 * k + rng.uniform(-0.1, 0.1))).normalized()
        parts.leaf(bm, base, along, Vector((0, 0, 1)), size * (1.0 - 0.2 * abs(k)), size * 0.9, peak=1.4)


def _mark(bm: bmesh.types.BMesh, at: Vector, rng: random.Random) -> None:
    """Three short blades painted flat on the lawn."""
    turn = rng.uniform(-0.4, 0.4)  # roughly upright as the camera sees it
    for spread in (-0.5, 0.0, 0.5):
        a = math.radians(135) + turn + spread
        along = Vector((math.cos(a), math.sin(a), 0))
        parts.leaf(bm, at, along, Vector((0, 0, 1)), rng.uniform(0.07, 0.1) * (0.75 if spread else 1.0), 0.024, peak=0.8)


def _patch(bm: bmesh.types.BMesh, centre: Vector, radius: float, rng: random.Random) -> None:
    """A soft, roundish patch of darker grass, flat on the lawn."""
    points = 28
    wobble = [rng.uniform(0, math.tau) for _ in range(2)]
    ring = []
    for k in range(points):
        a = k / points * math.tau
        r = radius * (1 + 0.12 * math.sin(2 * a + wobble[0]) + 0.08 * math.sin(3 * a + wobble[1]))
        ring.append(bm.verts.new(centre + Vector((math.cos(a) * r, math.sin(a) * r * 0.9, 0))))
    bm.faces.new(ring)


def _daisy(petals: bmesh.types.BMesh, hearts: bmesh.types.BMesh, at: Vector, rng: random.Random) -> None:
    turn = rng.uniform(0, math.tau)
    for k in range(6):
        a = turn + k * math.tau / 6
        parts.leaf(petals, at, Vector((math.cos(a), math.sin(a), 0)), Vector((0, 0, 1)), 0.045, 0.026, peak=1.2)
    parts.sphere(hearts, at + Vector((0, 0, 0.004)), 0.014, squash=0.4)


def _shadow_material() -> bpy.types.Material:
    """Clear where the sun reaches the ground, a flat dark band where something shades it, like the toon bands."""
    mat = bpy.data.materials.get("FieldShadow") or bpy.data.materials.new("FieldShadow")
    mat.surface_render_method = "BLENDED"
    nodes, links = toon._node_tree(mat)
    light = nodes.new("ShaderNodeBsdfDiffuse")
    to_rgb = nodes.new("ShaderNodeShaderToRGB")
    value = nodes.new("ShaderNodeRGBToBW")
    shaded = nodes.new("ShaderNodeMath")
    shaded.operation = "LESS_THAN"
    shaded.inputs[1].default_value = style.LIGHTS["day"]["ambient"] + 0.1
    dark = nodes.new("ShaderNodeMath")
    dark.operation = "MULTIPLY"
    dark.inputs[1].default_value = SHADOW
    ink = nodes.new("ShaderNodeEmission")
    ink.inputs["Color"].default_value = toon.linear(style.OUTLINE)
    clear = nodes.new("ShaderNodeBsdfTransparent")
    mix = nodes.new("ShaderNodeMixShader")
    output = nodes.new("ShaderNodeOutputMaterial")
    links.new(light.outputs[0], to_rgb.inputs[0])
    links.new(to_rgb.outputs["Color"], value.inputs[0])
    links.new(value.outputs[0], shaded.inputs[0])
    links.new(shaded.outputs[0], dark.inputs[0])
    links.new(dark.outputs[0], mix.inputs["Fac"])
    links.new(clear.outputs[0], mix.inputs[1])
    links.new(ink.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], output.inputs["Surface"])
    return mat
