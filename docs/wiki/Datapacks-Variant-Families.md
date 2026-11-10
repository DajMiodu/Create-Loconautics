# Variant Families

Folder: `data/<namespace>/variant_families/`

A variant family is one scroll menu. When a player holds a block that is in a family and opens the picker, they see every block of that family and can switch the held block to any of them.

## Simple form

A list of block ids. Each id becomes one menu entry, named after the block.

```json
{
  "blocks": [
    "loconautics:white_dashboard",
    "loconautics:orange_dashboard",
    "loconautics:magenta_dashboard"
  ]
}
```

This is all most families need. Loconautics uses it for dashboards, analog controllers, the hand brake and Create postboxes.

## Full form

Use `types` when one menu entry has several colors, or a wrapped material.

```json
{
  "types": [
    {
      "name": "block.hullsteel.boiler",
      "colors": {
        "colorless": "hullsteel:boiler/boiler",
        "black": "hullsteel:boiler/black_boiler",
        "light_blue": "hullsteel:boiler/light_blue_boiler"
      }
    }
  ]
}
```

| Key | Meaning |
| --- | --- |
| `name` | Translation key. Only used for display. |
| `colors` | Dye name (`white`, `light_blue`, ...) or `colorless` mapped to a block id. `colorless` is the undyed block, not a color. |
| `block` | Shortcut for a type that only has the colorless block. |
| `materials` | List of wrapping material names, for blocks with a wrapped version. |
| `wrapped` | For each color, a map of material name to block id. Needs `materials`. |

`blocks` and `types` can be in the same file.

## Rules

- A menu holds at most 16 entries. A longer list is split into several menus.
- A block id that is not registered is skipped, and the load log says which one. The rest of the family still works.
- An entry whose blocks are all missing is dropped.
- A block can only be in one family. If two files list it, the first one loaded wins.
- Holding a block that is in no family does nothing.

## Adding your own

1. Make `data/yourpack/variant_families/my_family.json` with a `blocks` list.
2. Run `/reload`.
3. Hold one of the blocks and open the picker. Check the log for `[VariantPicker] loaded N datapack families`.
