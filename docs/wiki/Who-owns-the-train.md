## Who owns the train

A driver, a [schedule](Schedule) and a computer can all set the target speed. The computer
takes priority until it releases the train.

<a id="controllease-explained"></a>`setManualControl(true)` takes a new [control lease](Glossary#controllease-glossary). While you hold it, the values
you set with `setTargetSpeed`, `setAccel`, `setBrakeScale` and `steer` are written last on every
tick, after the axle, the [schedule](Schedule) and the signal speed cap.

A new lease starts with every value unset. A brake left on by the last program cannot carry over
into this one.

One lease at a time. `isManualControl()` reads true while any computer holds it, not only yours.

The four setters throw `manual control is not enabled` without it. `setDoors` is the exception,
see [Doors](Doors).

Three other calls open a lease without being asked. `setDoors`, `parkAtStation` and
`parkAtCoord` take one if nobody holds it, so `isManualControl()` reads true afterwards and the four
setters stop throwing. That lease is not a new one, so still call `setManualControl(true)` when you
want the values cleared, and still call `release()` at the end.

### Taking it and giving it back

```lua
train.setManualControl(true)
train.setTargetSpeed(0.6)
-- ... drive ...
train.setManualControl(false)
```

`setManualControl(false)` drops the lease. `release()` drops the lease and unbinds every
[frequency](Glossary#frequency-glossary) slot the computer opened. Use `release()` at the end of a program that
used frequencies, so nothing is left set.

### You do not have to be careful about crashes

The mod drops the lease when the last computer detaches from the peripheral, or when a
computer using `getTrain()` shuts down.

Breaking the computer, turning it off, or unloading the chunk all release the train. A program
that stops partway does not leave the train driving.

That handles the crash, but not the values. A brake set to 1.0 or a door held shut stays that way
until the lease drops, so a program that might be stopped should leave safe values set where it
can.
