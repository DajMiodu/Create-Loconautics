## Orientation

Which way the carriage holding the axle is pointing, read from the physics body.

These are the real angles on the rail. Do not confuse them with [Body offset](Body-offset), which
is the decorative angle you set yourself. `getYaw()` is the direction the train is driving,
`getBodyYaw()` is the extra turn you asked the body to hold on top of it.

All of them throw `sub level not available` when the body is not loaded, which happens when the
train is outside a loaded chunk. Wrap them in `pcall` in a program that runs while nobody is near.

All three measure from the locomotive's front, the way the Bearing Axle faces, so tilting the
train on one axis does not change the other two. They come back rounded to hundredths of a degree.
They read the train on the track, `setBodyRoll`, `setBodyPitch` and `setBodyYaw` never change them.

### `train.getYaw()`

Direction in degrees. 0 is south, the same as vanilla Minecraft, and it counts up clockwise.

It wraps around. 359 goes back to 0, so subtracting two readings gives a large wrong jump once
per turn. To fix that, add 540, take the remainder after dividing by 360, then subtract 180:

```lua
local turn = (yaw - lastYaw + 540) % 360 - 180
```

That gives the shortest turn between the two readings, with a sign, and it never jumps.

### `train.getPitch()`

Nose up or down in degrees. Positive means pointing down, the same as vanilla Minecraft, so a
climbing train reads negative.

### `train.getRoll()`

Side tilt in degrees.

```lua
print(string.format("yaw %.1f  pitch %.1f  roll %.1f",
    train.getYaw(), train.getPitch(), train.getRoll()))
```

```result
yaw 180.0  pitch 0.0  roll 3.0
```

### `train.isUpright(minDot)`

True when the carriage is still upright. `minDot` is how strict you want to be, from 0 to 1.

Use 0.9 for level, 0.7 for still rideable. 1.0 means perfectly flat, and almost nothing passes
that.

The number is the dot product of the carriage's up direction with world up. It is 1 when the two
point the same way and 0 when the car is on its side.

```lua
if not train.isUpright(0.8) then
    print(string.format("leaning: roll %.1f pitch %.1f", train.getRoll(), train.getPitch()))
end
```

```result
leaning: roll 12.4 pitch 1.1
```

### `train.getVelocity()`

The carriage body's speed through the world as a table `{x, y, z}`.

This one is blocks per second, not per tick. It comes straight from the physics body, so it is
the one exception to the rule in [Conventions](Conventions). Divide by 20 to compare it with
`getSpeed()`.

It is not the same as `getSpeed()`. `getSpeed()` is one signed number along the rail. This is the
real movement through the world, so it includes sideways and up or down movement that the rail speed
hides. That makes it useful on a derailed train.

#### It throws an error if the body is not being simulated.

```lua
local v = train.getVelocity()
print(string.format("%.2f b/s horizontal", math.sqrt(v.x ^ 2 + v.z ^ 2)))
print(string.format("%.2f b/s vertical", v.y))
```

```result
15.83 b/s horizontal
-0.40 b/s vertical
```

A falling car reads a large negative `v.y` while `getSpeed()` still reports whatever it was doing
along the rail.
