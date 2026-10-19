"""The rendered moments: watering (the can when you water, a rain cloud when it waters itself), planting and
harvest.

Frames are posed in Python and rendered one by one at 12 frames a second, which suits the toon look.

Watering is one effect layer per light, shared by every bed: the bed is a holdout, so the planks hide water that
falls behind them and the rest of the frame stays transparent. The app plays it over the card while fading the
dry card into the wet one between WET_FROM and WET_TO.

Planting is one effect layer per crop and light, made the same way: the crop's seed packet tips over the bed and
pours a seed onto each spot the crop's seed card has a mound, and the app fades the empty card into the seed card
between SEED_FROM and SEED_TO.

A harvest is rendered as whole cards; the Android export keeps only what differs from the empty bed
(harvest_base_<light>.png), so the frames play over the empty-bed card and bring the crops' shadows with them.

File names: water_can_<light>_<nn>.png, water_rain_<light>_<nn>.png, plant_<crop>_<light>_<nn>.png,
harvest_<crop>_<stage>_<light>_<nn>.png.
"""
import math
import os
import random

import bpy
from mathutils import Matrix, Vector

from . import bed, props, render, scene, stills, style, toon
from .crops.parts import ACROSS, DEPTH, TO_CAMERA, UP

FPS = 12
SIZE = 480  # pixels; cards show at about 470 px on a phone, and moving frames need less detail than still ones
EFFECT = "Effect"

WATER_FRAMES = 24
WET_FROM, WET_TO = 7, 16  # the frames over which the app fades the dry card into the wet one
GRAVITY = 6.0  # metres per second squared: gentler than real, so the drops can be followed
# The can's path as (frame, across, depth, height, tilt in degrees), in picture directions: it swings in from the
# top right, tips and pours while drifting across the bed, then leaves past the top left.
CAN_PATH = [(0, 1.35, 0.3, 1.5, 0), (4, 0.5, 0.22, 0.86, 0), (7, 0.48, 0.2, 0.86, 45), (16, 0.24, -0.1, 0.84, 50),
            (19, 0.22, -0.12, 0.86, 0), (23, -0.45, -0.2, 2.2, 0)]
CAN_SCALE = 1.8  # the toy can is big next to the bed, so it reads on a small card
POUR = range(7, 16)
CLOUD_PATH = [(0, -1.5, 0.15, 1.02), (5, -0.02, 0.06, 0.88), (17, 0.04, 0.06, 0.88), (23, 1.55, 0.15, 1.05)]
CLOUD_SCALE = 1.7
RAIN = range(5, 17)

PLANT_FRAMES = 16
SEED_FROM, SEED_TO = 8, 13  # the frames over which the app fades the empty card into the crop's seed card
# The packet's path as (frame, across, depth, height, tilt in degrees, top towards the picture's left): it drops in
# over the right of the bed, tips its open top down, pours while drifting left, then rights itself and leaves past
# the top left.
PACKET_PATH = [(0, 1.25, 0.25, 1.5, 0), (3, 0.4, 0.14, 0.86, 20), (5, 0.34, 0.1, 0.84, 150),
               (10, -0.16, -0.04, 0.82, 160), (12, -0.24, -0.08, 0.92, 30), (15, -0.75, -0.2, 1.8, 0)]
PACKET_SCALE = 0.48  # of the packet prop, whose body is 0.92 m tall
PACKET_MIDDLE = Vector((0.0, 0.0, 0.46))  # in the prop: the middle of the packet, which it tips about
PACKET_MOUTH = Vector((0.0, 0.0, 0.95))  # in the prop: the middle of its top, where the seeds come out
POUR_FROM, POUR_TO = 5, 10  # the frames seeds leave the packet
SEED_COUNT = 16  # seeds poured into a bed, shared out over its spots
SEED_FALL = 3  # frames from the packet's mouth to the soil
SEED_SPARKLE = {1: 0.5, 2: 1.0, 3: 0.7, 4: 0.35}  # by frames since the last seed landed on a spot

