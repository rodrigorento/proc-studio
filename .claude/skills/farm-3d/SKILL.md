---
name: farm-3d
description: Make or change Task Farm's farm art, the pre-rendered toon beds, crops, animations, props, home field and app icon, through the art/ pipeline (concept images, Blender models in art/farmkit, renders, Android export, review). Use when adding a crop, stage, light, animation or prop, retouching one, or re-rendering and exporting the art.
compatibility: Needs Blender 5.2 with Blender Lab's MCP add-on and the `blender` MCP server, Python 3 with Pillow and PyYAML, and for concept images the imagegen skill with OPENAI_API_KEY.
---

# Farm 3D art

The farm is pre-rendered: Blender builds every bed, crop, animation and prop from Python (`art/farmkit`), renders them
with a cel-shaded toon look, and `art/export_android.py` turns the renders into WebPs plus the Kotlin that looks them
up (`CropArt.kt`, `CropMotion.kt`). Nothing is modelled by hand, so every change is a code change you can re-render.

`art/style/style.yaml` holds the art direction, `art/assets.yaml` lists every asset with its sources, outputs and
status, and `art/farmkit/style.py` holds the numbers (palette, bed, camera, lights).

## Setup

- Blender 5.2 running with Blender Lab's MCP add-on (from `https://lab.blender.org/`), connected on port 9876, and
  the `blender` MCP server in Claude Code. Siddharth Ahuja's "MCP for Blender" add-on uses the same port with another
  protocol: keep it disabled.
- In each Blender session, before any farmkit call:

  ```python
  import sys, importlib
  sys.path.insert(0, "<repo>/art")
  import farmkit; importlib.reload(farmkit); farmkit.reload()   # after every edit to farmkit
  ```

- `execute_blender_code` must set `result` to a dict. Calls longer than about 120 s move to the background; render
  in batches (a few crops per call).

## Pipeline for a new or changed asset

1. **Concept.** With the imagegen skill, make the concept as an *edit* of the approved reference in
   `art/style/references/`, so it keeps the bed, angle, outlines and light. For a crop: a wet stage strip, then the
   dry strip as an edit of the wet one (the model miscounts plants otherwise), and a turnaround. For a design
   choice, make 3-4 options, put them on a sheet, and ask. Save concepts under `art/concepts/<asset>/` with their
   sidecar JSON.
2. **Model.** A crop is `art/farmkit/crops/<crop>.py` with:
   - `STAGES = {"seed": {"growth": 0.0}, ...}` in exactly that layout (the export reads it with a regex),
   - `build(collection, stage, wet)`, which adds the plants on the bed's soil (`bed.soil_height`),
   - the shared pieces in `crops/parts.py` (`leaf`, `palmate`, `tube`, `lathe`, `mound`, `sprout`, `flat`),
   - materials by palette name (`toon.material("leaf")`); a new colour goes in `style.PALETTE` and, for crops, in
     `style.TINT_STRENGTH` so it keeps its colour at night.
   Register it in `farmkit/__init__.py` (`_MODULES`) and `farmkit/render.py` (`CROPS`). Props go in
   `farmkit/stills.py`, the home field in `farmkit/field.py`; animation timing in `farmkit/anim.py`.
3. **Look.** Render a few states in day light into the scratchpad and compare with the concept; then
   `python3 art/review.py stages <crop>` and `lights <crop>` on the full renders. `farmkit.showroom` lays every crop
   and prop out in one scene (`build()`, `render(path)`, `save("art/blend/showroom.blend")`).
4. **Render.**
   - cards: `render.render_all("<repo>/art/build/renders")` (all crops, about 2 min)
   - animations: `anim.render_water("<repo>/art/build/anim")`, `anim.render_plant(..., crops=[...])` and
     `anim.render_harvest(..., crops=[...])`
   - props and icon: `stills.render_all("<repo>/art/build/props")`
   - the home field (lawn, marked-out plot, bed shadow, field.json): `field.render_all("<repo>/art/build/field")`
5. **Export.** `python3 -I art/export_android.py`. It checks every image the app can ask for exists, keeps harvest
   frames to what differs from the empty bed, writes the launcher icon at every density, and prints the size
   against the budget.
6. **Check.**
   - `python3 art/review.py check`: `art/assets.yaml` matches the files. Add or update the asset's entry.
   - `bash gradlew testDebugUnitTest installDebug`, then open the app, long-press the "Task Farm" title and use
     the farm art preview (watered, Water / Rain / Harvest / Plant) on a phone or emulator.
   - `git status`: only the asset you changed should differ. A single unrelated WebP changing is render noise:
     `git checkout` it.
7. **Commit** the farmkit code, concepts, WebPs and generated Kotlin together. PNG and .blend files under `art/` go
   through Git LFS. Re-save `art/blend/farm.blend` when the scene setup changes.

## Rules the art keeps

- Cel-shaded toon, three bands, warm dark-brown ink (`#251803`), strict isometric orthographic camera, 640 px cards.
- Nothing underground shows, except roots pulled out in a harvest.
- The smaller the crop, the more per bed: radish 9, lettuce 4, carrot 2, pumpkin 1, sunflower 1.
- Stages: seed, sprout, growing, ripe, overripe (gone to seed); the sunflower stops at bloom. Every stage has a
  watered and a dry version: dry is pale cracked soil and drooping olive leaves, thirsty but never dead.
- The app shows everything in the day light (`LIGHT` in `art/export_android.py`). farmkit still renders the four
  lights in `style.LIGHTS` (morning, day, dusk, night) for the showroom and review sheets, so a render call can pass
  `lights=["day"]` when only the app's art is needed.
- App file names (no light in them): `crop_<crop>_<stage>_<wet|dry>`, `bed_empty_<wet|dry>`, `anim_water_<can|rain>_<nn>`,
  `anim_plant_<crop>_<nn>`, `anim_harvest_<crop>_<stage>_<nn>`, `prop_<name>`. Renders keep the light:
  `crop_<crop>_<stage>_<wet|dry>_<light>.png` and so on.

## Gotchas

- Run Blender calls only after file edits have finished; a parallel call can run the old code.
- Placing or measuring objects in a scene that isn't the window's scene reads stale matrices: set
  `bpy.context.window.scene` first (the card scene functions do), and `view_layer.update()` before reading
  `matrix_world`.
- Squashing something that has rotated parts: parent the parts to an empty and scale the empty
  (`scene.group`); a non-uniform scale written into `matrix_world` gets decomposed and loses the shear.
- Ink outlines are inverted hulls; they must not cast shadows (`use_backface_culling_shadow`), and effects such as
  sparkles shouldn't either (`visible_shadow = False`).
- Identical scenes can render a few levels apart: compare renders with a tolerance.
- Re-rendering an animation clears its old frames first, and the export insists on exactly the frame counts in
  `anim.py`, so a stale or missing frame stops the export rather than shifting the timing.
