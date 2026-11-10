# Loconautics CC API

Loconautics exposes each train to ComputerCraft. A computer inside a cart can read the
physics state, drive the train, wait for station and signal events, and write a
[schedule](Schedule) from Lua.

<a id="bearingaxle-explained"></a>The API lives on the [Bearing Axle](Glossary#bearingaxle-glossary) block. One axle means one train, so a <a id="consist-explained"></a>[consist](Glossary#consist-glossary) with one
locomotive has one handle, however many carriages are coupled behind it.

## Contents

**Start here**

- [Quick start](Quick-start)
- [Getting a handle](Getting-a-handle)
- [Conventions](Conventions)

**Reading the train**

- [Identity](Identity)
- [Motion](Motion)
- [State](State)
- [Station](Station)
- [Signals](Signals)
- [Track ahead](Track-ahead)
- [Orientation](Orientation)
- [World position](World-position)
- [Carriages](Carriages)
- [Consist wide cargo](Consist-wide-cargo)

**Driving the train**

- [Drivetrain](Drivetrain)
- [Who owns the train](Who-owns-the-train)
- [Manual control](Manual-control)
- [Braking](Braking)
- [Parking](Parking)
- [Routing and distance](Routing-and-distance)

**Schedules**

- [Schedule](Schedule)
- [Writing a schedule](Writing-a-schedule)

**Doors and body**

- [Doors](Doors)
- [Body offset](Body-offset)

**Links**

- [Frequencies](Frequencies)
- [Redstone links](Redstone-links)

**The whole railway**

- [Network](Network)
- [Controlling the network](Controlling-the-network)

**Reacting to changes**

- [Events](Events)

**Programs and reference**

- [Examples](Examples)
- [Full programs](Full-programs)
- [Known behavior](Known-behavior)
- [Glossary](Glossary)

**Other guides**

- [Turntable Bearing](Turntable)
- [Datapacks](Datapacks)
