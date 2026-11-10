## Braking

Stopping a train from a computer: stop now, stop in a set distance, and read how far the train needs to stop. These calls take the [control lease](Glossary#controllease-glossary) for you, the way `parkAtStation` does, so there
is no `setManualControl(true)` first. They do not need the [drivetrain](Drivetrain) to be built right,
because a stop only brakes.

Nothing here waits. Each call returns a stop id on the same tick. Wait for the `train_stopped` event, or
poll `isStopping()`.

A finished stop leaves the brake on. Call `release()` or `setBrakeScale(0)` while holding the lease
before you expect the train to move again.

`setTargetSpeed` cancels a stop, and the event fires with `completed` false.

### `train.stop([emergency])`

Brings the train to a stop. Without `emergency` it slows on the service curve, the same gentle braking a
station stop uses. With `true` it brakes as hard as the train can, which is the stop to use when something
is wrong.

```lua
train.stop()
local _, id, stopId, completed = os.pullEvent("train_stopped")
print(completed and "stopped" or "cancelled")
```

```result
stopped
```

### `train.stopIn(distance)`

Stops the train in `distance` blocks of track ahead, following the way it is moving. It runs the service
curve to land on that mark, so it is how you stop at a signal or an obstacle without working out the speed
yourself.

The distance is to the nose. Use `getGapToRed()` as it is, it already measures from the nose to the stop
line.

The train has to be moving. A train standing still has nothing to stop.

It brakes harder if it is going to overrun. A distance shorter than `getBrakingDistance()` cannot be met
on the service curve, so the train brakes at full strength and stops past the mark. Check first when it matters.

```lua
local gap = train.getGapToRed()
if gap and gap < train.getBrakingDistance() * 1.5 then
    train.stopIn(gap)
end
```

### `train.isStopping()`

True from the moment `stop` or `stopIn` is called until the train has stopped or the stop is cancelled.

### `train.getBrakingDistance()`

Blocks the train needs to stop from its current speed on the service curve. It is the distance `stopIn` would
land on, so `stopIn(train.getBrakingDistance())` stops as gently as the train can.

### `train.getEmergencyBrakingDistance()`

The same with full braking, the shortest the train can stop in.

### `train.getServiceDecel()` and `train.getEmergencyDecel()`

The deceleration behind the two distances, in blocks per tick per tick. Use them for your own maths, for
example `v * v / (2 * train.getServiceDecel())` is `getBrakingDistance()` for a speed `v`.
`getAccelLimit()` is the speed up limit and is a different number.

### Event

| Event           | Extra parameters                 | Fires when                                                                      |
| --------------- | -------------------------------- | ------------------------------------------------------------------------------- |
| `train_stopped` | `trainId`, `stopId`, `completed` | A stop from `stop` or `stopIn` ends, `completed` is false when it was cancelled |
