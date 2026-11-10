## Sequences

A sequence is the instruction list the bearing's own screen edits, up to five entries. Running one
from Lua is an alternative to `turnBy`, useful when the plate must repeat a pattern
without the computer running.

Instruction types:

| type | value | what it does |
|---|---|---|
| `turn_angle` | 1 to 360 degrees | turns by that much |
| `delay` | 1 to 600 ticks | waits |
| `await` | none | stops until the next redstone rising edge |
| `end` | none | stops, or restarts on `hold` |
| `schedule` | none | hands the plate to the train router, see [Schedule mode](Turntable-Schedule-mode) |

Speed modifiers, only meaningful on `turn_angle`: `forward`, `forward_fast`, `back`, `back_fast`.
The fast ones are double rate, the back ones reverse.

### `turntable.getInstructions()`

The current list, as an array of `{type = , speed = , value = }`.

### `turntable.setInstructions(list)`

Replaces the list. Takes an array of tables, `speed` defaults to `forward` and `value` defaults to
the type's own default. Values outside the allowed range are clamped, not rejected.

```lua
turntable.setInstructions({
    { type = "turn_angle", value = 90 },
    { type = "delay",      value = 40 },
    { type = "turn_angle", value = 90, speed = "back_fast" },
    { type = "end" },
})
```

A list holding `schedule` becomes a one entry list. Schedule mode owns the plate, so the other
instructions are dropped.

Setting the list resets the sequence. Anything running is canceled and any alignment is cleared.

### `turntable.getTrigger()` / `turntable.setTrigger(mode)`

How a redstone rising edge starts the list.

- `pulse`: runs the list once.
- `hold`: repeats the list while redstone stays on.
- `alternate`: runs the list once, and flips the direction of every turn for the next run.

### `turntable.runSequence()`

Starts the list at the first instruction, without any redstone.

### `turntable.isRunningSequence()` / `turntable.getCurrentInstruction()`

Whether a sequence is running, and which entry it is on, 1 based. `getCurrentInstruction()` reads 0
when nothing is running.

### `turntable.resume()`

Steps past the current instruction to the next one. This is what an `await` waits for, so call it
instead of pulsing redstone. Throws if no sequence is running.

### `turntable.cancelSequence()`

The same as `stop()`.
