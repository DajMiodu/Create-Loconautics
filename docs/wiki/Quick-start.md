## Quick start

Put a computer inside the cart, then run this. It reads the train, parks it at a station and gives control
back. The station name is an example, use one from your own railway.

```lua
local train = loconautics.getTrain()
print(train.getName(), train.getStationState())

train.parkAtStation("Central")
repeat os.sleep(0.2) until not train.isParking()
train.release()
```

The first line is the handle every other example in this guide starts from, see
[Getting a handle](Getting-a-handle). Driving needs a built [drivetrain](Drivetrain), reading does not.

### Finding what you need

| I want to | Go to |
| --- | --- |
| Get a train to talk to | [Getting a handle](Getting-a-handle) |
| Know how units, nil and errors work | [Conventions](Conventions) |
| Read speed, slope, mass or power | [Motion](Motion) |
| Know if it is derailed, braked or held | [State](State) |
| Know where it is stopped, and for how long | [Station](Station) |
| Measure the distance to the next signal | [Signals](Signals) |
| Find the end of the track, a bend, a hill or a sub level track ahead | [Track ahead](Track-ahead) |
| Read its direction, tilt or world position | [Orientation](Orientation), [World position](World-position) |
| Count cargo, fluid and passengers | [Carriages](Carriages), [Consist wide cargo](Consist-wide-cargo) |
| Drive by hand | [Drivetrain](Drivetrain), [Who owns the train](Who-owns-the-train), [Manual control](Manual-control) |
| Stop the train | [Braking](Braking) |
| Send it to a station or a point on the track | [Parking](Parking) |
| Get the distance or turns to a station | [Routing and distance](Routing-and-distance) |
| Read or write a schedule | [Schedule](Schedule), [Writing a schedule](Writing-a-schedule) |
| Open the doors | [Doors](Doors) |
| Tilt or shift the carriage body | [Body offset](Body-offset) |
| Send or receive Create link signals | [Frequencies](Frequencies), [Redstone links](Redstone-links) |
| List every train, signal and track piece | [Network](Network) |
| Run signals, points and trains from a desk | [Controlling the network](Controlling-the-network) |
| Wait for an arrival, a derail or a red | [Events](Events) |
| Copy a working program | [Examples](Examples), [Full programs](Full-programs) |
| Look up a word | [Glossary](Glossary) |
