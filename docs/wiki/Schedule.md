## Schedule

Reading the schedule the train is running, whether a conductor, a blaze burner or a
computer loaded it. To write one, see [Writing a schedule](Writing-a-schedule).

#### All read only, no lease needed.
A train with no schedule is normal. These return [nil](Glossary#nil-glossary) or 0 instead of
throwing an error, so `getScheduleSize() > 0` is a cheap way to test whether there is one.

### `train.getScheduleState()`

Which part of the current leg the train is in. [Nil](Glossary#nil-glossary) if no schedule is
loaded.

| Value | Meaning |
| --- | --- |
| `"pre_transit"` | About to leave, planning the route |
| `"in_transit"` | Driving towards the stop |
| `"turntable"` | Waiting on a turntable to turn the train |
| `"post_transit"` | Arrived, checking the departure conditions |

`post_transit` is where a train waits. A schedule that looks stuck is usually here,
with a condition that never becomes true.

```lua
print(train.getScheduleState() or "no schedule")
```

```result
in_transit
```

### `train.getScheduleEntry()`

Which entry is running, 1 based. 0 when there is no schedule.

### `train.getScheduleSize()`

How many entries the schedule has. 0 when there is none, which makes it a good test
before calling anything else here.

```lua
if train.getScheduleSize() > 0 then
    print(string.format("stop %d of %d", train.getScheduleEntry(), train.getScheduleSize()))
end
```

```result
stop 2 of 4
```

### `train.getRemainingToTarget()`

How many blocks are left to the next scheduled stop.

It reads `-1`, not [nil](Glossary#nil-glossary), before the first leg is planned. Test for that instead of
for [nil](Glossary#nil-glossary).

```lua
local rem = train.getRemainingToTarget()
print(rem >= 0 and string.format("%.0f blocks to go", rem) or "still routing")
```

```result
310 blocks to go
```

### `train.isPaused()`

True while the schedule is paused, which is what `pauseSchedule()` does. A paused train
keeps its place and carries on from the same entry.

### `train.isCompleted()`

True once a non cyclic schedule has run its last entry. A cyclic one never completes, it
goes back to entry 1 instead.

### `train.getHeldReason()`

A short key saying why the schedule cannot carry on, [nil](Glossary#nil-glossary) when nothing is
wrong.

Only hardware faults appear here. The check runs every tick, and these are the twelve keys it can
return. A train waiting for a departure condition, a timed stop or a red signal reports
[nil](Glossary#nil-glossary), even though `isScheduleHeld()` is true.

| Key | Meaning |
| --- | --- |
| `"no_sub_level"` | The carriage's [sub level](Glossary#sublevel-glossary) is gone |
| `"not_loaded"` | The carriage's plot has no loaded chunks |
| `"no_controller"` | No analog controller in the carriage |
| `"no_driver"` | No lit blaze burner and no seated conductor |
| `"invalid_bearing_axle"` | No usable [Bearing Axle](Glossary#bearingaxle-glossary): missing, duplicated, or not on bogeys |
| `"no_transmission"` | The train has no transmission |
| `"transmission_not_touching"` | The transmission is not next to the axle |
| `"transmission_wrong_side"` | The transmission is on a side of the axle, not the shaft end |
| `"transmission_wrong_axis"` | The transmission shaft does not line up with the axle |
| `"no_rotation"` | No rotation reaches the transmission, the engine or the kinetic network is not supplying power |
| `"transmission_mismatch"` | Transmission and controller are on different [frequencies](Glossary#frequency-glossary) |
| `"bearing_axle_mismatch"` | Axle and controller are on different steering [frequencies](Glossary#frequency-glossary) |

`setSchedule` uses a similar but different set of keys, see
[Writing a schedule](Writing-a-schedule).

```lua
local why = train.getHeldReason()
if why then
    print("schedule stuck: " .. why)
end
```

```result
schedule stuck: no_transmission
```

### `train.pauseSchedule()`

### `train.resumeSchedule()`

Stop and restart the schedule without clearing it.

Resuming plans the route again. It restarts the current entry from `pre_transit`, so the train
works out a new route instead of using an old one.

Both throw `this train has no schedule, call setSchedule first` when none is loaded.

```lua
train.pauseSchedule()
print("waiting for cargo")
os.pullEvent("cargo_loaded")
train.resumeSchedule()
```

Check first when you are not sure there is a schedule:

```lua
if train.getScheduleSize() > 0 then
    train.pauseSchedule()
end
```
