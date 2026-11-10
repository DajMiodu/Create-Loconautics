## Quick start

Put a computer next to the bearing, then run this. It assembles the plate if needed, turns it a quarter
turn and waits for the turn to finish. The plate needs rotation reaching the bearing, or it does not move.

```lua
local turntable = peripheral.find("loconautics:turntable_bearing")
if not turntable then error("no turntable bearing attached") end

if not turntable.isAssembled() then turntable.assemble() end
turntable.turnBy(90)
repeat os.sleep(0.05) until not turntable.isTurning()
print(turntable.getAngle())
```

### Finding what you need

| I want to | Go to |
| --- | --- |
| Get the turntable to talk to | [Getting a handle](Turntable-Getting-a-handle) |
| Know how angles, speed and errors work | [Conventions](Turntable-Conventions) |
| Build or release the plate | [Assembly](Turntable-Assembly) |
| Turn the plate to an angle | [Rotation](Turntable-Rotation) |
| Change the turn speed or read the stress | [Kinetics](Turntable-Kinetics) |
| Run a repeating pattern without the computer | [Sequences](Turntable-Sequences), [Redstone](Turntable-Redstone) |
| Let scheduled trains use the plate | [Schedule mode](Turntable-Schedule-mode) |
| Find the plate or its axis in the world | [Geometry](Turntable-Geometry) |
| Read everything in one call | [State snapshot](Turntable-State-snapshot) |
| Copy a working program | [Examples](Turntable-Examples) |

### Which turn call to use

| Call | Turns | Direction |
| --- | --- | --- |
| `turnBy(degrees)` | By that amount from now, not wrapped | The sign of the number |
| `turnTo(degrees)` | To an absolute angle | The shortest way |
| [Sequence](Turntable-Sequences) | A saved list of turns and waits | Follows the cogwheel, or the override's sign |
