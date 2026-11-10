## Examples

### A four way turntable

```lua
local turntable = peripheral.find("loconautics:turntable_bearing")
local legs   = { north = 0, east = 90, south = 180, west = 270 }

local function goTo(name)
    local target = legs[name]
    if not target then error("no leg called " .. name) end
    turntable.turnTo(target)
    repeat os.sleep(0.05) until not turntable.isTurning()
    print("facing " .. name)
end

goTo("south")
```

### Wait for a turn, with a timeout

A turn does not finish if rotation stops, so add a timeout.

```lua
local function turn(degrees, timeoutSeconds)
    turntable.turnBy(degrees)
    local deadline = os.clock() + (timeoutSeconds or 30)
    while turntable.isTurning() do
        if os.clock() > deadline then
            turntable.stop()
            return false, "timed out with " ..
                string.format("%.1f", turntable.getRemainingDegrees()) .. " degrees left"
        end
        os.sleep(0.05)
    end
    return true
end

local ok, err = turn(180, 20)
if not ok then print(err) end
```

### Park the plate before letting a train on

```lua
local function safeToCross()
    local s = turntable.getState()
    return s.assembled and s.settled and not s.turning
end

while not safeToCross() do os.sleep(0.1) end
redstone.setOutput("back", true)      -- release the signal
```

### Drive an `await` sequence from Lua

```lua
turntable.setInstructions({
    { type = "turn_angle", value = 90 },
    { type = "await" },
    { type = "turn_angle", value = 90 },
    { type = "end" },
})
turntable.setTrigger("pulse")

turntable.runSequence()
repeat os.sleep(0.05) until turntable.getCurrentInstruction() == 2

print("first quarter done, waiting for the go ahead")
os.pullEvent("redstone")
turntable.resume()
```
