## Frequencies

<a id="frequency-explained"></a>A [frequency](Glossary#frequency-glossary) is a Create [redstone link](Glossary#redstonelink-glossary) channel, named by two item ids,
the same two slots the in game link screen uses. Bind one to a short slot name and the train can send
and receive a signal strength on it.

You choose the slot name. `"door_signal"`, `"alarm"`, anything. It is only a local label, the
network never sees it.

The two item ids are the address. Any Create link anywhere in the world using the same two items
is on the same channel.

Strength runs 0 to 15, the same range as redstone.

A slot receives its own signal. One slot sends and listens on the same channel, so
`getFrequencyState` reads back your own `setFrequencyState`. Two Create links placed side by side do
the same. Use two slots on two channels if you want one way.

### `train.setFrequency(slot, itemId1, itemId2)`

Binds `slot` to the channel named by those two items.

Binding a slot again replaces the old binding, it does not add a second one.

An unknown item id throws an error instead of binding to nothing, so a typo stops the program
rather than failing silently.

```lua
train.setFrequency("door_signal", "minecraft:green_dye", "minecraft:white_dye")
```

### `train.getFrequencyState(slot)`

The strength received on that slot, 0 to 15.

### `train.setFrequencyState(slot, strength)`

Sends a strength on that slot, 0 to 15.

### `train.clearFrequency(slot)`

Unbinds the slot and removes it from the network.

Every slot is unbound when the lease is released, so a program that calls `release()` does not
need this.

All three error with `slot "x" has no frequency bound` if `setFrequency` was never called for
that name.

### Sending a pulse

```lua
train.setFrequency("door_signal", "minecraft:green_dye", "minecraft:white_dye")

train.setFrequencyState("door_signal", 15)
os.sleep(1)
train.setFrequencyState("door_signal", 0)
```

### Waiting for a lever somewhere else

A link sender at a platform turns on when the platform is clear. The train waits for it.

```lua
train.setFrequency("platform", "minecraft:blue_dye", "minecraft:white_dye")

print("waiting for platform")
repeat os.sleep(0.5) until train.getFrequencyState("platform") > 0
print("clear")
```

```result
waiting for platform
clear
```

### Alarm signal

The strength encodes severity, so one channel is enough.

```lua
train.setFrequency("alarm", "minecraft:red_dye", "minecraft:white_dye")

if train.isDerailed() then
    train.setFrequencyState("alarm", 15)
elseif train.getStressRatio() > 1.0 then
    train.setFrequencyState("alarm", 7)
else
    train.setFrequencyState("alarm", 0)
end
```
