## Identity

Calls that identify the train. Only `setName` changes anything, and none of them need
a [control lease](Glossary#controllease-glossary).

### `train.getId()`

The train's UUID as a string, the same string every event carries as its first parameter.

The id survives reloads, so you can save it and compare against it later.

Every assembled train has one, so it is never [nil](Glossary#nil-glossary).

At 36 characters it is long, so cut it with `:sub(1, 8)` when
printing.

```lua
print(train.getId())              --> 8f14e45f-ceea-467a-9f1b-2c3d4e5f6a7b
print(train.getId():sub(1, 8))    --> 8f14e45f
```

This is how a computer watching several trains tells their events apart:

```lua
local mine = train.getId()

while true do
    local event, id, station = os.pullEvent("train_arrived")
    if id == mine then
        print("my train is at " .. station)
    end
end
```

```result
my train is at Central
```

### `train.getName()`

The display name, or an empty string if nobody named it.

Never [nil](Glossary#nil-glossary). An unnamed train reads `""`, and an empty string is truthy in Lua, so
`getName() or something` never reaches the fallback. Test the string itself:

```lua
local name = train.getName()
local label = name ~= "" and name or ("Train " .. train.getId():sub(1, 8))
print(label)
```

```result
Express 01
```

### `train.setName(name)`

Renames the whole [consist](Glossary#consist-glossary), the same field the train screen edits.

Takes effect at once, no lease and no reassembly.

Every carriage behind the first gets named too, `"<name> Carriage 1"`, `"<name> Carriage 2"` and
so on. `getName()` reads the first one back.

`""` clears it, and `getName()` goes back to `""`.

[Nil](Glossary#nil-glossary) is not accepted. The argument has to be a string, so `setName(nil)` errors with
`bad argument #1 (string expected, got nil)`. Pass `""` instead.

#### Saved with the train.

```lua
train.setName("Express 01")
print(train.getName())    --> Express 01

train.setName("")
print(train.getName())    --> prints an empty line
```

### `train.getCarriageCount()`

How many carriages are coupled into the [consist](Glossary#consist-glossary), counting the locomotive.

#### Always at least 1.
Use it as the limit for every carriage loop. Indices run from 1 to this number, and anything
outside that throws an error, so read it instead of guessing.

```lua
for i = 1, train.getCarriageCount() do
    print("carriage " .. i .. ": " .. train.getCarriagePassengerCount(i) .. " aboard")
end
```

```result
carriage 1: 0 aboard
carriage 2: 3 aboard
carriage 3: 1 aboard
```

Read it again after coupling. The number changes when carriages are added or dropped, so read it
inside the loop that uses it instead of storing it once at the top of a long running program.

### `train.getLength()`

The length of the whole [consist](Glossary#consist-glossary) in blocks, from the nose of the first carriage to the end of
the last, with the gaps between carriages counted. It is the same length the station stop uses, so it is the
number to compare with a platform.

It changes when carriages are added or dropped, read it again after coupling.

For one carriage, use `train.getCarriage(i).getLength()`, see [Carriages](Carriages).

```lua
local length = train.getLength()
local platform = 60
print(string.format("%.1f blocks, %s", length, length <= platform and "fits the platform" or "too long"))
```

```result
42.5 blocks, fits the platform
```

### `train.getWidth()` and `train.getHeight()`

The other two sizes of the [consist](Glossary#consist-glossary), in blocks, for checking a tunnel, a bridge or a
platform edge.

- **`getWidth()`** is the side to side size, across the track. It is the widest carriage.
- **`getHeight()`** is the up and down size. It is the tallest carriage, measured from the lowest block to the
  highest, so the bogeys are included.

Both are box sizes, measured on the box that holds all of a carriage's blocks, so one roof vent
or one step makes the whole carriage that wide or that tall. Round up when you compare them with a gap.

For one carriage, use `c.getWidth()` and `c.getHeight()`.

```lua
print(string.format("%.0f wide, %.0f tall", train.getWidth(), train.getHeight()))
if train.getHeight() > 5 then print("will not clear a 5 block tunnel") end
```

```result
5 wide, 6 tall
will not clear a 5 block tunnel
```
