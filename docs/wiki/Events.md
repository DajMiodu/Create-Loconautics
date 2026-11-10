## Events

These events fire for a train when its state changes, so a program can wait instead of checking in a loop.

Every one gives the train's UUID first, so a computer watching more than one train can tell them
apart.

<a id="pullevent-explained"></a>`os.pullEvent` pauses the program until an event fires, then returns the event name first and the
other values after it. The examples below discard it into `_`.

| Event | Extra parameters | Fires when |
| --- | --- | --- |
| `train_arrived` | `trainId`, `stationName` | The train enters the `stopped` state |
| `train_departed` | `trainId`, `stationName` | It leaves `stopped` for `departing` or `running` |
| `train_derailed` | `trainId` | The train comes off the rails |
| `train_rerailed` | `trainId` | It is put back on the rails |
| `train_signal_red` | `trainId`, `gapDistance` | It stops at a red signal |
| `train_signal_clear` | `trainId` | The signal holding it turns green |
| `train_parked` | `trainId`, `onTarget`, `parkId` | A computer park ends, `onTarget` is true when it stopped on the mark |
| `train_stopped` | `trainId`, `stopId`, `completed` | A [stop](Braking) ends |
| `train_maneuver_start` | `trainId` | A schedule starts a maneuver leg |
| `train_maneuver_end` | `trainId` | The maneuver leg ends |
| `train_maneuver_dead_end` | `trainId`, `destinationName` | A maneuver finds no path and stops |

The railway wide events, `signal_changed`, `block_occupied` and the rest, are in [Network](Network). They need `loconautics.watchNetwork()`.

A computer that called `watchNetwork()` gets all of the events above for every train, not only the train it holds a handle to. The train id is always the first value.

### Waiting for one

Give the event name and [`os.pullEvent`](Glossary#pullevent-glossary) ignores everything else:

```lua
local _, id = os.pullEvent("train_derailed")
print("DERAIL: " .. id)
redstone.setOutput("top", true)
```

```result
DERAIL: 8f14e45f-ceea-467a-9f1b-2c3d4e5f6a7b
```

```lua
local _, id, station = os.pullEvent("train_arrived")
print(id .. " arrived at " .. station)
```

```result
8f14e45f-ceea-467a-9f1b-2c3d4e5f6a7b arrived at Central
```

```lua
local _, id, gap = os.pullEvent("train_signal_red")
print(string.format("held at red, %.1f blocks short", gap))
```

```result
held at red, 42.5 blocks short
```

### Filtering by train

With no name it returns every event, so filter by id.

```lua
local mine = train.getId()

while true do
    local event, id, extra = os.pullEvent()
    if id == mine then
        print(event .. " " .. tostring(extra))
    end
end
```

```result
train_signal_red 42.5
train_signal_clear nil
train_arrived Central
```

### Notes

A computer starts receiving events once it has a handle. Attaching the peripheral is enough for
the `peripheral.find` way, and one successful `loconautics.getTrain()` call is enough for the other.

`stationName` can be an empty string. This happens when the station cannot be found, for example when
a derail pushes the train out of a stop.

`train_arrived` fires once per stop, however the train got there. A [schedule](Schedule)
arriving, a driver holding space, a computer parking it, or a car coming to rest on a marker.

Signal events need a [schedule](Schedule) driven train, because only the schedule driver stops at
signals. A train driven by hand never reports a red.

Derail and rerail do not fire when the world loads. A train that was already derailed when the
world was saved comes back derailed with no event, so read `isDerailed()` once at startup.

### Watching events alongside a main program

`os.pullEvent` waits, so a program that watches events and does something else needs two
[coroutines](Glossary#coroutine-glossary). `parallel.waitForAny` runs them at the same time and returns when
either one finishes.

```lua
-- watch for faults while the main program runs
parallel.waitForAny(
    function()
        while true do
            local event, id = os.pullEvent()
            if id == train.getId() then
                if event == "train_derailed" then
                    redstone.setOutput("top", true)
                elseif event == "train_rerailed" then
                    redstone.setOutput("top", false)
                end
            end
        end
    end,
    mainLoop
)
```

Without it, the event loop blocks on `os.pullEvent` and the main loop never runs.
