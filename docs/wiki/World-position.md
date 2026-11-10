## World position

Where the train is in the world, in world coordinates, not [sub level](Glossary#sublevel-glossary) ones.

### `train.getWorldPos()`

The point the carriage body turns around, as `{x, y, z}`.

It is the physics center, not the corner of any block, so it falls between block coordinates
rather than on them.

One point for the whole carriage. To find one block, use `getWorldPosOf` below.

```lua
local p = train.getWorldPos()
print(string.format("%.1f %.1f %.1f", p.x, p.y, p.z))
```

```result
123.5 71.0 -840.2
```

Distance from the train to a fixed point:

```lua
local p = train.getWorldPos()
local dx, dz = p.x - 100, p.z - 250
print(string.format("%.1f blocks from the depot", math.sqrt(dx * dx + dz * dz)))
```

```result
1090.4 blocks from the depot
```

### `train.getWorldPosOf(blockId)`

Every block matching `blockId` anywhere in the [consist](Glossary#consist-glossary), as a list of `{x, y, z}`
tables in world coordinates.

Use full ids only. `"minecraft:chest"`, never `"chest"`.

Only blocks with a block entity are found. It reads each carriage's list of block entities, so a
chest, a tank or a controller is found and a plain `minecraft:stone` is not.

It searches the whole train, every carriage, not only the one with the axle.

An empty list when nothing matches, not [nil](Glossary#nil-glossary), so test with `#result == 0` and it is
safe to loop over either way.

It has its own name rather than an argument because Lua has no overloading.

<a id="ipairs-explained"></a>

```lua
for i, pos in ipairs(train.getWorldPosOf("minecraft:chest")) do
    print(string.format("chest %d at %.1f %.1f %.1f", i, pos.x, pos.y, pos.z))
end
```

```result
chest 1 at 122.5 71.5 -838.5
chest 2 at 122.5 71.5 -839.5
chest 3 at 124.5 71.5 -841.5
```

[`ipairs`](Glossary#ipairs-glossary) reads a plain list in order, 1, 2, 3, which is what this returns. To
count them instead:

```lua
local chests = train.getWorldPosOf("minecraft:chest")
print(#chests .. " chests aboard")
```

```result
3 chests aboard
```
