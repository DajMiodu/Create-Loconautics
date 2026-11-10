## State snapshot

### `turntable.getState()`

Returns every readable value that has no side effect in one table, using the keys shown below. This is cheaper than separate calls in
a polling loop.

```lua
local s = turntable.getState()
--[[
  assembled = true,       angle = 90.0,        turning = false,
  settled = true,         powered = true,      speed = 32.0,
  cogSpeed = 32.0,        speedOverride = 0.0, stress = 128.0,
  degreesPerTick = 9.6,   remaining = 0.0,     trigger = "pulse",
  instruction = 0,        scheduleDriven = false,
  occupied = false,       aligned = false,     error = nil,
]]
```
