## Body offset

<a id="bogey-explained"></a>The carriage body normally sits straight on its [bogeys](Glossary#bogey-glossary). These functions move it off that
position. They turn it around the plot anchor and slide it along its own axes, and nothing else
moves with it. The [bogeys](Glossary#bogey-glossary), the wheel plates and the rail all stay where they were.
Use it to adjust a build that came out slightly off center, not to drive.

There is one setter and one getter per axis, and each one only changes its own value, so you never
have to set the other five again. Angles are in degrees, offsets are in blocks, and all six start at
zero on a train nobody has changed.

Angles take -360 to 360 and offsets take -32 to 32 blocks, which is already longer than any carriage.
Outside that range, or with a value that is not a real number, the setter throws an error instead of
clamping. A bad loop then stops at the failing call instead of moving the body a thousand
blocks away.

A setter says where the body should end up, it does not move it there at once. The body moves towards
the value you set, a fraction of the remaining distance each tick, so a jump from 0 to 180 appears as
a turn instead of an instant change. `setBodyLerpSpeed` sets that fraction, and the default is
0.2, which takes about a second. The getters read the value you asked for, not where the body has got
to, so reading, adding and writing still works part way through a move.

The values are stored on the train and saved with it, and they are already in place after a reload
instead of sliding in from zero. A derailed train ignores them, because a derailed car follows its
physics body and not the rail, and it uses them again as soon as it is back on the rails.

### `train.setBodyRoll(degrees)` and `train.getBodyRoll()`

Side tilt, turning around the car's own length. Positive leans one way, negative the other.

```lua
train.setBodyRoll(3)
print(train.getBodyRoll())   --> 3

train.setBodyRoll(400)       --> error: roll must be between -360 and 360
```

### `train.setBodyPitch(degrees)` and `train.getBodyPitch()`

Nose up or down, turning around the car's side to side axis.

```lua
train.setBodyPitch(-2)       -- nose down a couple of degrees
print(train.getBodyPitch())  --> -2
```

### `train.setBodyYaw(degrees)` and `train.getBodyYaw()`

Direction, turning around the up axis. 180 turns the body around on its own
[bogeys](Glossary#bogey-glossary).

```lua
train.setBodyYaw(180)
print(train.getBodyYaw())    --> 180
```

### `train.setBodyX(blocks)` and `train.getBodyX()`

Slides the body along the car. Positive moves towards the end it was built facing.

```lua
train.setBodyX(-1)           -- one block back
print(train.getBodyX())      --> -1
```

### `train.setBodyY(blocks)` and `train.getBodyY()`

Lifts the body, positive up.

```lua
train.setBodyY(0.5)          -- half a block of extra ride height
print(train.getBodyY())      --> 0.5

train.setBodyY(100)          --> error: y must be between -32 and 32
```

### `train.setBodyZ(blocks)` and `train.getBodyZ()`

Slides the body sideways, off to one side of the rail.

```lua
train.setBodyZ(0.2)          -- a fifth of a block to one side
print(train.getBodyZ())      --> 0.2
```

### `train.setBodyOffset(x, y, z)`

All three offsets at once, for when you already know the full position. There is no matching getter,
read the three values back with `getBodyX()`, `getBodyY()` and `getBodyZ()`.

```lua
train.setBodyOffset(-1, 0.5, 0.2)
print(train.getBodyX(), train.getBodyY(), train.getBodyZ())   --> -1  0.5  0.2
```

### `train.setBodyLerpSpeed(speed)` and `train.getBodyLerpSpeed()`

How much of the remaining distance the body covers each tick, from 0 to 1. Anything outside that
range throws an error. 1 moves to the target in one tick, 0.2 is the default, 0.02 is very slow, and
0 holds the body where it is and ignores anything you set until you raise it again.

```lua
train.setBodyLerpSpeed(1)     -- no smoothing, move there now
train.setBodyYaw(180)

train.setBodyLerpSpeed(0.05)  -- slow, about five seconds per move
train.setBodyRoll(10)

print(train.getBodyLerpSpeed())   --> 0.05
```

The speed is saved with the train, the same as the six values.

### `train.isBodySettled()`

True once the body has reached every value you asked for. A setter returns on the tick you call it,
so nothing waits on its own. Use this when the next step needs the body to have stopped moving.

```lua
train.setBodyYaw(180)
repeat os.sleep(0.05) until train.isBodySettled()
print("round the other way")
```

Put it in a function if you do it more than once:

```lua
local function moveAndWait(fn, value)
    fn(value)
    repeat os.sleep(0.05) until train.isBodySettled()
end

moveAndWait(train.setBodyYaw, 180)
moveAndWait(train.setBodyY, 0.5)
```

It turns true as soon as the last axis arrives, so a program that sets a value and checks straight
after will not pass the test too early. Two cases never settle and will loop forever: a lerp speed of
0, and another program on the same train setting a different target.

### `train.resetBody()`

Sets all six back to zero, moving smoothly like any other change. It is safe to call on a train that
was never changed. Call it at the end of any program that animates the body, or the last position it
set stays on the car.

```lua
train.setBodyRoll(5)
train.resetBody()
print(train.getBodyRoll())   --> 0, the body itself takes a moment to catch up
```

The three angles are measured against the blocks as they were built, so a number means the same
thing whichever way the train is pointing. The offsets use those same axes.

### Starting out

Put a computer in the cart and set one value at a time, starting with small numbers. Everything here
takes effect as soon as you set it.

```lua
local train = loconautics.getTrain()

train.setBodyY(0.25)    -- quarter block up
train.setBodyX(-1)      -- one block back along the car
train.setBodyYaw(180)   -- body turned around, bogeys stay put

train.resetBody()       -- back to the start
```

Each of those moves into place over about a second instead of jumping, and the three calls do not
wait for each other. Each one starts when you call it, from wherever the body is, so all three run at
the same time. Use `isBodySettled()` if you want one to finish before the next starts. If you are
trying to find the right number and the wait gets in the way, turn the smoothing off with
`train.setBodyLerpSpeed(1)` while you work, then set it back.

Use small angles. One degree of roll is visible on a long carriage, so
start at 1 and increase.

### Turning a cab around

A locomotive built facing the wrong way along the rail. Yaw fixes it without rebuilding, and the
change is kept across reloads and rerails.

```lua
local train = loconautics.getTrain()
train.setBodyYaw(180)
```

### Raising a body that sits low

Read the current value, add to it, write it back. Do this for any "a bit more" change.

```lua
local train = loconautics.getTrain()
train.setBodyY(train.getBodyY() + 0.05)
```

### A crooked freight car

A little roll and a little yaw, so the wagon looks like its load has shifted. Both are saved, so it
keeps the lean after a reload.

```lua
local train = loconautics.getTrain()
train.setBodyRoll(3)
train.setBodyYaw(1.5)
```

### Leaning into the curves

The body position can change every tick, so it can be animated. This reads the direction, works out
how far it turned since the last reading, and rolls the body that way. The `+ 540` and `% 360` are
there because the direction wraps from 359 back to 0, and without them the car would flip over every
time it passed south.

```lua
local train = loconautics.getTrain()
local lastYaw = train.getYaw()

train.setBodyLerpSpeed(0.15)

while true do
    os.sleep(0.1)
    local yaw = train.getYaw()
    local turn = (yaw - lastYaw + 540) % 360 - 180
    lastYaw = yaw
    train.setBodyRoll(-turn * 0.5)
end
```

The lerp speed smooths the motion, so the loop can pass uneven readings every tenth of a second and
the car still leans evenly. Raise the speed for a car that leans quickly, lower it for a heavy one
that leans slowly.

Do not confuse the two names. `getYaw()` is the train's real direction on the rail, `getBodyYaw()` is
only the extra turn you asked for.

### Kneeling at a station

Lowers the body while the doors are open and raises it when the train leaves, like a tram kneeling
at the platform.

```lua
local train = loconautics.getTrain()
train.setBodyLerpSpeed(0.08)

while true do
    local event = os.pullEvent()
    if event == "train_arrived" then
        train.setBodyY(-0.3)
    elseif event == "train_departed" then
        train.resetBody()
    end
end
```

There is no animation loop here. One call sets the parked height, one sets zero, and the smoothing
handles the motion in between.

### Everything in one program

Reads the six values, moves the body along each axis in turn so you can see what each one does, then
resets it. Run this on a computer in a parked cart.

```lua
local train = loconautics.getTrain()
train.setBodyLerpSpeed(0.2)

local function wait()
    repeat os.sleep(0.05) until train.isBodySettled()
end

local function show(what)
    print(string.format("%-8s roll %.1f  pitch %.1f  yaw %.1f  x %.2f  y %.2f  z %.2f",
        what,
        train.getBodyRoll(), train.getBodyPitch(), train.getBodyYaw(),
        train.getBodyX(), train.getBodyY(), train.getBodyZ()))
end

show("start")

train.setBodyRoll(5)
show("roll")
wait()

train.setBodyPitch(-5)
show("pitch")
wait()

train.setBodyYaw(180)
show("yaw")
wait()

train.setBodyOffset(-1, 0.5, 0.2)
show("offset")
wait()

train.setBodyY(train.getBodyY() + 0.5)
show("higher")
wait()

train.resetBody()
show("reset")
```

`show` prints the values you asked for, so it reads the target as soon as you set it. Check the cart
itself for the actual position.
