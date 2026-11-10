## Network

The calls above are all one train's view. These calls see the whole railway, so a control desk can run
without every train reporting in. None of them need a train or a [control lease](Glossary#controllease-glossary),
a computer beside a desk, a monitor or a yard can call them, the same as [`getStations`](Getting-a-handle).

The lists are read only. They show what is there. Changing signals, points and trains from a computer is in
[Controlling the network](Controlling-the-network).

Every id is a string and stays the same across reloads. Trains use the [train id](Identity#traingetid), signals
use the signal's own UUID, blocks use the block UUID and track nodes use `dimension@x,y,z`.

A call that finds nothing returns an empty list, not [nil](Glossary#nil-glossary).

Ask for a region when the world is big. Every call that takes one accepts the same table, and every
key is optional:

```lua
local region = { dimension = "minecraft:overworld", minX = 0, maxX = 400, minZ = -200, maxZ = 200 }
```

A block here is Create's signal block: the stretch of track between two signals, the thing a signal
turns red for. It is not a block in the world.

### `loconautics.getTrains([filter])`

Every train on a track, one entry per [consist](Glossary#consist-glossary). `filter` is a wildcard on the train
name, `*` for all when left out.

Each entry is a table:

- `id`, `name`, `dimension`
- `x`, `y`, `z`, the same point `train.getWorldPos()` gives, missing while the body is not loaded
- `speed`, signed, blocks per tick
- `derailed`, `heldAtRed`
- `stationState`, one of `running`, `stopping`, `stopped`, `departing`
- `station`, the station it is at or braking for, missing when none
- `target`, where its schedule is going, missing when it has no schedule
- `remaining`, blocks of track left to `target`, missing when there is no schedule or no route
- `scheduleEntry`, `scheduleSize`, which stop of the schedule it is on and how many there are
- `paused`, true while the schedule is paused
- `heldReason`, why the schedule is holding the train, missing when it is not
- `carriages`, `dwellTicks`
- `length`, `width`, `height`, the size of the consist in blocks
- `blocks`, a list of the block ids the train is sitting in right now

```lua
for _, t in ipairs(loconautics.getTrains()) do
    print(string.format("%-12s %5.1f b/s  %s", t.name, t.speed * 20, t.station or "between stations"))
end
```

```result
Express 01     16.0 b/s  between stations
Freight 03      0.0 b/s  North Yard
```

A train with no Bearing Axle is not listed. The list matches what `loconautics.getTrain()` can drive.

### `loconautics.getSignals([region])`

Every signal that has a signal block placed, inside `region` when given. A signal sits on a piece of track
and can face both ways, so each entry has up to two `sides`.

- `id`, `dimension`, `x`, `y`, `z`, the position of the first signal block on it
- `sides`, a list of tables:
  - `side`, 1 or 2, always the same side for the same signal
  - `type`, `"block"` or `"chain"`
  - `state`, `"red"`, `"yellow"`, `"green"` or `"invalid"`
  - `forcedRed`, true while redstone holds it red
  - `block`, the id of the block this side guards the entrance to

```lua
for _, s in ipairs(loconautics.getSignals()) do
    for _, side in ipairs(s.sides) do
        print(s.id:sub(1, 8), side.side, side.type, side.state)
    end
end
```

```result
3b91c0aa    1    block    green
3b91c0aa    2    block    red
```

Yellow only comes from chain signals. Create has two states for a plain block signal, red and green.
A chain signal shows yellow when some of the routes behind it are free and some are not. Do not wait for a
yellow on a block signal.

`invalid` means the signal has no block to read yet, for example a signal facing a dead end.

Sable trains count. A train standing in a block or holding a reservation turns the signal behind it
red, the same way it does when the train drives.

### `loconautics.getBlocks([busyOnly])`

Every block that has a signal on it. Pass `true` to list only blocks with a train in them or a
reservation on them.

- `id`, the block id
- `occupiedBy`, a list of train ids sitting in it, empty when free
- `reservedBy`, the id of the train that has claimed it, missing when none
- `signals`, the ids of the signals that border it

```lua
for _, b in ipairs(loconautics.getBlocks(true)) do
    print(b.id:sub(1, 8), #b.occupiedBy .. " in", b.reservedBy and "reserved" or "")
end
```

```result
a04f7d12    1 in    
c8e21b90    0 in    reserved
```

Occupied is by wheels. A train is in every block one of its bogeys is on, so a long train can be in
two blocks at once. A derailed train is in none.

Reserved means the train is cleared to enter. A scheduled train claims the blocks ahead as it
approaches, so a reserved block with nobody in it is normal.

### `loconautics.getJunctions([region])`

Every track node with three or more connections, which is where a train can take more than one way.
Create has no switch block, the train picks its way with `steer` or its schedule.

- `id`, `dimension`, `x`, `y`, `z`
- `edges`, a list of `{to, length}`, the node each way leads to and how long that piece is

### `loconautics.getTurntables([region])`

Every turntable that is assembled, inside `region` when given.

- `id`, the plate's id
- `dimension`, `x`, `y`, `z`, the center of the axis
- `powered`, `settled`, `aligned`, the same states the turntable block reports
- `occupied`, true while a train is on the plate

```lua
for _, t in ipairs(loconautics.getTurntables()) do
    print(t.id:sub(1, 8), t.occupied and "train on it" or "free")
end
```

```result
5c2e0b71    free
```

### `loconautics.getTrack(region)`

The track itself inside a box, for drawing a map. The region is required, there is no way to ask for
the whole world.

Returns `{nodes, edges}`:

- each node is `{id, dimension, x, y, z, sublevel}`
- each edge is `{from, to, length, curve, portal, sublevel, path}`, listed once, `curve` is true for a bezier piece and
  `portal` for a piece that crosses dimensions

Track on a sub level is placed where it really is. A node, edge or signal built on a moving body, a
turntable plate for example, reports world coordinates for the moment of the call and `sublevel` is true.
A fixed map draws it again every second. Pass `subOnly = true` in the region to get only that moving track.

`path` is a flat list `{x1, z1, x2, z2, ...}` along a curve, in world coordinates, only on curved pieces
that stay in one dimension. Draw straight lines between its points. A piece without `path` is straight.

A portal piece has `portal = true` and no `path`. One end is in this dimension and one in the other. Its
near node is the portal point, the id of each end starts with its dimension.

A box holding more than 2000 nodes throws an error. Ask for a smaller one and stitch the pieces. An
edge that leaves the box is still listed, its far node is not.

```lua
local map = loconautics.getTrack({ minX = 0, maxX = 200, minZ = 0, maxZ = 200 })
print(#map.nodes .. " nodes, " .. #map.edges .. " edges")
```

```result
318 nodes, 331 edges
```

### Watching the network

Network events are sent only to a computer that asks for them, so a normal computer pays nothing.

```lua
loconautics.watchNetwork()
```

`loconautics.unwatchNetwork()` stops them. A computer that shuts down stops on its own. The first scan after
`watchNetwork` only records what exists, so you get no burst of events for the state that was already there.

| Event | Extra parameters | Fires when |
| --- | --- | --- |
| `signal_changed` | `signalId`, `side`, `state`, `oldState` | One side of a signal changes color |
| `block_occupied` | `blockId`, `trainId` | A train's wheels enter a block |
| `block_cleared` | `blockId`, `trainId` | The last of that train's wheels leave it |
| `train_added` | `trainId` | A train starts being tracked |
| `train_removed` | `trainId` | A train stops being tracked |
| `train_signal_passed_at_danger` | `trainId`, `distance` | A train not driven by a schedule reaches a red |

These are checked every four ticks, so an event can arrive up to 0.2 seconds late. Two changes inside
the same four ticks may show as one.

`train_signal_passed_at_danger` also goes to a computer holding that one train's handle, without `watchNetwork`.
`distance` is the distance to the stop line. It fires once for a red, the train has to leave the red before it can
fire again.

It only fires for trains a schedule is not driving. A scheduled train stops at reds. A train driven by
hand or by a computer runs through them, and this is how you hear about it.

```lua
loconautics.watchNetwork()

while true do
    local event, a, b, c = os.pullEvent()
    if event == "train_signal_passed_at_danger" then
        print("DANGER: " .. a:sub(1, 8) .. " ran a red")
    elseif event == "block_occupied" then
        print("block " .. a:sub(1, 8) .. " taken by " .. b:sub(1, 8))
    end
end
```

```result
block a04f7d12 taken by 8f14e45f
DANGER: 8f14e45f ran a red
```

### A track board

Lists every busy block with the names of the trains in it, and refreshes when anything changes.

```lua
loconautics.watchNetwork()

local function draw()
    local names = {}
    for _, t in ipairs(loconautics.getTrains()) do names[t.id] = t.name end
    term.clear()
    term.setCursorPos(1, 1)
    for _, b in ipairs(loconautics.getBlocks(true)) do
        local who = {}
        for _, id in ipairs(b.occupiedBy) do who[#who + 1] = names[id] or id:sub(1, 8) end
        print(b.id:sub(1, 8), table.concat(who, ", "))
    end
end

draw()
while true do
    local event = os.pullEvent()
    if event == "block_occupied" or event == "block_cleared" or event == "train_removed" then
        draw()
    end
end
```