HARVEST_FRAMES = 14
# By frames since a plant starts to move: how high it is (metres), how it is squashed (across, up) and leaning.
LIFT = [0.0, 0.0, 0.16, 0.45, 0.8, 1.25, 1.75, 2.35, 3.2]
SQUASH = [(1.1, 0.86), (1.14, 0.8), (0.86, 1.25), (0.93, 1.12), (0.97, 1.04)]
LEAN = [0, 0, 0, 8, 14, 20, 24, 26, 26]
MOUND = [1.0, 0.7, 0.35]  # the mound's height as the plant leaves it
SHRINK = [1.0, 1.0, 0.75, 0.5, 0.28, 0.1]  # what stays behind (the pumpkin's vine) shrinks into the soil
SPARKLE = {4: 0.5, 5: 1.0, 6: 0.8, 7: 0.45}
STAGGER = 3  # frames between the first and the last plant of a bed to pop


def harvest_stages(module) -> list[str]:
    """The stages a crop is harvested from: ripe, or overripe when late; the sunflower from bloom."""
    return [s for s in ("ripe", "overripe") if s in module.STAGES] or [list(module.STAGES)[-1]]


def render_water(out_dir: str, lights: list[str] | None = None) -> list[str]:
    lights = lights or list(style.LIGHTS)
    _clear(out_dir, "water_", lights)
    card = _card()
    bed.build(scene.collection(card, "Bed"), wet=False)
    layer = card.view_layers[0].layer_collection.children["Bed"]
    layer.holdout = True
    written = []
    try:
        for kind, pose in (("can", _can_frame), ("rain", _rain_frame)):
            for f in range(WATER_FRAMES):
                pose(scene.collection(card, EFFECT), f)
                for light in lights:
                    scene.set_light(card, light)
                    written.append(scene.render(card, os.path.join(out_dir, f"water_{kind}_{light}_{f:02d}.png")))
    finally:
        layer.holdout = False
        _done(card)
    return written


def render_plant(out_dir: str, crops: list[str] | None = None, lights: list[str] | None = None) -> list[str]:
    crops = crops or list(render.CROPS)
    lights = lights or list(style.LIGHTS)
    for crop in crops:
        _clear(out_dir, f"plant_{crop}_", lights)
    card = _card()
    written = []
    try:
        for crop in crops:
            render.build(card, crop, "seed", False)
            spots = _spots(card)
            render.build(card, None, None, False)
            card.view_layers[0].layer_collection.children["Bed"].holdout = True
            seeds = _seeds(spots)
            for f in range(PLANT_FRAMES):
                _plant_frame(scene.collection(card, EFFECT), crop, f, spots, seeds)
                for light in lights:
                    scene.set_light(card, light)
                    written.append(scene.render(card, os.path.join(out_dir, f"plant_{crop}_{light}_{f:02d}.png")))
    finally:
        bed_layer = card.view_layers[0].layer_collection.children.get("Bed")
        if bed_layer:
            bed_layer.holdout = False
        _done(card)
    return written


def render_harvest(out_dir: str, crops: list[str] | None = None, lights: list[str] | None = None) -> list[str]:
    crops = crops or list(render.CROPS)
    lights = lights or list(style.LIGHTS)
    for crop in crops:
        _clear(out_dir, f"harvest_{crop}_", lights)
    card = _card()
    written = []
    try:
        render.build(card, None, None, True)
        for light in lights:
            scene.set_light(card, light)
            written.append(scene.render(card, os.path.join(out_dir, f"harvest_base_{light}.png")))
        for crop in crops:
            module = render.CROPS[crop]
            for stage in harvest_stages(module):
                render.build(card, crop, stage, True)
                plants = _plants(card, crop, module)
                for f in range(HARVEST_FRAMES):
                    effect = scene.collection(card, EFFECT)
                    for plant in plants:
                        _pose_plant(plant, effect, f)
                    for light in lights:
                        scene.set_light(card, light)
                        path = os.path.join(out_dir, f"harvest_{crop}_{stage}_{light}_{f:02d}.png")
                        written.append(scene.render(card, path))
    finally:
        _done(card)
    return written


# --- Watering ----------------------------------------------------------------------------------------------------

