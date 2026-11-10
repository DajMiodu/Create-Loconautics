## Redstone links

<a id="redstonelink-explained"></a>The [Bearing Axle](Glossary#bearingaxle-glossary)'s own left and right steering inputs, read from the block itself.

These are not the same as [Frequencies](Frequencies). Those are channels you bind and send on.
These four are the two steering inputs the axle already has, and they are read only.

Link signals and direct redstone are reported separately, two calls per side, so you can tell a
wireless signal from a lever wired to the block.

None of the four need a lease, but using `steer()` on the result does.

### `train.getLeftLink()`

### `train.getRightLink()`

True when that side has a signal from a Create [frequency](Glossary#frequency-glossary) link.

### `train.getLeftRedstone()`

### `train.getRightRedstone()`

True when that side has direct redstone rather than a link signal.

### Steering from the links

A lever or a link at a junction selects the route, and the computer
converts it to a `steer()` call.

```lua
train.setManualControl(true)

while true do
    if train.getLeftLink() then
        train.steer(-1)
    elseif train.getRightLink() then
        train.steer(1)
    else
        train.steer(0)
    end
    os.sleep(0.05)
end
```

### Either source, one answer

To treat a lever and a link the same:

```lua
local left = train.getLeftLink() or train.getLeftRedstone()
local right = train.getRightLink() or train.getRightRedstone()

train.steer(left and -1 or right and 1 or 0)
```

Both sides on at once is a wiring error, and the code above silently picks left. To report it:

```lua
if left and right then
    print("both inputs high, check the wiring")
end
```
