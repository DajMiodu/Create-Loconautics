## Writing a schedule

A computer can build and load a [schedule](Schedule) directly. The train needs an analog controller,
exactly one transmission, a usable Bearing Axle, and matching [frequencies](Glossary#frequency-glossary) between them.

A missing analog controller has its own message, `this cart has no analog controller, a schedule
has nothing to drive`. Everything else throws one of six keys: `no_transmission`,
`many_transmissions`, `no_bearing_axle`, `invalid_bearing_axle`, `transmission_mismatch`,
`bearing_axle_mismatch`.

A schedule can load and still be held. `setSchedule` does not check for a driver, and it does not
check that the transmission is next to the axle. So a train with no lit blaze burner and no seated
conductor takes the schedule and is then held on `no_driver`. Read
[`getHeldReason()`](Schedule#traingetheldreason) after loading one.

### `train.setSchedule(table)`

The table has two keys.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `cyclic` | boolean | `true` | Go back to entry 1 after the last one |
| `entries` | list | required | The stops, in order |

Each entry has:

| Key | Type | Meaning |
| --- | --- | --- |
| `instruction` | string | Instruction type id |
| `data` | table | Fields for that instruction |
| `items` | list | Item ids for instructions that take item filters |
| `conditions` | list | Columns of wait conditions, see below |

`conditions` is a list of columns, and each column is a list of conditions. Every condition in a
column has to pass at the same time. If any one column passes, the train leaves. This is the same
layout the [schedule](Schedule) screen shows, one column per stack of cards.

Type ids can be written with or without the mod name. `"destination"` and `"create:destination"` both
work.

#### Instructions

| Id | Data | Notes |
| --- | --- | --- |
| `create:destination` | `Text` | Station name to match, `*` matches anything, at most three of them |
| `create:throttle` | `Value` | Percent, 0 to 100 |

#### Conditions

| Id | Data | Items |
| --- | --- | --- |
| `create:delay` | `Value`, `TimeUnit` (0 ticks, 1 seconds, 2 minutes) | |
| `create:time_of_day` | `Hour`, `Minute`, `Rotation` | |
| `create:item_threshold` | `Threshold`, `Operator`, `Measure` | one item filter |
| `create:fluid_threshold` | `Threshold`, `Operator`, `Measure` | one bucket or fluid item |
| `create:redstone_link` | `Inverted` | two frequency items |
| `create:player_count` | `Count`, `Exact` | |
| `create:idle` | | |
| `create:unloaded` | | |
| `create:powered` | | |

`Threshold` is a string, not a number. It is stored as text, so `Threshold = "64"` works and
`Threshold = 64` reads back as 0.

`Operator` is 0 for more than, 1 for less than, 2 for exactly. The first two do not include the
number itself. `Measure` is 0 for items and 1 for stacks, and a stack only counts when it is full.

`Inverted` is 1 to pass when there is no signal, 0 to pass when there is one. `Exact` works the
opposite way to its name: 0 means exactly that many players, 1 means that many or more.

`Rotation` on `create:time_of_day` sets how often the time comes round. 0 is every 24 hours, then 12,
6, 4, 3, 2, 1, 0:45, 0:30 and 0:15 at index 9. `Hour` is 0 to 23 and `Minute` is 0 to 59.

`Carriage` works on every condition, not only the two thresholds. It is a carriage index counting
from 0, and leaving it out means the whole [consist](Glossary#consist-glossary).

```lua
train.setSchedule({
    cyclic = true,
    entries = {
        {
            instruction = "destination",
            data = { Text = "Central" },
            conditions = {
                { { type = "delay", data = { Value = 10, TimeUnit = 1 } } },
            },
        },
        {
            instruction = "destination",
            data = { Text = "North Yard" },
            conditions = {
                {
                    { type = "item_threshold", data = { Threshold = "64", Operator = 0 },
                      items = { "minecraft:coal" } },
                },
                { { type = "delay", data = { Value = 2, TimeUnit = 2 } } },
            },
        },
    },
})
```

That second stop leaves once there are more than 64 coal on board, or after two minutes, whichever
happens first. `Operator = 0` means more than, not 64 or more, so use 63 if 64 should be enough.

### `train.getSchedule()`

Reads the running [schedule](Schedule) back in the same shape `setSchedule` takes. Data fields come
back as numbers and strings, and item slots as a list of item ids, with an empty string for an empty
slot.

It throws `this train has no schedule, call setSchedule first` if none is loaded. Check
`getScheduleSize() > 0` first if you are not sure, or wrap it in `pcall`.

### `train.clearSchedule()`

Removes the [schedule](Schedule) and its saved progress.

```lua
if train.getScheduleSize() > 0 then
    local s = train.getSchedule()
    for i, entry in ipairs(s.entries) do
        print(i .. ": " .. entry.instruction .. " " .. (entry.data.Text or ""))
    end
end
```
