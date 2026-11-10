## Consist wide cargo

The same cargo calls without an index, run over every carriage at once.

```lua
train.getItemCount()
train.getItemCount(itemId)
train.getItems()
train.getFluidAmount()
train.getFluidAmount(fluidId)
train.getFluids()
```

Leave out the index to get the whole train. `train.getItemCount()` covers every carriage, and
`train.getCarriageItemCount(2)` covers only the second one.

Pass an id to count one thing, or leave it out for the total.

Ids always include the mod name. `"minecraft:coal"`, never `"coal"`. Fluid ids are fluid type
ids, so water is `"minecraft:water"` and a modded fuel uses its own mod name.

```lua
print("coal on board: " .. train.getItemCount("minecraft:coal"))
print("everything on board: " .. train.getItemCount())
```

```result
coal on board: 640
everything on board: 1284
```

`getItems()` and `getFluids()` are lookup tables, so use `pairs` on them, the same as the per
carriage versions.

```lua
for id, data in pairs(train.getFluids()) do
    print(string.format("%s: %d / %d mB", id, data.amount, data.capacity))
end
```

```result
minecraft:water: 8000 / 16000 mB
minecraft:lava: 2000 / 8000 mB
```

An item count is a plain number, but a fluid entry is a table with `amount` and `capacity`. That is
the difference to remember:

```lua
for id, count in pairs(train.getItems()) do
    print(id .. " x" .. count)
end
```

```result
minecraft:coal x640
minecraft:iron_ingot x644
```

Wait for a load:

```lua
print("loading")
repeat os.sleep(1) until train.getItemCount("minecraft:coal") >= 64
print("full, departing")
train.resumeSchedule()
```
