## Examples

### Station loop with platform clearance

A train runs on a loop. Each platform has a link sender that turns on when the platform is clear. The
train waits for a minimum stop time, waits for every passenger to leave, then waits for the platform
to clear before letting the [schedule](Schedule) carry on.

```lua
local train = loconautics.getTrain()
local MIN_DWELL = 100

train.setFrequency("platform", "minecraft:green_dye", "minecraft:white_dye")

local function totalPassengers()
    local total = 0
    for i = 1, train.getCarriageCount() do
        total = total + train.getCarriagePassengerCount(i)
    end
    return total
end

while true do
    local _, id, station = os.pullEvent("train_arrived")
    if id == train.getId() then
        print("arrived at " .. station)
        train.pauseSchedule()

        repeat os.sleep(0.5) until train.getDwellTicks() >= MIN_DWELL
        repeat os.sleep(1) until totalPassengers() == 0

        print("waiting for platform clearance")
        repeat os.sleep(0.5) until train.getFrequencyState("platform") > 0

        print("clear, departing")
        train.resumeSchedule()
    end
end
```

### Safety monitor

Runs alongside anything else. Prints a status line every five seconds and sets an alarm
[frequency](Glossary#frequency-glossary) when anything is wrong.

<a id="stringformat-explained"></a>

```lua
local train = loconautics.getTrain()
train.setFrequency("alarm", "minecraft:red_dye", "minecraft:white_dye")

local function statusLoop()
    while true do
        local gap = train.getGapToRed()
        local rem = train.getRemainingToTarget()
        local name = train.getName()

        print(string.format("[%s] spd=%.2f stress=%.0f%% slope=%.1f gap=%s rem=%s",
            name ~= "" and name or train.getId():sub(1, 8),
            train.getSpeed(),
            train.getStressRatio() * 100,
            train.getSlopeAngle(),
            gap and string.format("%.1f", gap) or "clear",
            rem >= 0 and string.format("%.0f", rem) or "unknown"))

        local derailed = train.isDerailed()
        local overstressed = train.getStressRatio() > 1.0
        local level = train.isUpright(0.8)
        train.setFrequencyState("alarm", (derailed or overstressed or not level) and 15 or 0)

        if derailed then print("FAULT: derailed") end
        if overstressed then print("FAULT: overstressed") end
        if not level then
            print(string.format("FAULT: tilt roll=%.1f pitch=%.1f", train.getRoll(), train.getPitch()))
        end

        os.sleep(5)
    end
end

local function eventLoop()
    while true do
        local event, id, extra = os.pullEvent()
        if id == train.getId() then
            if event == "train_signal_red" then
                print(string.format("held at red, %.1f blocks short", extra))
            elseif event == "train_signal_clear" then
                print("signal clear")
            elseif event == "train_derailed" then
                print("DERAIL")
            end
        end
    end
end

parallel.waitForAny(statusLoop, eventLoop)
```

### Cargo manifest

```lua
local train = loconautics.getTrain()
train.setFrequency("cargo_full", "minecraft:orange_dye", "minecraft:white_dye")

local function manifest()
    print("=== " .. train.getCarriageCount() .. " carriages ===")
    print("items: " .. train.getItemCount() .. "  fluid: " .. train.getFluidAmount() .. " mB")

    local alert = false
    for i = 1, train.getCarriageCount() do
        local c = train.getCarriage(i)
        local count, capacity = c.getItemCount(), c.getItemCapacity()
        local fill = capacity > 0 and (count / capacity) or 0
        print(string.format("  carriage %d: %d items (%.0f%%)", i, count, fill * 100))
        if fill > 0.8 then
            alert = true
        end
        for id, qty in pairs(c.getItems()) do
            print("    " .. id .. " x" .. qty)
        end
        for id, data in pairs(c.getFluids()) do
            print(string.format("    %s: %d / %d mB", id, data.amount, data.capacity))
        end
    end
    train.setFrequencyState("cargo_full", alert and 15 or 0)
end

while true do
    manifest()
    os.sleep(10)
end
```

### Manual route driver

Drives to a station by hand, taking each junction from the step list, slowing down for reds, and
letting the parking code handle the last stretch.

```lua
local train = loconautics.getTrain()

local TARGET = "East Depot"
local CRUISE = 0.8
local APPROACH = 0.3

local function driveTo(name)
    train.setManualControl(true)
    train.setAccel(0.005)
    train.setTargetSpeed(CRUISE)

    while true do
        local ok, steps = pcall(train.getStepsToStation, name)
        if not ok then
            print("route lost: " .. steps)
            train.setTargetSpeed(0.0)
            train.setBrakeScale(1.0)
            os.sleep(2)
            train.release()
            return false
        end

        if #steps == 0 then
            train.setManualControl(false)
            train.parkAtStation(name)
            repeat os.sleep(0.2) until not train.isParking()
            train.release()
            return true
        end

        local step = steps[1]
        train.steer(step == "left" and -1 or step == "right" and 1 or 0)

        local dist = train.getDisToStation(name)
        local red = train.getDisToSignal("red")
        if (red and red < 20) or (dist and dist < 40) then
            train.setTargetSpeed(APPROACH)
        else
            train.setTargetSpeed(CRUISE)
        end

        if train.isBrakeHard() then
            print("handbrake pulled, stopping")
            train.setTargetSpeed(0.0)
            train.release()
            return false
        end

        os.sleep(0.1)
    end
end

print(driveTo(TARGET) and ("parked at " .. TARGET) or ("failed to reach " .. TARGET))
```

### Platform display

A computer at the station, wired to the axle over a modem, writing to a monitor.

```lua
local mon = peripheral.find("monitor")
local train = peripheral.find("loconautics:bearing_axle")

local function line(y, text)
    mon.setCursorPos(1, y)
    mon.write(text .. string.rep(" ", 30 - #text))
end

while true do
    local state = train.getStationState()
    local rem = train.getRemainingToTarget()
    local passengers = 0
    for i = 1, train.getCarriageCount() do
        passengers = passengers + train.getCarriagePassengerCount(i)
    end

    local name = train.getName()
    line(1, name ~= "" and name or ("Train " .. train.getId():sub(1, 8)))
    line(2, "dest: " .. (train.getTargetStationName() or "unknown"))

    if state == "stopped" then
        line(3, "at platform")
        line(4, "dwell: " .. math.floor(train.getDwellTicks() / 20) .. "s")
    elseif state == "stopping" then
        line(3, "arriving")
        line(4, "")
    elseif state == "departing" then
        line(3, "departing")
        line(4, "")
    else
        line(3, "in transit")
        line(4, rem >= 0 and string.format("next stop: %.0f blocks", rem) or "routing")
    end

    line(5, string.format("speed: %.2f b/t", math.abs(train.getSpeed())))
    line(6, "passengers: " .. passengers)

    if train.isDerailed() then
        line(7, "!! DERAILED !!")
    elseif train.isHeldAtRed() then
        line(7, "held at signal")
    elseif train.getStressRatio() > 1.0 then
        line(7, "overstressed")
    else
        line(7, "")
    end

    os.sleep(1)
end
```
