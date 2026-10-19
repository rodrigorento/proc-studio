"""The card scene: render settings, the isometric camera, the sun and the time of day."""
import math

import bpy
from mathutils import Euler, Matrix, Vector

from . import style, toon

SCENE = "Card"
CAMERA = "CardCamera"
SUN = "CardSun"
ISOMETRIC = Euler((math.radians(54.7356), 0.0, math.radians(45.0)))


def card_scene() -> bpy.types.Scene:
    """The scene every bed is built and rendered in, set up on first use."""
    scene = bpy.data.scenes.get(SCENE) or bpy.data.scenes.new(SCENE)
    bpy.context.window.scene = scene
    render = scene.render
    render.engine = "BLENDER_EEVEE"
    render.resolution_x = render.resolution_y = style.CAMERA["resolution"]
    render.resolution_percentage = 100
    render.film_transparent = True
    render.image_settings.file_format = "PNG"
    render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    _camera(scene)
    _sun(scene)
    if scene.world is None:
        scene.world = bpy.data.worlds.new(SCENE)
    return scene


def collection(scene: bpy.types.Scene, name: str) -> bpy.types.Collection:
    """An empty collection under the scene, replacing any previous one with that name."""
    remove(name)
    new = bpy.data.collections.new(name)
    scene.collection.children.link(new)
    return new


def remove(name: str) -> None:
    """Delete a collection and every object in it, if it exists."""
    old = bpy.data.collections.get(name)
    if old:
        for obj in list(old.all_objects):
            bpy.data.objects.remove(obj)
        for child in list(old.children_recursive):
            bpy.data.collections.remove(child)
        bpy.data.collections.remove(old)


def group(collection: bpy.types.Collection, name: str) -> bpy.types.Object:
    """Parent every top-level object of the collection to a new empty at the origin, so they move as one."""
    handle = bpy.data.objects.new(name, None)
    collection.objects.link(handle)
    bpy.context.view_layer.update()
    for obj in collection.all_objects:
        if obj is not handle and obj.parent is None:
            obj.parent = handle
            obj.matrix_parent_inverse = Matrix.Identity(4)
    return handle


def extent(camera: bpy.types.Object, objects) -> tuple[float, float, float, float]:
    """The meshes' (left, right, bottom, top) as seen by the camera, in metres across its view."""
    xs, ys, _ = _project(camera, objects)
    return min(xs), max(xs), min(ys), max(ys)


def frame(camera: bpy.types.Object, objects, margin: float) -> tuple[float, float]:
    """Point the camera at the objects' middle and zoom so they fill its view with `margin` (a share of the view)
    on each side, standing it off in front of the nearest one. Returns their (width, height) across the view."""
    xs, ys, zs = _project(camera, objects)
    width, height = max(xs) - min(xs), max(ys) - min(ys)
    camera.data.ortho_scale = max(width, height) / (1 - 2 * margin)
    right, up, back = (camera.matrix_world.col[i].xyz.normalized() for i in range(3))
    camera.location = right * ((min(xs) + max(xs)) / 2) + up * ((min(ys) + max(ys)) / 2) + back * (max(zs) + 10.0)
    camera.data.clip_end = max(zs) - min(zs) + 20.0
    return width, height


def _project(camera: bpy.types.Object, objects) -> tuple[list[float], list[float], list[float]]:
    """The meshes' bounding-box corners along the camera's right, up and back axes. The camera's scene must be the
    window's, since that is the one brought up to date."""
    bpy.context.view_layer.update()
    axes = [camera.matrix_world.col[i].xyz.normalized() for i in range(3)]
    depsgraph = bpy.context.evaluated_depsgraph_get()
    projected = ([], [], [])
    for obj in objects:
        if obj.type not in {"MESH", "FONT"}:
            continue
        evaluated = obj.evaluated_get(depsgraph)
        for corner in evaluated.bound_box:
            p = evaluated.matrix_world @ Vector(corner)
            for values, axis in zip(projected, axes):
                values.append(p.dot(axis))
    return projected


def set_light(scene: bpy.types.Scene, name: str) -> None:
    """Point the sun, set the ambient light and tint every material for one time of day."""
    light = style.LIGHTS[name]
    elevation = math.radians(light["elevation"])
    azimuth = math.radians(light["azimuth"])
    towards_sun = Vector((math.cos(elevation) * math.cos(azimuth),
                          math.cos(elevation) * math.sin(azimuth),
                          math.sin(elevation)))
    sun = scene.objects[SUN]
    sun.rotation_euler = (-towards_sun).to_track_quat("-Z", "Y").to_euler()
    world = scene.world
    if hasattr(world, "use_nodes"):
        world.use_nodes = True
    background = world.node_tree.nodes["Background"]
    background.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)  # so ambient is exactly the strength
    background.inputs["Strength"].default_value = light["ambient"]
    toon.set_time(light)


def render(scene: bpy.types.Scene, path: str) -> str:
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True, scene=scene.name)
    return path


def _camera(scene: bpy.types.Scene) -> None:
    camera = scene.objects.get(CAMERA)
    if camera is None:
        camera = bpy.data.objects.new(CAMERA, bpy.data.cameras.new(CAMERA))
        scene.collection.objects.link(camera)
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = style.CAMERA["ortho_scale"]
    camera.rotation_euler = ISOMETRIC
    back = ISOMETRIC.to_matrix() @ Vector((0.0, 0.0, 10.0))
    camera.location = Vector(style.CAMERA["target"]) + back
    camera.data.clip_end = 30.0
    scene.camera = camera


def _sun(scene: bpy.types.Scene) -> None:
    if scene.objects.get(SUN):
        return
    data = bpy.data.lights.new(SUN, "SUN")
    data.energy = math.pi  # a white surface facing the sun then lights to about 1.0, which the toon bands expect
    data.angle = math.radians(1.5)  # crisp shadow edges suit the toon bands
    sun = bpy.data.objects.new(SUN, data)
    scene.collection.objects.link(sun)
