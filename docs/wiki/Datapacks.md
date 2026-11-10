# Loconautics Datapacks

Loconautics reads several folders from datapacks. You can change how boilers, hoses and the block picker behave without editing the mod, and other mods can add their own blocks to them.

## Pages

| Page | What it controls |
| --- | --- |
| [Variant Families](Datapacks-Variant-Families) | The scroll menu that switches a held block between colors or styles |
| [Boiler Blocks](Datapacks-Boiler-Blocks) | How much heat, steam power and water a block adds to a boiler |
| [Hose Spray](Datapacks-Hose-Spray) | What each fluid does when sprayed from the hose nozzle, and its color |
| [Tags](Datapacks-Tags) | Which blocks count as firebox, boiler body, smokestack, hatch or bogey, and the bogey mass |
| [Troubleshooting](Datapacks-Troubleshooting) | Log lines and common mistakes |

## Basics

- Files go in `data/<namespace>/<folder>/` inside a datapack.
- Use `/reload` in game to load changes. Variant families and hose colors are sent to every player again after a reload.
- Files from different packs add together. They do not replace each other.
- A bad file is logged and skipped. The rest still loads.
- Blocks from optional mods are safe to list in tags. Write them as `{ "id": "othermod:block", "required": false }` and they are ignored when that mod is missing.
