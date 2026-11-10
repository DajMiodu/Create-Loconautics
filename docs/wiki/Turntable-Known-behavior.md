## Known behavior

- **There are no turntable events.** Nothing is queued when a turn ends, poll `isTurning()` or
  `getState()`, see [State snapshot](Turntable-State-snapshot). A 0.05 second sleep in the loop is one game tick, which is as often as anything can
  change.
- **The peripheral survives a chunk reload, the sub level id does not.** Keep the handle, look the id
  up again each time you need it.
- **Priority: schedule mode, then manual turn, then sequence.** A `turnBy` while a sequence runs
  cancels the sequence. Any turn at all while the list is the `schedule` one is refused, the router
  controls the plate, see [Schedule mode](Turntable-Schedule-mode).
- **`turnTo` takes the short way.** Crossing 0 in either direction is fine, but if the plate must
  avoid a leg, use `turnBy` with an explicit sign.
- **Angles have small rounding error.** The plate steps by `getDegreesPerTick()` per tick and
  takes a smaller last step, so a 90 degree turn lands within about 0.01 degrees. Compare
  angles with a tolerance, never with `==`.
- **Disassembling zeroes the angle.** After a `disassemble` and a fresh `assemble`, 0 means whatever
  the plate is pointing at now, not what it meant before.
