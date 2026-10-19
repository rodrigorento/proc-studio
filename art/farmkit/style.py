"""Numbers the Blender scripts share: palette, outline, camera and lights.

The art direction behind them is in art/style/style.yaml; keep the two in step.
Colours are sRGB hex sampled from the approved concept images, each (lit, base, shade).
"""

PALETTE = {
    "wood": ("#E08A35", "#BF6A24", "#84420F"),
    "rivet": ("#D6D5D2", "#747574", "#38312A"),
    "soil_wet": ("#8E4418", "#6E3210", "#4A1E07"),
    "soil_dry": ("#C37839", "#9F5A26", "#703A15"),
    "pebble_wet": ("#B5652A", "#8E4A1A", "#5A2A0C"),
    "pebble_dry": ("#D9955A", "#B57640", "#7E4A22"),
    "leaf": ("#A6DC1E", "#7FC00F", "#44820C"),
    "leaf_dry": ("#B4C24A", "#93A12E", "#5B6A18"),
    "leaf_old": ("#DAE008", "#B8B20A", "#6E7404"),
    "carrot": ("#FCB235", "#FA6E07", "#B73908"),
    "flower": ("#F1F0D9", "#D8D6B4", "#9E9C78"),
}

OUTLINE = "#251803"  # warm dark brown ink
OUTLINE_WIDTH = 0.018  # metres; the bed is 1 m wide
LEAF_OUTLINE_WIDTH = 0.007  # thin parts (leaves, stems, flowers)

# Direction from the scene towards the isometric camera: leaves are turned to face it so they read.
TO_CAMERA = (0.5774, -0.5774, 0.5774)

# Toon bands: lighting below SHADE_BELOW is the shade colour, above LIT_ABOVE the lit colour, base between.
SHADE_BELOW = 0.22
LIT_ABOVE = 0.62

BED = {
    "size": 1.0,  # outer width, metres
    "plank_height": 0.155,
    "plank_depth": 0.11,
    "plank_round": 0.05,
    "post_size": 0.19,
    "post_height": 0.37,
    "post_round": 0.08,
    "post_out": 0.035,  # how far the posts stick out past the planks
    "soil_top": 0.2,  # soil surface height at the bottom of the furrows
    "ridges": 3,
    "ridge_height": 0.085,
}

CAMERA = {
    "resolution": 640,
    "ortho_scale": 1.6,
    "target": (0.0, 0.0, 0.36),  # framed a little above the bed, leaving room for tall crops
}

# `hours` is when the app shows each light: from the first hour, up to but not including the second.
# Where each light comes from: elevation above the ground and azimuth in degrees (0 = +X, 90 = +Y).
# The camera looks from +X/-Y, so 270 lights the left face of the bed and 0-45 comes from the right.
# Colours are first desaturated to `saturation`, then key_tint multiplies the lit and base bands and
# shadow_tint the shade band, then `lift` mixes towards white.
LIGHTS = {
    "morning": {
        "hours": (5, 11),
        "elevation": 18, "azimuth": 215, "ambient": 0.14,
        "key_tint": "#FFE3CC", "shadow_tint": "#B4AEE0", "saturation": 0.95, "lift": 0.04,
    },
    "day": {
        "hours": (11, 17),
        "elevation": 48, "azimuth": 228, "ambient": 0.12,
        "key_tint": "#FFFFFF", "shadow_tint": "#FFFFFF", "saturation": 1.0, "lift": 0.0,
    },
    "dusk": {
        "hours": (17, 20),
        "elevation": 14, "azimuth": 20, "ambient": 0.12,
        "key_tint": "#FFC58C", "shadow_tint": "#C98FC0", "saturation": 1.0, "lift": 0.0,
    },
    "night": {
        "hours": (20, 5),
        "elevation": 55, "azimuth": 240, "ambient": 0.16,
        "key_tint": "#7C8CD2", "shadow_tint": "#3C3D7A", "saturation": 0.3, "lift": 0.0,
    },
}
