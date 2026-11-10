## Kinetics

### `turntable.getSpeed()`

How fast the plate turns, in RPM, signed. Without an override this is the rotation reaching the
bearing, capped at 96. With one it is the override. Without rotation the speed is 0 and the plate
does not move. Check this first when a turn does not complete.

### `turntable.getCogSpeed()`

The rotation actually reaching the bearing, ignoring any override. Compare it with `getSpeed()` to
see how far the speed exceeds the input.

### `turntable.setSpeedOverride(rpm)`

Turns the plate at that speed regardless of the cogwheel, up to 256 RPM in either direction. The sign is
the direction, and 0 hands the plate back to the cogwheel.

You still need rotation. An override sets how fast, not whether. A turntable with a stalled cogwheel
does not turn no matter what you set, and it costs no stress either.

The override takes precedence over the cogwheel's direction. A `turn_angle` [sequence](Turntable-Sequences) entry
normally follows the input rotation, so reversing the cogwheel reverses the sequence. With an
override set, the override's own sign decides.

```lua
turntable.setSpeedOverride(64)      -- a 3 RPM cogwheel now turns the plate at 64
turntable.turnBy(90)
repeat os.sleep(0.05) until not turntable.isTurning()
turntable.clearSpeedOverride()
```

### `turntable.getSpeedOverride()` / `turntable.clearSpeedOverride()`

The current override in RPM, 0 when there is none, and the call that drops it. The override is saved
with the block, so it survives a reload and a disassembly.

### `turntable.getStress()`

The stress units the turntable is charging its network right now.

Stress is charged for the plate's speed, not the input speed. A turntable overridden to
64 RPM off a 3 RPM cogwheel costs the same SU as one driven at 64. The rate is
`blocks.turntable_bearing.suImpact` in the server config, 4 SU per RPM by default, so that 64 RPM plate
costs 256 SU. An unassembled turntable costs nothing.

```lua
if turntable.getStress() > 512 then
    turntable.setSpeedOverride(32)   -- reduce speed, the network is overloaded
end
```

### `turntable.getDegreesPerTick()`

How fast the plate can turn right now, unsigned. This is `getSpeed()` converted to degrees per tick,
so a 90 degree turn takes `90 / getDegreesPerTick()` ticks. An override moves this too.

```lua
local perTick = turntable.getDegreesPerTick()
if perTick == 0 then
    print("no rotation reaching the bearing")
else
    print(string.format("90 degrees takes %.1fs", 90 / perTick / 20))
end
```

### `turntable.isPowered()`

True when the turntable is assembled and rotation is reaching it. This reads the cogwheel, not the
override, so it is still false on a stalled cogwheel regardless of the override.
