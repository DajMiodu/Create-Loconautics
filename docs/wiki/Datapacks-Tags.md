# Tags

Folder: `data/<namespace>/tags/block/` and `data/<namespace>/tags/item/`

Tags use the normal Minecraft format. Loconautics reads a few of its own to decide what counts as what.

```json
{
  "replace": false,
  "values": [
    "yourmod:some_block",
    { "id": "othermod:optional_block", "required": false }
  ]
}
```

Keep `replace` false so you add to the list instead of wiping it.

## Other tags

| Tag | Meaning |
| --- | --- |
| `loconautics:bogeys` | Bogey blocks. The mod uses this tag to set their mass, see Bogey mass below. |
| `loconautics:dashboard` | All dashboard colors. |
| `c:dyed/<color>` (block and item) | Puts the Loconautics dyed blocks in the shared dye color tags that other mods read. |

## Tags from other mods and vanilla

Loconautics adds its blocks and items to these tags:

- `minecraft:mineable/pickaxe` and `minecraft:mineable/axe`: which tool breaks Loconautics blocks fast.
- `create:copycat_allow`: blocks Create copycats may copy. Empty today.
- `curios:head`: the conductor's hats, so they fit the Curios head slot.

## Bogey mass

File: `data/sable/physics_block_properties/bogey.json`

This folder is read by the Sable physics mod, not by Loconautics. Loconautics includes one file in it, which sets the mass of every block in the `loconautics:bogeys` tag.

```json
{
  "selector": "#loconautics:bogeys",
  "properties": {
    "sable:mass": 12.0
  }
}
```

- `selector` is a block id or a `#tag`. Every matching block gets the properties.
- `properties` sets physics values. Here it sets the mass of every bogey to 12.0.

To change the mass of a modded bogey, add it to the `loconautics:bogeys` tag. Mass uses Sable's own units, so assemble a train and drive it to test a change.
