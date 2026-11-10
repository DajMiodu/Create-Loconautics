## Carriages

What is inside each car: cargo, fluid, passengers and block count.

Indices are 1 based and run to `getCarriageCount()`, counted from one end of the rake, see
[Conventions](Conventions#units). Carriage 1 is not promised to be the car with the axle in it.

Every call here throws an error on a bad index, including carriage 0. Limit loops with
`getCarriageCount()` to avoid it.

```
carriage 5 not found, this train has 3 carriages
```

### `train.getCarriage(index)`

Returns a handle for one carriage. Every method reads current state,
so the handle can be kept and `getCarriage` need not be called again to see a change.

It is an object, so call its methods. Write `c.getPassengerCount()`, not `c.passengers`.

| Method | Returns |
| --- | --- |
| `c.getIndex()` | The index you asked for |
| `c.getLength()` | Length of the carriage in blocks, the same measure `train.getLength()` adds up |
| `c.getWidth()` | Side to side size of the carriage in blocks |
| `c.getHeight()` | Height of the carriage in blocks, bogeys included |
| `c.getBlockCount()` | Non air blocks in the carriage |
| `c.getItemCount()` | Items in every inventory added up |
| `c.getItemCount(itemId)` | Just that item |
| `c.getItemCapacity()` | Total space in all slots |
| `c.getItems()` | `itemId` to count |
| `c.getFluidAmount()` | Fluid in mB |
| `c.getFluidAmount(fluidId)` | Just that fluid |
| `c.getFluidCapacity()` | Tank capacity in mB |
| `c.getFluids()` | `fluidId` to `{amount, capacity}` |
| `c.getPassengerCount()` | Seated players |
| `c.hasPassengers()` | `getPassengerCount() > 0` |

<a id="pairs-explained"></a>

```lua
local c = train.getCarriage(2)
while true do
    print(c.getIndex() .. ": " .. c.getItemCount() .. " items, " .. c.getPassengerCount() .. " aboard")
    for id, count in pairs(c.getItems()) do
        print("  " .. id .. " x" .. count)
    end
    os.sleep(5)
end
```

```result
2: 1284 items, 3 aboard
  minecraft:coal x640
  minecraft:iron_ingot x644
```

If the train loses that carriage, uncoupled or broken up, the next call on the handle throws the
same `carriage 2 not found` error `getCarriage` would.

Use [`pairs`](Glossary#pairs-glossary) here, not [`ipairs`](Glossary#ipairs-glossary). `c.getItems()` is keyed by item
id, not numbered 1, 2, 3, so `ipairs` would find nothing and stop straight away. Use `pairs` for any
lookup table like `items` or `fluids`, and `ipairs` for a plain list like the one `getWorldPosOf`
returns.

`pairs` gives no set order. Sort the keys first if you want the same order every time.

To work out how full a carriage is, without dividing by zero:

```lua
local c = train.getCarriage(1)
local capacity = c.getItemCapacity()
local fill = capacity > 0 and (c.getItemCount() / capacity) or 0
print(string.format("%.0f%% full", fill * 100))
```

```result
72% full
```

A carriage with no inventory has a capacity of 0. Dividing by it gives `nan`,
so test the capacity first.

### The per carriage getters

The same reads as the handle's methods, taking the index each time instead. They cost the same,
use whichever reads better in your program.

```lua
train.getCarriageItemCount(index)
train.getCarriageItemCount(index, itemId)
train.getCarriageItems(index)
train.getCarriageItemCapacity(index)
train.getCarriageFluidAmount(index)
train.getCarriageFluidAmount(index, fluidId)
train.getCarriageFluids(index)
train.getCarriageFluidCapacity(index)
train.getCarriageBlockCount(index)
train.getCarriagePassengerCount(index)
train.getCarriageHasPassengers(index)
```

#### They use the same 1 based index and throw the same error on a bad one.

Count everyone on board:

```lua
local total = 0
for i = 1, train.getCarriageCount() do
    total = total + train.getCarriagePassengerCount(i)
end
print(total .. " passengers")
```

```result
4 passengers
```