def _can_matrix(f: int) -> Matrix:
    across, depth, height, tilt = _keyed(CAN_PATH, f)
    spout_to = -ACROSS  # the spout points left in the picture, so the can is seen side on
    return (Matrix.Translation(ACROSS * across + DEPTH * depth + UP * height)
            @ Matrix.Rotation(math.atan2(spout_to.y, spout_to.x), 4, "Z")
            @ Matrix.Rotation(math.radians(tilt), 4, "Y") @ Matrix.Scale(CAN_SCALE, 4))


def _can_frame(collection: bpy.types.Collection, f: int) -> None:
    props.watering_can(collection, _can_matrix(f))
    _water(collection, f, _can_drops())


def _can_drops() -> list:
    rng = random.Random(3)
    falling = []
    for f in POUR:
        m = _can_matrix(f)
        rose = m @ props.CAN_ROSE
        spout = (m.to_3x3() @ props.CAN_SPOUT).normalized()
        for _ in range(5):
            jitter = Vector((rng.uniform(-0.2, 0.2), rng.uniform(-0.2, 0.2), rng.uniform(-0.12, 0.12)))
            falling.append(_fall(rose + spout * 0.05, (spout + jitter) * 0.9, f, GRAVITY))
    return falling


def _rain_frame(collection: bpy.types.Collection, f: int) -> None:
    across, depth, height = _keyed(CLOUD_PATH, f)
    props.cloud(collection, Matrix.Translation(ACROSS * across + DEPTH * depth + UP * height)
                @ Matrix.Rotation(math.pi / 4, 4, "Z") @ Matrix.Scale(CLOUD_SCALE, 4))
    _water(collection, f, _rain_drops(), streak=True)


def _rain_drops() -> list:
    rng = random.Random(5)
    inner = style.BED["size"] / 2 - style.BED["plank_depth"]
    falling = []
    for f in RAIN:
        across, depth, height = _keyed(CLOUD_PATH, f)
        for _ in range(10):
            start = (ACROSS * (across + rng.uniform(-0.42, 0.42)) + DEPTH * (depth + rng.uniform(-0.15, 0.15))
                     + UP * (height - 0.12))
            if abs(start.x) < inner and abs(start.y) < inner:
                falling.append(_fall(start, Vector((0.0, 0.0, -2.2)), f, 0.0))
    return falling


def _water(collection, f: int, falling: list, streak: bool = False) -> None:
    """The drops in the air at frame f, and a splash for two frames where each one landed."""
    airborne, rings = [], []
    for path, landing in falling:
        airborne += [p for frame, p in path if frame == f]
        if landing and 0 <= f - landing[0] < 2:
            rings.append((landing[1], float(f - landing[0])))
    if streak:
        props.streaks(collection, "Rain", [(p, p + UP * 0.11) for p in airborne])
    else:
        props.drops(collection, "Drops", airborne, radius=0.021)
    props.splashes(collection, "Splashes", rings)


def _fall(start: Vector, velocity: Vector, spawned: int, gravity: float) -> tuple[list, tuple | None]:
    """Where a drop is at each frame until it lands on the soil, and (frame, point) where it lands."""
    p, v = start.copy(), velocity.copy()
    path = []
    for f in range(spawned, spawned + 4 * FPS):
        path.append((f, p.copy()))
        for _ in range(4):
            v.z -= gravity / FPS / 4
            p += v / FPS / 4
            ground = bed.soil_height(p.x, p.y)
            if p.z <= ground:
                return path, (f + 1, Vector((p.x, p.y, ground)))
    return path, None


# --- Planting ----------------------------------------------------------------------------------------------------

def _spots(card) -> list[tuple[Vector, float]]:
    """Where the seed card in the scene has a mound, and the mound's radius: one spot per plant."""
    card.view_layers[0].update()
    mounds = sorted((o for o in card.collection.children["Bed"].objects if o.name.endswith(".Mound")),
                    key=lambda o: o.name)
    return [(o.matrix_world.translation.copy(), o.dimensions.x / 2) for o in mounds]


