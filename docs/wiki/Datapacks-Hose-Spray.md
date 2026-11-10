# Hose Spray

Folder: `data/<namespace>/hose_spray/`

Controls what a fluid does when it is sprayed from the hose nozzle, and what color the spray is.

## Built in behavior

These always work, with no files:

- Water puts out fire and soaks farmland.
- Lava sets blocks and entities on fire.
- Potion fluids apply their effect.
- Create honey slows entities.

Files in this folder add to that, or change it for one fluid.

## Format

One binding per file, or many with `bindings`.

```json
{ "fluid": "minecraft:water", "effect": "douse" }
```

```json
{
  "bindings": [
    { "fluid": "minecraft:water", "effect": "douse" },
    { "fluid": "aeronautics:levitite_blend", "mob_effect": "minecraft:levitation", "duration": 100 }
  ]
}
```

## Keys

| Key | Meaning |
| --- | --- |
| `fluid` or `fluidTag` | Pick one. A single fluid id, or a fluid tag. An unknown fluid is skipped and logged. |
| `effect` | Reuse a built in behavior: `douse` (puts out fire, soaks farmland) or `ignite` (sets fire, burns entities). |
| `mob_effect` | Effect id given to entities hit by the spray. |
| `duration` | Effect length in ticks. Default 200. |
| `amplifier` | Effect level, 0 is level one. Default 0. |
| `damage` | Damage dealt to entities each hit. |
| `ignite` | Seconds an entity is set on fire. |
| `match` | List of entity ids or `#entity_tags` this applies to. Left out means every entity. |
| `entities` | Per entity rules, see below. |
| `colors` | List of hex colors like `"#33AAFF"` for the spray. Up to 4 are used, a shorter list repeats. Works with `fluid` only, not `fluidTag`. |

`mob_effect`, `damage` and `ignite` written at the top level are one rule that applies to every entity.

## Per entity rules

```json
{
  "fluid": "minecraft:water",
  "effect": "douse",
  "entities": {
    "rules": [
      { "match": ["minecraft:blaze"], "damage": 3.0 }
    ],
    "blacklist": ["minecraft:villager"]
  }
}
```

- `rules` is a list. Each rule has `match` and one of `mob_effect`, `damage` or `ignite`.
- `blacklist` is a list of entity ids or `#tags` that ignore this fluid.

The included `water_hurts_blazes` file works like this: water still puts out fire, and also deals 3 damage to blazes.

## Colors

```json
{ "fluid": "minecraft:lava", "colors": ["#FF6A00", "#FFB347"] }
```

Colors are sent to every player on join and after `/reload`.

## Included files

- `water_hurts_blazes.json`: water hurts blazes.
- `levitite.json`: Aeronautics levitite blend gives Levitation for 100 ticks.
