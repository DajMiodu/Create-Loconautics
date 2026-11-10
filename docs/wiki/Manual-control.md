## Manual control

The four setters that drive the train, and the calls that take and drop the
[control lease](Glossary#controllease-glossary).

Take the lease first. `setTargetSpeed`, `setAccel`, `setBrakeScale` and `steer` all throw
`manual control is not enabled` without one. `setDoors` is the exception, see [Doors](Doors).

Every call returns on the same tick and none of them wait. To drive, set a value and then check the
result in a loop.

### `train.setManualControl(enabled)`

Takes the lease with `true`, drops it with `false`. See [Who owns the train](Who-owns-the-train).

A new lease starts with nothing set. So a brake the last program left on cannot carry over into
yours.

Taking the lease throws if the [drivetrain](Drivetrain) is not built right, with a message saying what
to fix.

### `train.isManualControl()`

True while any computer holds a lease on this train, not only yours. Check it before taking over
if two programs might be running.

### `train.release()`

Drops the lease and unbinds every [frequency](Glossary#frequency-glossary) slot this handle opened.
`setManualControl(false)` only does the first of those.

Use this at the end of a program. It leaves nothing set.

Crashes are already handled. The mod drops the lease when the computer detaches, shuts down or
unloads, so a stopped program never leaves a train driving itself.

### `train.setTargetSpeed(speed)`

The speed to aim for, in blocks per tick. A negative value drives in reverse.

This sets a target and the speed follows. The train moves towards it at
`getAccelLimit()` and reaches it some ticks later.

The call goes through the transmission, which it sets to the RPM and direction that give this speed,
and the engine has to supply the rotation, see [Drivetrain](Drivetrain). It throws if the drivetrain is not
built right.

It cancels a park that is running, which is the supported way to stop one.

```lua
train.setTargetSpeed(0.8)
repeat os.sleep(0.05) until math.abs(train.getSpeed() - 0.8) < 0.01
print("up to speed")
```

### `train.setAccel(accel)`

How fast the train may change speed, in blocks per tick per tick.

The sign is ignored, it applies to speeding up and slowing down the same way.

Set it before the speed. It controls the speed change that `setTargetSpeed` starts, so setting it
afterwards means the first change used the old value.

0.005 is a good starting point for a passenger train, lower for freight.

### `train.setBrakeScale(scale)`

Brake force from 0.0 to 1.0.

Anything above 0 also marks the train as braked, which `isBraked()` reports.

A value outside 0 to 1 throws an error instead of being clamped.

Set it back to 0 when stopped. A brake left at 1.0 holds the train even after the lease is
gone.

```lua
train.setTargetSpeed(0)
train.setBrakeScale(1.0)
repeat os.sleep(0.05) until math.abs(train.getSpeed()) < 0.01
train.setBrakeScale(0.0)
```

### `train.steer(dir)`

`-1` left, `0` straight, `1` right. Any other value throws an error.

Directions are relative to the front of the train, not to north, so the value means the same whichever way the
train sits on the rail.

Steering only matters at a junction and does nothing on plain track, so it is safe to set it
every tick.

### A full drive

Take the lease, run at speed, slow down near the target, stop, give it back. Every step sets a value
and then checks it in a loop.

```lua
train.setManualControl(true)
train.setAccel(0.005)
train.setTargetSpeed(0.8)

while (train.getDisToStation("North Yard") or math.huge) > 20 do
    os.sleep(0.1)
end

train.setTargetSpeed(0.0)
train.setBrakeScale(1.0)
repeat os.sleep(0.05) until math.abs(train.getSpeed()) < 0.01
train.setBrakeScale(0.0)
train.setManualControl(false)
```

The `or math.huge` is there so the comparison still works if the call returns [nil](Glossary#nil-glossary).
Comparing [nil](Glossary#nil-glossary) with a number ends the program, and `math.huge` is always greater than
20, so a missing answer keeps the loop running. See
[Routing and distance](Routing-and-distance) for when this call throws an error instead.
