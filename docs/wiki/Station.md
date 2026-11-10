## Station

Whether the train is at a platform, which one, and how long it has been there.

Four states, one at a time: `running`, `stopping`, `stopped`, `departing`.

`stopped` is the only one that means parked. It means the same thing the station block means when
it raises its flag, and it is kept across a save and reload.

The state is the same however the train got there, whether a driver, a [schedule](Schedule), a
computer parking it, or the station building it.

### `train.isAtStation()`

True in `stopping` and `stopped`, so it covers braking into the platform as well as standing there.

Too broad to test for parked. Read `getStationState()` when you mean standing still.

### `train.getStationState()`

One of `"running"`, `"stopping"`, `"stopped"`, `"departing"`.

```lua
local state = train.getStationState()

if state == "stopped" then
    print("at the platform")
elseif state == "stopping" then
    print("pulling in")
end
```

```result
at the platform
```

### `train.getStationName()`

Name of the station the train is standing at or braking for. [Nil](Glossary#nil-glossary) when there is none.

The name stays after the stop ends. It is kept for about four blocks after the train pulls away,
so a name on its own does not mean the train is still there. This is intentional, it keeps a car that
rocks on its couplers from losing its station.

#### Combine it with the state:

```lua
local name = train.getStationName()
if name and train.getStationState() == "stopped" then
    print("parked at " .. name)
end
```

```result
parked at Central
```

### `train.getTargetStationName()`

Where the [schedule](Schedule) is going next. [Nil](Glossary#nil-glossary) if no [schedule](Schedule) is running.

Not the same as `getStationName()`, which is where the train is now. One is where it is going,
the other is the platform it is at.

```lua
print("dest: " .. (train.getTargetStationName() or "unknown"))
```

### `train.getDwellTicks()`

How many ticks the train has been in the `stopped` state. It goes back to 0 as soon as it
leaves.

#### 20 ticks is one second.

```lua
print("stopped for " .. math.floor(train.getDwellTicks() / 20) .. "s")
```

```result
stopped for 7s
```

Enforce a minimum stop time:

```lua
local MIN_DWELL = 100    -- five seconds
repeat os.sleep(0.5) until train.getDwellTicks() >= MIN_DWELL
```
