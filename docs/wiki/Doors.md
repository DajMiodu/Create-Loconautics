## Doors

Doors cover the whole [consist](Glossary#consist-glossary). There is no door state per carriage, one call
moves every door on the train.

No [control lease](Glossary#controllease-glossary) needed. `setDoors` is the one setter that works without
one, but it opens a lease for itself, so `isManualControl()` reads true after you call it.

Calling `setDoors` removes station control of the doors. The station normally opens them on
arrival and shuts them on departure. Once a computer sets them, the station no longer changes them and
they stay as set until you give them back.

### `train.doorsOpen()`

True while the doors are open.

### `train.setDoors(open)`

Opens every door with `true`, shuts them with `false`.

The setting stays. The station will not change it back, which also means it will not close the
doors for you.

### `train.releaseDoors()`

Gives the doors back to the station.

Call it when you are done, or the doors stay where you left them until the lease is released.

### Holding a stop open

Open, wait, close, then return control to the station.

```lua
train.setDoors(true)
os.sleep(5)
train.setDoors(false)
train.releaseDoors()
```

### Doors that wait for the platform

Open on arrival, wait until a link says the platform is clear, then close and give them back.

```lua
train.setFrequency("platform", "minecraft:green_dye", "minecraft:white_dye")

local _, id, station = os.pullEvent("train_arrived")
if id == train.getId() then
    train.setDoors(true)
    print("doors open at " .. station)

    repeat os.sleep(0.5) until train.getFrequencyState("platform") > 0

    train.setDoors(false)
    train.releaseDoors()
end
```

A missing `releaseDoors()` is the common error. The doors stay shut and the next station cannot
open them.
