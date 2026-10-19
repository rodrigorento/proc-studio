"""Render every card the app needs: each crop's stages, watered and dry, in each light, plus the empty bed.

File names are what the Android export uses: crop_<crop>_<stage>_<wet|dry>_<light>.png and
bed_empty_<wet|dry>_<light>.png.
"""
import os

from . import bed, scene, style, toon
from .crops import carrot, lettuce, pumpkin, radish

CROPS = {"radish": radish, "lettuce": lettuce, "carrot": carrot, "pumpkin": pumpkin}


def card_name(crop: str, stage: str, wet: bool, light: str) -> str:
    return f"crop_{crop}_{stage}_{_wetness(wet)}_{light}"


def empty_name(wet: bool, light: str) -> str:
    return f"bed_empty_{_wetness(wet)}_{light}"


def render_all(out_dir: str, crops: list[str] | None = None, lights: list[str] | None = None) -> list[str]:
    """Build each state once and render it in every light. Returns the written paths."""
    os.makedirs(out_dir, exist_ok=True)
    card = scene.card_scene()
    toon.reset()
    lights = lights or list(style.LIGHTS)
    written = []
    for wet in (True, False):
        _build(card, None, None, wet)
        written += _render_lights(card, out_dir, lambda light: empty_name(wet, light), lights)
        for crop in crops or list(CROPS):
            for stage in CROPS[crop].STAGES:
                _build(card, crop, stage, wet)
                written += _render_lights(card, out_dir, lambda light: card_name(crop, stage, wet, light), lights)
    return written


def _build(card, crop: str | None, stage: str | None, wet: bool) -> None:
    collection = scene.collection(card, "Bed")
    bed.build(collection, wet=wet)
    if crop:
        CROPS[crop].build(collection, stage, wet=wet)


def _render_lights(card, out_dir: str, name, lights: list[str]) -> list[str]:
    paths = []
    for light in lights:
        scene.set_light(card, light)
        paths.append(scene.render(card, os.path.join(out_dir, name(light) + ".png")))
    return paths


def _wetness(wet: bool) -> str:
    return "wet" if wet else "dry"
