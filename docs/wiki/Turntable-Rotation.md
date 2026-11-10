## Rotation

The rotation calls need the turntable assembled.

### `turntable.getAngle()`

Current plate angle, 0 to 360, relative to the assembly angle.

### `turntable.turnBy(degrees)`

Turns by the given degrees from the current angle. The sign sets the direction, and
the value is not wrapped, so `turnBy(720)` is two full turns.

It returns immediately. The plate turns over the following ticks at the rate the cogwheel
speed allows. Poll `isTurning()` to wait for it.

```lua
turntable.turnBy(90)
repeat os.sleep(0.05) until not turntable.isTurning()
```

It cancels a running sequence. A manual turn and a sequence cannot both drive the plate, so the
sequence is dropped, not paused.

The sign is independent of the cogwheel. `turnBy(90)` always turns 90 degrees in the positive
direction. A `turn_angle` [sequence](Turntable-Sequences) entry follows the cogwheel, so the same list turns the other way when you reverse the input.

### `turntable.turnTo(degrees)`

Turns to an absolute angle, by the shortest path. `turnTo(350)` from 10 degrees turns 20
degrees backwards, not 340 forwards. Use `turnBy` to control the direction.

### `turntable.stop()`

Cancels the current manual turn or sequence and holds the current angle. The servo
still resists external force, the plate does not spin freely.

### `turntable.isTurning()`

True while a manual turn or a sequence is still moving the plate.

### `turntable.isSettled()`

True when the plate has not moved since the last tick and has no alignment target. The train code
uses this test before it allows a train onto a plate.

### `turntable.getRemainingDegrees()`

How much of the current `turnBy` or `turnTo` is left. Reads 0 during a sequence, use
`getCurrentInstruction()` there.
