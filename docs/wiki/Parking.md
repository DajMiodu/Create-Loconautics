## Parking

Give the train a destination and let it drive itself there.

`parkAtStation` does not wait. It returns true straight away and then runs in the background, a tick
at a time. Check `isParking()` to find out when it is done. `parkAtCoord` is the exception, it waits
until the park is over and returns the result.

The [drivetrain](Drivetrain) has to be built right, or both calls throw.

You do not have to take a [control lease](Glossary#controllease-glossary) first, but the call opens one for
itself, and `setManualControl(true)` afterwards cancels the park.

A finished park leaves the brake on. Arriving sets the target speed to 0 and the brake to 1.0, so
call `setBrakeScale(0)` while holding a lease, or call `release()`, before you expect the train to
move again.

There are two versions. Prefer the station version.

### `train.parkAtStation(stationName)`

Plans a route when called, follows that route through every junction on the
way, and brakes the way a driver holding space does.

The train stops on the marker. When it does, the front of the train is on the
[station marker](Glossary#stationmarker-glossary) and `getStationName()` reports the station, the same as if
a player had parked it.

```lua
train.parkAtStation("Central")
while train.isParking() do
    os.sleep(0.2)
end
print("parked at " .. train.getStationName())
```

```result
parked at Central
```

It throws an error instead of setting off anyway when there is no way to get there:

```
no route from here to station "Central", the track may be broken between the two or the station
only takes trains from its other side
```

The second half of the message matters. A Create station faces one way. If the
train would arrive at the back of the marker, the station will not accept it, and driving will not
change that. Turn the train around, or turn the station's target with a wrench.

Wrap it in `pcall` when the route might not exist:

```lua
local ok, err = pcall(train.parkAtStation, "Central")
if not ok then
    print("cannot park: " .. err)
    return
end
```

```result
cannot park: no route from here to station "Central", the track may be broken between the two or
the station only takes trains from its other side
```

### `train.parkAtCoord(x, y, z)`

Drives to the point you give and stops with the front of the train on it. Use it for a siding or a
loading spot with no station on it.

The point has to be on the track. Give the coordinates of a rail block. Anything more than a block
and a half from the rails throws an error instead of driving somewhere else:

```
there is no track at 120, 64, -40 on this train's track graph, the nearest rail is 9 blocks away
```

It plans a route and steers through junctions, the same way a waypoint in a schedule does, and
lands the same way `parkAtStation` does. It errors when there is no route, just like `parkAtStation`.

It waits until the park is over, then returns `true` if the train stopped on the point and
`false` if it ended any other way. Your program pauses on this line while the train drives.

```lua
if train.parkAtCoord(120, 64, -40) == true then
    train.release()
else
    print("did not make it, stopped where it was")
end
```

`false` means it stopped somewhere else. That happens when the train was already past the point
when the park started, lost the track on the way, or something cancelled the park: `setTargetSpeed`,
`release`, or `setManualControl(true)`.

A pulled handbrake keeps it waiting. The park pauses while the handbrake holds and carries on
when it is released, so the call does not return until then.

No station is set either: after it lands, `getStationName()` still reads nil.

### `train.isParking()`

True while a park is still running. It turns false as soon as the train arrives, or gives up.

#### Three things end a park:

- The train arrives.
- It rolls past the target, in which case it stops where it is instead of reversing back.
- `setTargetSpeed` is called, which cancels the park and gives you the throttle. This is the supported way
  to stop one.

False does not mean the park succeeded. Check where the train actually ended up:

```lua
train.parkAtStation("Central")
repeat os.sleep(0.2) until not train.isParking()

if train.getStationState() == "stopped" then
    print("parked at " .. train.getStationName())
else
    print("park ended without arriving")
end
```
