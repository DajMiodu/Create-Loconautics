# Boiler Blocks

Folder: `data/<namespace>/boiler_blocks/`

A boiler is a firebox, a run of body blocks and a smokestack on top. Each block adds something to the boiler. Blocks with no file here use the defaults. Use this folder to make a block count for more or less.

## Format

One block per file, or many with `entries`.

```json
{
  "entries": [
    {
      "block": "hullsteel:boiler/big_boiler",
      "boiler_capacity": 2.0,
      "water_capacity": 16000
    }
  ]
}
```

| Key | Meaning | Default |
| --- | --- | --- |
| `block` | Block id. If the block does not exist the entry is skipped quietly, so entries for optional mods are safe. | required |
| `firebox_heat` | Heat one firebox block adds. The boiler adds this up over all its fireboxes. | 1.0 |
| `boiler_capacity` | Power size of a body block. The boiler adds this up over its body. It sets steam output, how hard the steam takeoff can push, and the firebox burn rate. | 1.0 |
| `water_capacity` | Water in mB that one body block holds. | the `BODY_WATER_CAPACITY` config value |

## Capacity versus water

These are two separate numbers.

- `boiler_capacity` is about power. More of it means more steam.
- `water_capacity` is about storage. More of it means a bigger water tank.

A block can raise one and not the other.

## The block must also be a boiler part

This folder only changes the numbers. The block still has to count as a boiler part through a tag, see [Tags](Datapacks-Tags). For a body block, add it to `loconautics:boiler_body`.

## Example

Make a modded barrel count as a double size body that holds 8000 mB.

First add it to the body tag, in `data/loconautics/tags/block/boiler_body.json` inside your pack:

```json
{ "replace": false, "values": [ "yourmod:big_barrel" ] }
```

Then `data/yourpack/boiler_blocks/big_barrel.json`:

```json
{ "block": "yourmod:big_barrel", "boiler_capacity": 2.0, "water_capacity": 8000 }
```

On load the log shows `[Boiler] loaded N firebox, N body and N water contribution overrides`.
