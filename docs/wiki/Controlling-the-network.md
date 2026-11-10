## Controlling the network

These calls change things. They work from any computer with no train attached, so a control room can run the
whole railway.

They are on by default. Set `compat.computercraft.remoteControl` to `false` in the server config to keep
control to computers inside the train, and every call in this part then throws.

Everything a computer sets is given back when it stops. Breaking the computer, turning it off or
unloading its chunk releases the trains it stopped, the signals it held and the points it set. A crashed
control room does not leave a signal red for ever.

Only loaded trains can be driven. A train in a chunk that is not loaded cannot be reached.

### `loconautics.getTrainById(id)`

The same handle `loconautics.getTrain()` gives, for any loaded train. Take the id from
[`getTrains`](Network#loconauticsgettrainsfilter). Every `train.` call in this guide works on it, driving,
schedules, doors, braking, frequencies and the carriage reads.

It throws when no loaded train has that id, and when the train has no Bearing Axle.

```lua
local train = loconautics.getTrainById(loconautics.getTrains("Express 01")[1].id)
train.setDoors(true)
print(train.getTargetStationName())
```

```result
Central
```

The [control lease](Glossary#controllease-glossary) works as it does for any handle: only one at a time, and it is
dropped when the computer stops.

### `loconautics.getTurntableById(id)`

The turntable peripheral for a turntable, by the `id` that `getTurntables()` lists. It has every call of a
turntable bearing, `turnTo`, `getAngle`, `isSettled` and the rest, so a desk can turn one it is not wired to.

### `loconautics.stopTrains([filter [, emergency]])`

Starts a [stop](Braking) on every train whose name matches `filter`, `*` for all when left out. With
`emergency` true they brake as hard as they can. Returns how many trains it stopped.

```lua
print(loconautics.stopTrains("*", true) .. " trains stopped")
```

```result
5 trains stopped
```

### `loconautics.releaseTrains()`

Gives back every train this computer stopped with `stopTrains`, and returns how many. A stopped train keeps
its brake on until then, so run this when the emergency is over.

### `loconautics.holdSignal(id [, side])`

Holds a signal red, as if a redstone signal powered it. `id` is a signal id from `getSignals()`. `side` is 1 or 2
for one side, and both sides when left out.

It only holds red. It cannot turn a signal green. A train already past the signal is not affected.

The hold shows in `getSignals()` as `held = true`, and `forcedRed` is true as well.

#### It lasts until released, until the computer stops, or until the world closes.

```lua
loconautics.holdSignal("3b91c0aa-0000-4000-8000-000000000001", 1)
```

### `loconautics.releaseSignal(id [, side])`

Lets go of a hold, same arguments.

### `loconautics.getHeldSignals()`

A list of `{id, side}` for every hold on the railway, from any computer.

### `loconautics.setPoint(junctionId, towardId)`

Sets which way trains go at a junction. `junctionId` is a junction from `getJunctions()` and `towardId` is the
`to` of one of its `edges`.

It steers any train that is choosing its own way. That includes steering by hand, `steer`, and a train
with no route. A schedule follows its planned route and ignores points.
The point stays set for every train until cleared. If the set way is not open to a train, for example it
arrives from the wrong side, that train chooses as it normally would.

It throws when the node is not a junction, or no track leads from it to `towardId`.

```lua
local j = loconautics.getJunctions()[1]
loconautics.setPoint(j.id, j.edges[2].to)
```

### `loconautics.clearPoint(junctionId)`

Takes the point away, the junction goes back to normal steering.

### `loconautics.getPoints()`

A list of `{junction, toward}` for every point set on the railway.

### Sending the nearest parked train

Asks every parked train how far it is from a station and sends the closest. Use `release()` on the train once
the `train_parked` event arrives.

```lua
local station = "North Yard"
local best, bestDistance

for _, t in ipairs(loconautics.getTrains()) do
    if t.stationState == "stopped" and not t.target then
        local train = loconautics.getTrainById(t.id)
        local ok, distance = pcall(train.getDisToStation, station)
        if ok and distance and (not bestDistance or distance < bestDistance) then
            best, bestDistance = train, distance
        end
    end
end

if best then
    best.parkAtStation(station)
    print(string.format("sent %s, %.0f blocks away", best.getName(), bestDistance))
end
```

```result
sent Freight 03, 212 blocks away
```
