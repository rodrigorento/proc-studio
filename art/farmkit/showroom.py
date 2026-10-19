"""The showroom: every crop at every stage, and the props, laid out in one scene to review together.

    from farmkit import showroom
    showroom.build(wet=True, light="day")       # a "Showroom" scene; look through its camera in the viewport
    showroom.render("art/build/showroom.png")   # on the app's dark background
    showroom.save("art/blend/showroom.blend")   # the scene alone, for anyone to open

Rows are the crops (Today's radish at the top), columns the stages; the sunflower's bloom sits under "ripe".
The props stand in a row along the bottom.
"""
import os

import bpy
import numpy
from mathutils import Matrix

from . import bed, scene, stills, toon
from .crops.parts import ACROSS, DEPTH
from .render import CROPS

SCENE = "Showroom"
CAMERA = "ShowroomCamera"
COLUMNS = ["seed", "sprout", "growing", "ripe", "overripe"]
ACROSS_STEP = 1.75  # metres between beds left to right; a bed is 1 m across
DEPTH_STEP = 3.3  # metres between rows, front to back: about 1.9 m down the picture
PROP_SIZE = 0.8  # each prop is scaled to fit a square this big in the picture, standing on one baseline
PROPS = [name for name in stills.NAMES if name != "icon"]
WIDTH = 2400  # pixels
MARGIN = 0.03
LABEL = "#C9CBD1"
BACKGROUND = "#16171A"


def build(wet: bool = True, light: str = "day") -> bpy.types.Scene:
    card = scene.card_scene()  # the sun and world the showroom shares
    toon.reset()
    show = _scene(card)
    bpy.context.window.scene = show  # so placing and measuring update this scene, not the card
    show.camera.matrix_world = Matrix.Translation((0, 0, 0)) @ scene.ISOMETRIC.to_matrix().to_4x4()
    for row, crop in enumerate(CROPS):
        stages = list(CROPS[crop].STAGES)
        _label(show, crop.capitalize(), _at(-1, row) + ACROSS * 0.45, align="RIGHT")
        for stage in stages:
            column = COLUMNS.index(stage) if stage in COLUMNS else COLUMNS.index("ripe")
            collection = scene.collection(show, f"Showroom.{crop}.{stage}")
            bed.build(collection, wet=wet)
            CROPS[crop].build(collection, stage, wet=wet)
            scene.group(collection, f"{crop}.{stage}").matrix_world = Matrix.Translation(_at(column, row))
    for column, name in enumerate(COLUMNS):
        _label(show, "ripe / bloom" if name == "ripe" else name, _at(column, -1) - DEPTH * 0.9)
    props_row = len(CROPS)
    _label(show, "Props", _at(-1, props_row) + ACROSS * 0.45, align="RIGHT")
    right_axis, up_axis = (show.camera.matrix_world.col[i].xyz.normalized() for i in range(2))
    baseline = _at(0, props_row).dot(up_axis) - PROP_SIZE / 2
    slot = (len(COLUMNS) - 1) * ACROSS_STEP / (len(PROPS) - 1)  # the row spans the bed columns
    for k, name in enumerate(PROPS):
        collection = scene.collection(show, f"Showroom.prop.{name}")
        stills.build(collection, name)
        handle = scene.group(collection, name)
        left, right, bottom, top = scene.extent(show.camera, collection.all_objects)
        size = PROP_SIZE / max(right - left, top - bottom)
        middle = _at(k * slot / ACROSS_STEP, props_row).dot(right_axis)
        # Scale about the origin, then slide across the picture so the prop is centred in its slot and on the line.
        shift = right_axis * (middle - size * (left + right) / 2) + up_axis * (baseline - size * bottom)
        handle.matrix_world = Matrix.Translation(shift) @ Matrix.Scale(size, 4)
    scene.set_light(show, light)
    return show


