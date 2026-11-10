## Getting a handle

Every example in this guide starts with a variable called `train`. This section is how you get one.

There are two ways, and which one you want depends on where the computer sits.

| Where the computer is | Use |
| --- | --- |
| Inside the cart, anywhere | `loconautics.getTrain()` |
| Bolted to the axle, or on its modem network | [`peripheral.find`](Glossary#peripheralfind-glossary) |

Both give you the same thing. The functions are identical, so nothing after this point
changes.

### `loconautics.getTrain()`

The `loconautics` global is on every computer. `getTrain()` looks at the
[sub level](Glossary#sublevel-glossary) the computer is in, and finds the
[Bearing Axle](Glossary#bearingaxle-glossary) anywhere in that cart. If there is none, it walks the coupled
[consist](Glossary#consist-glossary) and takes the first axle it finds.

Prefer this method. It works from any computer in the cart and keeps working after the
axle is moved or a carriage is recoupled.

```lua
local train = loconautics.getTrain()
print(train.getId())
```

```result
8f14e45f-ceea-467a-9f1b-2c3d4e5f6a7b
```

It throws an error instead of returning [nil](Glossary#nil-glossary). Two things can go wrong, and each
needs a different fix, so each has its own message:

```
this computer is not inside a train, place it on an assembled carriage
no bearing axle found in this train, the carriage needs one to be driven
```

The first means the computer is not in a cart at all. The second means it is, but the train has no
axle to talk to.

#### Wrap it in [`pcall`](Glossary#pcall-glossary) if you want to handle that instead of crashing:

```lua
local ok, train = pcall(loconautics.getTrain)
if not ok then
    print("no train here: " .. train)
    return
end
```

```result
no train here: this computer is not inside a train, place it on an assembled carriage
```

When it fails, the second value is the error message and not a train, which is why it prints
directly.

### `peripheral.find("loconautics:bearing_axle")`

<a id="peripheralfind-explained"></a>The axle is also a normal peripheral, so `peripheral.find` and `peripheral.wrap` work on it.

Range is limited. A computer only sees peripherals on its six touching faces or over a wired
modem network, so this finds the axle only if the computer is attached to it or wired to it.

Use this one for a station computer, a display or a control desk that does not ride the train.

It returns [nil](Glossary#nil-glossary) when it finds nothing, unlike `getTrain()`, so test for that.

```lua
local train = peripheral.find("loconautics:bearing_axle")
if not train then
    print("no axle on the network")
    return
end
```

`peripheral.wrap` is the other half of the pair, for when you know which side the axle is on.

```lua
local train = peripheral.wrap("left")
```

### `loconautics.isInSubLevel()`

<a id="sublevel-explained"></a>True if the computer is inside any Sable [sub level](Glossary#sublevel-glossary), train or not.

Use it before `getTrain()` to avoid an error.

```lua
if not loconautics.isInSubLevel() then
    print("place me inside the cart")
    return
end

local train = loconautics.getTrain()
```

True does not mean there is a train. A sub level with no axle passes this and then fails in
`getTrain()`, so keep the `pcall` above in a program that has to keep running.

### `loconautics.getStations([filter])`

Every station whose name matches `filter`, a wildcard like a schedule's, `*` for all when left out. Needs no train,
so a computer beside a turntable or a yard can call it.

Each entry is a table:

- `name`, the station name
- `id`, the station id as a string
- `dimension`, `x`, `y`, `z`, where the station block is, in world coordinates, and `sublevel`, true when it sits on a moving body
- `free`, true when no train is parked there and no scheduled train is on its way there
- `parkedTrain`, the [id](Identity#traingetid) of the train parked there, missing when none is
- `claimedBy`, the id of the scheduled train driving there right now, missing when none is

A schedule destination with a wildcard already skips any station that is not `free` and takes the nearest free
one, so use this to display state or to choose a station yourself. A schedule does not need it.

```lua
for _, s in ipairs(loconautics.getStations("Train Turntable*")) do
    print(s.name, s.free and "free" or ("taken by " .. (s.parkedTrain or s.claimedBy)))
end
```