def _seeds(spots: list[tuple[Vector, float]]) -> list[tuple[int, int, Vector]]:
    """Each seed as (frame it leaves the packet, the spot it lands on, where it lands). The packet drifts from
    right to left, so the spots fill in that order; with fewer spots than seeds, a spot takes several."""
    rng = random.Random(7)
    order = sorted(range(len(spots)), key=lambda i: -spots[i][0].dot(ACROSS))
    seeds = []
    for k in range(SEED_COUNT):
        spot = order[k * len(order) // SEED_COUNT]
        centre, radius = spots[spot]
        a, r = rng.uniform(0, 2 * math.pi), radius * 0.45 * math.sqrt(rng.random())
        land = centre + Vector((math.cos(a), math.sin(a), 0.0)) * r
        land.z = bed.soil_height(land.x, land.y) + 0.01
        seeds.append((POUR_FROM + round((POUR_TO - POUR_FROM) * k / (SEED_COUNT - 1)), spot, land))
    return seeds


def _packet_matrix(f: int) -> Matrix:
    across, depth, height, tilt = _keyed(PACKET_PATH, f)
    return (Matrix.Translation(ACROSS * across + DEPTH * depth + UP * height)
            @ Matrix.Rotation(math.radians(tilt), 4, TO_CAMERA) @ Matrix.Scale(PACKET_SCALE, 4)
            @ Matrix.Translation(-PACKET_MIDDLE))


def _plant_frame(collection: bpy.types.Collection, crop: str, f: int, spots: list, seeds: list) -> None:
    stills.packet(collection, crop)
    for obj in collection.all_objects:
        for mod in obj.modifiers:
            if mod.type == "SOLIDIFY":
                mod.thickness /= PACKET_SCALE  # ink as wide as the bed's, not shrunk with the packet
    scene.group(collection, "Packet").matrix_world = _packet_matrix(f)
    falling = []
    for spawn, _, land in seeds:
        t = (f - spawn) / SEED_FALL
        if 0.0 <= t < 1.0:
            start = _packet_matrix(spawn) @ PACKET_MOUTH
            here = start.lerp(land, t)
            here.z = start.z + (land.z - start.z) * t * t  # falls, gathering speed
            falling.append(here)
    props.seeds(collection, "Seeds", falling)
    sparkles = []
    for i, (centre, radius) in enumerate(spots):
        landed = max(spawn + SEED_FALL for spawn, spot, _ in seeds if spot == i)
        if f - landed in SEED_SPARKLE:
            size = SEED_SPARKLE[f - landed] * (0.07 + 0.5 * radius)
            sparkles += [(centre + UP * 0.12, size), (centre + UP * 0.2 - ACROSS * 0.06, size * 0.6)]
    props.sparkle(collection, "Sparkle", sparkles)


# --- Harvest -----------------------------------------------------------------------------------------------------

class _Plant:
    """One plant of a bed. What flies is parented to `handle`, an empty at the plant's foot that each frame poses;
    the mound and what stays behind keep their resting places so they can be scaled from scratch each frame."""

    def __init__(self, handle: bpy.types.Object, size: float, delay: int, hand: int):
        self.handle, self.pivot = handle, handle.location.copy()
        self.size, self.delay, self.hand = size, delay, hand
        self.mound, self.left = [], []


def _plants(card, crop: str, module) -> list[_Plant]:
    """Group the bed's crop objects by plant: Radish.3.Bulb belongs to Radish.3, Pumpkin.Body to Pumpkin."""
    prefix = crop.capitalize() + "."
    left_behind = getattr(module, "LEFT_BEHIND", ())
    card.view_layers[0].update()  # so matrix_world includes the locations the crop builders just set
    groups: dict[str, list] = {}
    for obj in card.collection.children["Bed"].objects:
        if obj.name.startswith(prefix):
            tokens = obj.name.split(".")
            key = ".".join(tokens[:2]) if tokens[1].isdigit() else tokens[0]
            groups.setdefault(key, []).append(obj)
    order = list(groups)
    random.Random(len(order)).shuffle(order)
    plants = []
    for key, objects in groups.items():
        mound = next(o for o in objects if o.name.endswith(".Mound"))
        handle = bpy.data.objects.new(f"{key}.Handle", None)
        handle.location = mound.location
        card.collection.children["Bed"].objects.link(handle)
        rank = order.index(key)
        plant = _Plant(handle, mound.dimensions.x / 2, round(STAGGER * rank / max(1, len(order) - 1)),
                       1 if rank % 2 else -1)
        for obj in objects:
            part = obj.name[len(key) + 1:]
            if obj is mound or part in left_behind:
                (plant.mound if obj is mound else plant.left).append((obj, obj.matrix_world.copy()))
            else:
                obj.parent = handle
                obj.matrix_parent_inverse = Matrix.Translation(-plant.pivot)
        plants.append(plant)
    return plants


def _pose_plant(plant: _Plant, effect: bpy.types.Collection, f: int) -> None:
    t = f - plant.delay
    around = Matrix.Translation(plant.pivot)
    back = Matrix.Translation(-plant.pivot)
    if t < 0:
        plant.handle.matrix_world = around
    else:
        lift = LIFT[min(t, len(LIFT) - 1)]
        across, up = SQUASH[t] if t < len(SQUASH) else (1.0, 1.0)
        lean = math.radians(LEAN[min(t, len(LEAN) - 1)]) * plant.hand
        drift = ACROSS * (0.07 * lift * plant.hand)
        plant.handle.matrix_world = (Matrix.Translation(UP * lift + drift) @ around
                                     @ Matrix.Rotation(lean, 4, TO_CAMERA) @ Matrix.Diagonal((across, across, up, 1.0)))
    height = MOUND[t] if 0 <= t < len(MOUND) else (1.0 if t < 0 else 0.0)
    shrink = SHRINK[t] if 0 <= t < len(SHRINK) else (1.0 if t < 0 else 0.0)
    for objects, scale in ((plant.mound, Matrix.Diagonal((1.0, 1.0, height, 1.0))),
                           (plant.left, Matrix.Diagonal((shrink, shrink, shrink, 1.0)))):
        for obj, rest in objects:
            obj.hide_render = scale[2][2] <= 0.0
            obj.matrix_world = around @ scale @ back @ rest
    props.puff(effect, "Puff", plant.pivot, plant.size, (t - 2) / 4, "pebble_wet")
    if t in SPARKLE:
        size = SPARKLE[t] * (0.07 + 0.5 * plant.size)
        props.sparkle(effect, "Sparkle", [(plant.pivot + UP * 0.12 + ACROSS * (0.05 * plant.hand), size),
                                          (plant.pivot + UP * 0.22 - ACROSS * (0.06 * plant.hand), size * 0.6)])


# --- Shared ------------------------------------------------------------------------------------------------------

def _keyed(keys: list[tuple], f: int) -> tuple:
    """The values at frame f between (frame, values...) keys, eased in and out between each pair."""
    for (f0, *a), (f1, *b) in zip(keys, keys[1:]):
        if f0 <= f <= f1:
            t = (f - f0) / (f1 - f0)
            t = t * t * (3 - 2 * t)
            return tuple(x + (y - x) * t for x, y in zip(a, b))
    return tuple(keys[-1][1:])


def _clear(out_dir: str, prefix: str, lights: list[str]) -> None:
    """Delete earlier frames this render replaces (those of [lights] only), so none are left over if the frame
    counts change and the other lights' frames stay for the review sheets."""
    os.makedirs(out_dir, exist_ok=True)
    for name in os.listdir(out_dir):
        if name.startswith(prefix) and name.endswith(".png") and any(f"_{light}_" in name for light in lights):
            os.remove(os.path.join(out_dir, name))


def _card() -> bpy.types.Scene:
    card = scene.card_scene()
    card.render.resolution_percentage = round(100 * SIZE / style.CAMERA["resolution"])
    toon.reset()
    return card


def _done(card: bpy.types.Scene) -> None:
    """Remove the effect objects and put the card back to full size."""
    scene.remove(EFFECT)
    card.render.resolution_percentage = 100
