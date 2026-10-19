"""Blender scripts that build and render the farm's crop beds.

Run inside Blender (through the Blender MCP server or `blender --python`), with art/ on sys.path:

    import sys; sys.path.insert(0, "<repo>/art")
    import farmkit; farmkit.reload()
"""
import importlib

_MODULES = ("style", "toon", "scene", "bed", "crops.parts", "crops.carrot", "crops.radish", "crops.lettuce", "crops.pumpkin", "crops.sunflower", "render", "props", "anim", "stills", "showroom", "field")


def reload() -> None:
    """Pick up edits to the scripts without restarting Blender, including edits to this list."""
    package = importlib.reload(importlib.import_module(__name__))
    for name in package._MODULES:
        importlib.reload(importlib.import_module(f"{__name__}.{name}"))
