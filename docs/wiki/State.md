## State

Five true or false values and one number, all read only, none needing a
[control lease](Glossary#controllease-glossary). Use these in monitoring loops.

### `train.isRailed()`

True while every [bogey](Glossary#bogey-glossary) is on track.

### `train.isDerailed()`

The opposite of `isRailed()`. Both exist so you can write the test either way round, use whichever
reads better.

A derailed train ignores almost everything. Body offsets, target speed and steering stop having
any effect until it is back on the rails, because a derailed car follows its physics body and not the
rail.

Use events instead of polling. `train_derailed` and `train_rerailed` fire when it changes,
which is cheaper than checking every tick.

```lua
if train.isDerailed() then
    print("derailed")
    redstone.setOutput("top", true)
end
```

Derail events do not fire when the world loads. A train that was already off the rails when the
world saved comes back derailed with no event, so read this once at startup before you start waiting
for events.

### `train.isBraked()`

True if any brake is on. That includes the short automatic brake at the end of a station stop, so it
turns true on arrival even when nobody pulled a brake.

### `train.isBrakeHard()`

True only for the handbrake, the one a player pulls. The automatic station brake does not set it.

Use this to detect a player taking over. A program that keeps driving while the handbrake is
pulled overrides the player in the cab.

```lua
if train.isBrakeHard() then
    print("handbrake pulled, handing back")
    train.setTargetSpeed(0)
    train.release()
    return
end
```

### `train.isScheduleHeld()`

True while the [schedule](Schedule) is holding the train, waiting for a departure condition, a red
signal or a route.

Read `getHeldReason()` to find out why, though it only names hardware faults.

### `train.getSteerInput()`

Current steering input. `-1` left, `0` straight, `1` right.

It reports the computer's value while one is set. If a computer called `steer()`, this reads that
value back. Otherwise it reports what the axle sees from its redstone links.

```lua
local dir = train.getSteerInput()
print(dir == -1 and "left" or dir == 1 and "right" or "straight")
```

```result
left
```
