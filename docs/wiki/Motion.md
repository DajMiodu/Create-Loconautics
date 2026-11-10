## Motion

Everything the physics knows about how the train is moving. These are all read only, none need a
[control lease](Glossary#controllease-glossary), and none of them return [nil](Glossary#nil-glossary).

Speeds are blocks per tick. Multiply by 20 for blocks per second.

### `train.getSpeed()`

How fast the train is going right now. `getTargetSpeed()` is the speed it was asked for.

The sign gives the direction, positive forward and negative in reverse, so use `math.abs` when you only care how
fast it is going.

A parked train reads 0, or close to it. Small rounding errors are left over, so compare against a
small number instead of against 0.

```lua
print(string.format("%.2f b/t  %.1f b/s", train.getSpeed(), train.getSpeed() * 20))

if math.abs(train.getSpeed()) < 0.01 then
    print("stopped")
end
```

```result
0.80 b/t  16.0 b/s
```

To wait for a full stop, put the same test in a loop:

```lua
train.setTargetSpeed(0)
repeat os.sleep(0.05) until math.abs(train.getSpeed()) < 0.01
print("stood still")
```

### `train.getTargetSpeed()`

The speed the train is trying to reach, whether it was set by a driver, a [schedule](Schedule) or
your own `setTargetSpeed`.

#### Compare the two to see if the train is still changing speed:

```lua
if math.abs(train.getSpeed() - train.getTargetSpeed()) > 0.01 then
    print("still ramping")
end
```

### `train.getTargetRpm()`

The RPM the transmission on the train is asking for, same as `train.getTransmission().getTargetRpm()`.

Errors if the drivetrain is not connected.

### `train.getAccel()`

How much the rail speed changed over the last tick, signed the same way `getSpeed()` is.

Despite the name, it is the change in the signed speed and not a plain "speeding up" flag, so a train
running in reverse reads negative while it speeds up and positive while it brakes. Compare it
with the sign of `getSpeed()` when the train can run either way.

It reads either one fixed number or 0, almost never anything between. That is correct, not a
stuck value. The train changes speed by a fixed step each tick, so it is either at the limit or off.

Two cases give a number in between: the last part of a speed change, and a station approach,
where the target follows a curve.

Do not use this for braking maths. Use `getAccelLimit()`, which has a value even at a steady
speed.

```lua
local a = train.getAccel()
local v = train.getSpeed()
local along = v < 0 and -a or a          -- along the way the train is pointing

print(along > 0 and "accelerating" or along < 0 and "braking" or "steady")
```

```result
accelerating
```

### `train.getAccelLimit()`

The most the train is allowed to change speed by, in blocks per tick per tick. Use this number for
any stopping calculation, because it has a value even at a steady speed.

Stopping distance is `v squared / (2a)`, with `v` as the current speed and `a` as this limit.

```lua
-- can we stop before the next red?
local gap = train.getGapToRed()
if gap then
    local v = math.abs(train.getSpeed())
    local stoppingDistance = (v * v) / (2 * train.getAccelLimit())
    if stoppingDistance > gap then
        print("cannot stop in time")
    end
end
```

`getGapToRed()` returns [nil](Glossary#nil-glossary) when nothing is red ahead, which is why the whole block is
inside `if gap then`. Doing maths on a [nil](Glossary#nil-glossary) would end the program.

### `train.getMass()`

Total mass of the [consist](Glossary#consist-glossary).

Sable does not use kilograms. It measures in kilopixelgrams, a weight per type of block rather than a real
density. Treat it as a relative number.

Rough scale: a small cart is in the tens, a full train in the hundreds.

### `train.getStressRatio()`

Kinetic network load as a fraction, stress divided by capacity. At 1.0 the network is full, and an
overstressed one reads above 1.0 instead of stopping at 1.0.

It reads 0 when there is no network, because a capacity of 0 skips the division.

```lua
print(string.format("stress %.0f%%", train.getStressRatio() * 100))

if train.getStressRatio() > 0.9 then
    print("network nearly maxed")
end
```

```result
stress 62%
```

### `train.getSlopeAngle()`

How steep the train is sitting, in degrees. Flat track reads 0. It is `getPitch()` without the sign,
so the two always agree.

Only the steepness is reported, not the direction, so a climb and a drop of the
same angle read the same. Work out the direction yourself from the sign of `getSpeed()`.

A decorative tilt does not change it. `setBodyPitch` and the other body setters are taken back
off first, so only the track moves it. A carriage leaning on a curve does not change it either,
that is roll.

### `train.getSlope()`

The same angle as `getSlopeAngle()`, with a sign. Positive means the train is climbing in the direction it
is moving, negative means it is going down. A train standing still is measured along its front.

```lua
local slope = train.getSlope()
print(slope > 1 and "climbing" or slope < -1 and "descending" or "level")
```

```result
climbing
```

### `train.isPowered()`

True when the locomotive has power.

False means the engine is not pulling. Test this first when a train that should be moving is not. A
broken [drivetrain](Drivetrain) also holds a computer driven train at 0, so read `getDrivetrain()` too.
