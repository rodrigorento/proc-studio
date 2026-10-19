"""Cel shading and ink outlines.

Every material is the FarmToon node group: the lighting is reduced to three bands (shade, base, lit),
each band gets a flat palette colour, then a time-of-day tint from the FarmTime group. Changing FarmTime's
two colours relights every material at once. Outlines are an inverted hull: a Solidify modifier that
pushes a back-facing copy of the mesh outwards in the ink material.
"""
import bpy

from . import style

TOON_GROUP = "FarmToon"
TIME_GROUP = "FarmTime"
OUTLINE_MATERIAL = "Ink"


def linear(hex_color: str) -> tuple[float, float, float, float]:
    """sRGB hex to the scene-linear RGBA that Blender's colour sockets expect."""
    def channel(value: int) -> float:
        c = value / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    h = hex_color.lstrip("#")
    return (channel(int(h[0:2], 16)), channel(int(h[2:4], 16)), channel(int(h[4:6], 16)), 1.0)


def material(name: str) -> bpy.types.Material:
    """The toon material for a palette entry, created on first use."""
    existing = bpy.data.materials.get(name)
    if existing:
        return existing
    lit, base, shade = style.PALETTE[name]
    mat = bpy.data.materials.new(name)
    nodes, links = _node_tree(mat)
    group = nodes.new("ShaderNodeGroup")
    group.node_tree = _toon_group()
    group.inputs["Lit"].default_value = linear(lit)
    group.inputs["Base"].default_value = linear(base)
    group.inputs["Shade"].default_value = linear(shade)
    group.inputs["Tint"].default_value = style.TINT_STRENGTH.get(name, 1.0)
    output = nodes.new("ShaderNodeOutputMaterial")
    links.new(group.outputs["Shader"], output.inputs["Surface"])
    return mat


def outline(obj: bpy.types.Object, width: float = style.OUTLINE_WIDTH) -> None:
    """Add the ink outline to a mesh object. Call after its other modifiers."""
    ink = _ink_material()
    if ink.name not in obj.data.materials:
        obj.data.materials.append(ink)
    hull = obj.modifiers.new("Outline", "SOLIDIFY")
    hull.thickness = -width
    hull.offset = 1.0
    hull.use_flip_normals = True
    hull.use_rim = False
    hull.material_offset = list(obj.data.materials).index(ink)


def set_time(light: dict) -> None:
    """Relight every toon material with one of style.LIGHTS."""
    group = _time_group()
    group.nodes["Key"].outputs[0].default_value = linear(light["key_tint"])
    group.nodes["Shadow"].outputs[0].default_value = linear(light["shadow_tint"])
    group.nodes["Saturation"].outputs[0].default_value = light["saturation"]
    group.nodes["Lift"].outputs[0].default_value = light["lift"]


def reset() -> None:
    """Forget the shader groups and materials so the next build makes them from the current code."""
    for name in list(style.PALETTE) + [OUTLINE_MATERIAL]:
        if name in bpy.data.materials:
            bpy.data.materials.remove(bpy.data.materials[name])
    for name in (TOON_GROUP, TIME_GROUP):
        if name in bpy.data.node_groups:
            bpy.data.node_groups.remove(bpy.data.node_groups[name])


def _node_tree(mat: bpy.types.Material):
    if hasattr(mat, "use_nodes"):
        mat.use_nodes = True
    mat.node_tree.nodes.clear()
    return mat.node_tree.nodes, mat.node_tree.links


def _ink_material() -> bpy.types.Material:
    ink = bpy.data.materials.get(OUTLINE_MATERIAL)
    if ink:
        return ink
    ink = bpy.data.materials.new(OUTLINE_MATERIAL)
    ink.use_backface_culling = True
    # The hull wraps its object; without this it would shadow the object it outlines.
    ink.use_backface_culling_shadow = True
    nodes, links = _node_tree(ink)
    emit = nodes.new("ShaderNodeEmission")
    emit.inputs["Color"].default_value = linear(style.OUTLINE)
    output = nodes.new("ShaderNodeOutputMaterial")
    links.new(emit.outputs["Emission"], output.inputs["Surface"])
    return ink


def _time_group() -> bpy.types.ShaderNodeTree:
    group = bpy.data.node_groups.get(TIME_GROUP)
    if group:
        return group
    group = bpy.data.node_groups.new(TIME_GROUP, "ShaderNodeTree")
    out = group.nodes.new("NodeGroupOutput")
    for name in ("Key", "Shadow"):
        group.interface.new_socket(name, in_out="OUTPUT", socket_type="NodeSocketColor")
        rgb = group.nodes.new("ShaderNodeRGB")
        rgb.name = name
        rgb.outputs[0].default_value = (1.0, 1.0, 1.0, 1.0)
        group.links.new(rgb.outputs[0], out.inputs[name])
    for name, default in (("Saturation", 1.0), ("Lift", 0.0)):
        group.interface.new_socket(name, in_out="OUTPUT", socket_type="NodeSocketFloat")
        value = group.nodes.new("ShaderNodeValue")
        value.name = name
        value.outputs[0].default_value = default
        group.links.new(value.outputs[0], out.inputs[name])
    return group


