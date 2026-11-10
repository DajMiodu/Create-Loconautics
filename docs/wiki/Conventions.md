## Conventions

These rules apply to the whole API.

### Units

| Thing | Unit | Note |
| --- | --- | --- |
| Speed | blocks per tick | Multiply by 20 for blocks per second |
| Distance | blocks | Same as meters here |
| Angle | degrees | Never radians |
| Mass | kilopixelgrams | A per block weight, not kilograms |
| Time | ticks | 20 to the second |

Speed is signed. Positive is forward along the rail, negative is reverse.

Carriage indices are 1 based, matching Lua. Asking for carriage 0 errors.

Carriage 1 is one end of the train, not the car holding the axle. The order comes from following
the couplers to one end and back, so index 1 is whichever end that reached. It does not change while
the train stays coupled the same way, but recoupling can turn it around, so read the cars instead of
assuming which end you are on.

#### Anything with a decimal point in this guide is printed with [`string.format`](Glossary#stringformat-glossary).
`%d` is a whole number, `%s` is text, `%.1f` is one decimal place.

### [Nil](Glossary#nil-glossary) means "no answer", not zero

<a id="nil-explained"></a>A call that finds nothing returns [nil](Glossary#nil-glossary). No station ahead, no signal in range, no title on
this leg.

Test for it before doing any maths. Comparing [nil](Glossary#nil-glossary) with a number ends the program.

```lua
local gap = train.getGapToRed()
if gap and gap < 20 then
    train.setTargetSpeed(0.2)
end
```

Lua stops at the first false value in an `and` chain, so `gap < 20` never runs when `gap` is
[nil](Glossary#nil-glossary). That is why the two tests are written in that order.

[Nil](Glossary#nil-glossary) never means "that went wrong". A call that fails throws an error instead, which is
the next rule.

### Errors are thrown, not returned

Asking for a station that does not exist, or steering without a
[control lease](Glossary#controllease-glossary), raises a Lua error with a message saying what went wrong.

Messages state the fix as well as the problem.

### `pcall` when you want to handle an error instead of crashing

<a id="pcall-explained"></a>`pcall` runs a function and catches any error it throws. It returns true and the result on
success, false and the error message on failure.

```lua
local ok, dist = pcall(train.getDisToStation, "Central")
if not ok then
    print("cannot route: " .. dist)
end
```

```result
cannot route: no route ahead of the train
```

Pass the function, do not call it. Write `pcall(train.getDisToStation, "Central")`, never
`pcall(train.getDisToStation("Central"))`. The second one runs the call first and crashes before
`pcall` sees it.

The second value means two different things. It is the answer when `ok` is true and the error
text when `ok` is false, which is why the example prints `dist` as a string.

Do not wrap everything. Reading `getStationName()` every tick needs no `pcall`, a
[nil](Glossary#nil-glossary) check is enough. Use it for the calls that can really fail: routing, carriage
indices, and anything that needs a lease.