def render(path: str) -> str:
    """Render the showroom, fitted to its contents, onto the app's dark background."""
    show = bpy.data.scenes[SCENE]
    bpy.context.window.scene = show
    everything = [obj for obj in show.objects if obj.type in {"MESH", "FONT"}]
    width, height = scene.frame(show.camera, everything, MARGIN)
    show.render.resolution_x = WIDTH
    show.render.resolution_y = round(WIDTH * height / width)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    scene.render(show, path)
    _on_background(path, BACKGROUND)
    return path


def save(path: str) -> str:
    """Write the showroom scene, with everything it uses, to its own .blend file."""
    show = bpy.data.scenes[SCENE]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.data.libraries.write(path, {show}, path_remap="RELATIVE_ALL", fake_user=True, compress=True)
    return path


def _scene(card: bpy.types.Scene) -> bpy.types.Scene:
    """The showroom scene, emptied: the card's render settings, world and sun, and its own camera."""
    show = bpy.data.scenes.get(SCENE) or bpy.data.scenes.new(SCENE)
    for child in list(show.collection.children):
        scene.remove(child.name)
    for obj in list(show.collection.objects):
        if obj.name != scene.SUN:
            bpy.data.objects.remove(obj)
    show.render.engine = card.render.engine
    show.render.film_transparent = True
    show.render.resolution_percentage = 100
    show.render.image_settings.file_format = "PNG"
    show.render.image_settings.color_mode = "RGBA"
    show.view_settings.view_transform = "Standard"
    show.view_settings.look = "None"
    show.world = card.world
    sun = card.objects[scene.SUN]
    if sun.name not in show.collection.objects:
        show.collection.objects.link(sun)
    camera = bpy.data.objects.new(CAMERA, bpy.data.cameras.new(CAMERA))
    show.collection.objects.link(camera)
    camera.data.type = "ORTHO"
    camera.data.clip_end = 200.0
    camera.rotation_euler = scene.ISOMETRIC
    show.camera = camera
    return show


def _at(column: float, row: float):
    """Where a bed's middle goes: columns run left to right in the picture, rows from the back to the front."""
    return ACROSS * (column * ACROSS_STEP) - DEPTH * (row * DEPTH_STEP)


def _label(show: bpy.types.Scene, text: str, at, align: str = "CENTER") -> None:
    curve = bpy.data.curves.new(f"Label.{text}", "FONT")
    curve.body = text
    curve.size = 0.32
    curve.align_x = align
    curve.align_y = "CENTER"
    obj = bpy.data.objects.new(f"Label.{text}", curve)
    obj.location = at
    obj.rotation_euler = scene.ISOMETRIC  # flat towards the camera
    obj.data.materials.append(_label_material())
    show.collection.objects.link(obj)


def _label_material() -> bpy.types.Material:
    mat = bpy.data.materials.get("Label") or bpy.data.materials.new("Label")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    emit = nodes.new("ShaderNodeEmission")
    emit.inputs["Color"].default_value = toon.linear(LABEL)
    out = nodes.new("ShaderNodeOutputMaterial")
    mat.node_tree.links.new(emit.outputs["Emission"], out.inputs["Surface"])
    return mat


def _on_background(path: str, color: str) -> None:
    """Lay the transparent render over a flat colour, so it reads as it will in the app."""
    image = bpy.data.images.load(path)
    try:
        pixels = numpy.empty(len(image.pixels), dtype=numpy.float32)
        image.pixels.foreach_get(pixels)
        rgba = pixels.reshape(-1, 4)
        h = color.lstrip("#")
        background = numpy.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=numpy.float32)
        alpha = rgba[:, 3:4]
        rgba[:, :3] = rgba[:, :3] * alpha + background * (1 - alpha)
        rgba[:, 3] = 1.0
        image.pixels.foreach_set(rgba.ravel())
        image.filepath_raw = path
        image.file_format = "PNG"
        image.save()
    finally:
        bpy.data.images.remove(image)
