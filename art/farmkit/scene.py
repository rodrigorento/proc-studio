"""The card scene: render settings, the isometric camera, the sun and the time of day."""
import math

import bpy
from mathutils import Euler, Vector

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
    old = bpy.data.collections.get(name)
    if old:
        for obj in list(old.all_objects):
            bpy.data.objects.remove(obj)
        for child in list(old.children_recursive):
            bpy.data.collections.remove(child)
        bpy.data.collections.remove(old)
    new = bpy.data.collections.new(name)
    scene.collection.children.link(new)
    return new


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
