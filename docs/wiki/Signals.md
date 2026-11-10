## Signals

How far the next signal is, and whether the train is being held by one.

Everything here is measured to the [stop line](Glossary#stopline-glossary), the spot a train parks at
whatever color the signal shows, so all three calls measure to the same place.

Query range is 256 blocks. Past that you get [nil](Glossary#nil-glossary), which means "nothing in range", not
"clear forever".

A derailed train reads [nil](Glossary#nil-glossary) too, because there is no rail ahead of it to search
along.

Signals only stop a [schedule](Schedule) driven train. Under [manual control](Manual-control) the
train runs through reds.

### `train.getGapToRed()`

<a id="stopline-explained"></a>Blocks from the nose to the [stop line](Glossary#stopline-glossary) of the next red ahead. [Nil](Glossary#nil-glossary) when there is
no red inside 256 blocks.

A parked train searches both ways. With no direction of travel there is no "ahead", so it
reports the nearer red on either side.

```lua
local gap = train.getGapToRed()
print(gap and string.format("red in %.1f blocks", gap) or "clear ahead")
```

```result
red in 42.5 blocks
```

The `and` and `or` pair is a [nil](Glossary#nil-glossary) check. Written out:

```lua
local gap = train.getGapToRed()
if gap then
    print(string.format("red in %.1f blocks", gap))
else
    print("clear ahead")
end
```

### `train.getDisToSignal(state)`

The same measurement, with a choice of which signals count. `state` is optional.

| `state` | Finds |
| --- | --- |
| left out | The nearest signal, whatever color it shows |
| `"red"` | The next red only, the same as `getGapToRed()` |

Any other string throws an error. There is no third option.

#### Both return [nil](Glossary#nil-glossary) when nothing is in range.

```lua
local nearest = train.getDisToSignal()
local red = train.getDisToSignal("red")

if nearest and red and nearest < red then
    print("a green one first")
end
```

```result
a green one first
```

### `train.getNextSignal()`

The nearest signal ahead, whatever color, as a table. [Nil](Glossary#nil-glossary) when none is in range.

- `id`, the signal's id, the same one `loconautics.getSignals()` lists
- `distance`, blocks from the nose to the [stop line](Glossary#stopline-glossary)
- `state`, `"red"`, `"yellow"`, `"green"` or `"invalid"`
- `type`, `"block"` or `"chain"`
- `block`, the id of the block it guards

It replaces the two step check of `getDisToSignal()` plus a color guess. Yellow only appears on chain
signals, see [Network](Network).

```lua
local sig = train.getNextSignal()
if sig and sig.state ~= "green" and sig.distance < 60 then
    print(string.format("%s signal in %.0f blocks", sig.state, sig.distance))
end
```

```result
red signal in 42 blocks
```

### `train.isHeldAtRed()`

True while the train is stopped and waiting for a red to clear.

Not the same as `getGapToRed()` returning a small number. A red 5 blocks ahead of a moving train
is not a hold. This only turns true once the train has stopped for it.

```lua
if train.isHeldAtRed() then
    print("waiting on the signal")
end
```

To react without checking in a loop, use the events instead:

```lua
local _, id, gap = os.pullEvent("train_signal_red")
print(string.format("held at red, %.1f blocks short", gap))

os.pullEvent("train_signal_clear")
print("signal clear")
```

Those two events only fire for [schedule](Schedule) driven trains, the same as the hold itself.
