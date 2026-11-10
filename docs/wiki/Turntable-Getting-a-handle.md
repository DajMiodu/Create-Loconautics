## Getting a handle

Place a computer or an advanced modem adjacent to the bearing block, not the plate. The plate
is in a sub level, which a peripheral cable cannot reach.

```lua
local turntable = peripheral.find("loconautics:turntable_bearing")
if not turntable then error("no turntable bearing attached") end
```

With a wired modem, use the printed network name instead:

```lua
local turntable = peripheral.wrap("turntable_bearing_0")
```
