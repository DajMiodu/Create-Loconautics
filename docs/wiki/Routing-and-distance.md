## Routing and distance

How far away something is, and which turns get you there.

Every call starts at the front of the train and only searches the way it is pointing. That rule
explains most unexpected results. A plain shortest path search would route out of the back of the
train and return the wrong distance and an empty step list.

A target on the same piece of rail as the front of the train works. It returns the distance to it if it is
ahead, or throws `the target is behind it on the same rail` if not. `getStepsToStation` returns an empty list.

If nothing can be reached ahead, they throw `no route ahead of the train`. Turn the train around
or drive it out of the dead end to fix it.

#### They find stations past a junction.

### `train.getDisToStation(stationName)`

How many blocks along the rail to the station. `stationName` is optional.

Give a name and it routes to that station. Leave it out and it checks every station ahead and
returns the nearest one, whichever that turns out to be.

It measures to the marker, not to the nearest whole rail segment. It counts down smoothly and
reaches 0 as the nose crosses the [station marker](Glossary#stationmarker-glossary).

Do not use it to work out braking. `parkAtStation` already brakes, and it measures properly.

There are two different errors for a named station. One that does not exist says so. One that
exists but is on a different track network says that instead, because it is a different problem with
a different fix. Leaving the name out skips both, it just returns [nil](Glossary#nil-glossary) when nothing is
reachable ahead.

```lua
local ok, dist = pcall(train.getDisToStation, "Central")
print(ok and string.format("%.0f blocks out", dist) or ("no route: " .. dist))

local nearest = train.getDisToStation()
print(nearest and string.format("%.0f blocks to nearest station", nearest) or "nothing ahead")
```

```result
310 blocks out
280 blocks to nearest station
```

### `train.getDisToCoord(x, y, z)`

The same, but to the nearest piece of track to a world coordinate.

### `train.getStepsToStation(stationName)`

A list of the turns needed to reach the station, in order. Each entry is `"left"`, `"right"` or
`"straight"`.

Only real junctions get an entry. A Create curve is many short pieces of track joined end to end,
and none of those are choices, so a long curve adds nothing to the list.

The list gets shorter as the train passes each junction, so call it again after every turn
instead of reusing a copy you took at the start.

An empty list means no junctions remain between here and the station.

```lua
local steps = train.getStepsToStation("East Depot")
for i, step in ipairs(steps) do
    print(i .. ": " .. step)
end
```

```result
1: left
2: straight
3: right
```

Steering from the first entry:

```lua
while true do
    local steps = train.getStepsToStation("East Depot")
    if #steps == 0 then break end

    local step = steps[1]
    train.steer(step == "left" and -1 or step == "right" and 1 or 0)
    os.sleep(0.1)
end
```

### `train.getStepsToCoord(x, y, z)`

The same, but to a coordinate.