def _toon_group() -> bpy.types.ShaderNodeTree:
    group = bpy.data.node_groups.get(TOON_GROUP)
    if group:
        return group
    group = bpy.data.node_groups.new(TOON_GROUP, "ShaderNodeTree")
    for name in ("Lit", "Base", "Shade"):
        group.interface.new_socket(name, in_out="INPUT", socket_type="NodeSocketColor")
    group.interface.new_socket("Tint", in_out="INPUT", socket_type="NodeSocketFloat")
    group.interface.new_socket("Shader", in_out="OUTPUT", socket_type="NodeSocketShader")
    nodes, links = group.nodes, group.links
    inputs = nodes.new("NodeGroupInput")
    outputs = nodes.new("NodeGroupOutput")

    # How lit the surface is, shadows included, as one value.
    diffuse = nodes.new("ShaderNodeBsdfDiffuse")
    to_rgb = nodes.new("ShaderNodeShaderToRGB")
    value = nodes.new("ShaderNodeRGBToBW")
    links.new(diffuse.outputs["BSDF"], to_rgb.inputs["Shader"])
    links.new(to_rgb.outputs["Color"], value.inputs["Color"])

    above_shade = _compare(group, value.outputs["Val"], style.SHADE_BELOW)
    above_base = _compare(group, value.outputs["Val"], style.LIT_ABOVE)

    shade_or_base = _mix(group, above_shade, inputs.outputs["Shade"], inputs.outputs["Base"])
    banded = _mix(group, above_base, shade_or_base, inputs.outputs["Lit"])

    # Time of day: desaturate, tint (lit and base bands by the key, shade by the shadow tint), lift.
    time = nodes.new("ShaderNodeGroup")
    time.node_tree = _time_group()
    saturated = nodes.new("ShaderNodeHueSaturation")
    links.new(banded, saturated.inputs["Color"])
    links.new(time.outputs["Saturation"], saturated.inputs["Saturation"])
    tint = _mix(group, above_shade, time.outputs["Shadow"], time.outputs["Key"])
    tinted = nodes.new("ShaderNodeMix")
    tinted.data_type = "RGBA"
    tinted.blend_type = "MULTIPLY"
    _socket(tinted.inputs, "Factor_Float").default_value = 1.0
    links.new(saturated.outputs["Color"], _socket(tinted.inputs, "A_Color"))
    links.new(tint, _socket(tinted.inputs, "B_Color"))
    lifted = nodes.new("ShaderNodeMix")
    lifted.data_type = "RGBA"
    links.new(time.outputs["Lift"], _socket(lifted.inputs, "Factor_Float"))
    links.new(_socket(tinted.outputs, "Result_Color"), _socket(lifted.inputs, "A_Color"))
    _socket(lifted.inputs, "B_Color").default_value = (1.0, 1.0, 1.0, 1.0)

    # Materials with a Tint below 1 (the crops) keep more of their colour: they only darken by as much as the
    # tint does, then take the rest of the tint in proportion.
    darkness = nodes.new("ShaderNodeRGBToBW")
    links.new(tint, darkness.inputs["Color"])
    gray = nodes.new("ShaderNodeCombineColor")
    for channel in ("Red", "Green", "Blue"):
        links.new(darkness.outputs["Val"], gray.inputs[channel])
    darkened = nodes.new("ShaderNodeMix")
    darkened.data_type = "RGBA"
    darkened.blend_type = "MULTIPLY"
    _socket(darkened.inputs, "Factor_Float").default_value = 1.0
    links.new(banded, _socket(darkened.inputs, "A_Color"))
    links.new(gray.outputs["Color"], _socket(darkened.inputs, "B_Color"))
    final = _mix(group, inputs.outputs["Tint"], _socket(darkened.outputs, "Result_Color"),
                 _socket(lifted.outputs, "Result_Color"))

    emit = nodes.new("ShaderNodeEmission")
    links.new(final, emit.inputs["Color"])
    links.new(emit.outputs["Emission"], outputs.inputs["Shader"])
    return group


def _compare(group, value_socket, threshold: float):
    node = group.nodes.new("ShaderNodeMath")
    node.operation = "GREATER_THAN"
    group.links.new(value_socket, node.inputs[0])
    node.inputs[1].default_value = threshold
    return node.outputs["Value"]


def _mix(group, factor, a, b):
    node = group.nodes.new("ShaderNodeMix")
    node.data_type = "RGBA"
    group.links.new(factor, _socket(node.inputs, "Factor_Float"))
    group.links.new(a, _socket(node.inputs, "A_Color"))
    group.links.new(b, _socket(node.inputs, "B_Color"))
    return _socket(node.outputs, "Result_Color")


def _socket(sockets, identifier: str):
    return next(s for s in sockets if s.identifier == identifier)
