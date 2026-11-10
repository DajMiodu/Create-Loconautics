## Known behavior

Behaviors that catch people out. Each row gives the short answer and links to the section that explains it.

| Behavior | What to do | Details |
| --- | --- | --- |
| `getAccel()` mostly reads one fixed number or 0 | Use `getAccelLimit()` for braking maths | [`getAccel`](Motion#traingetaccel) |
| A [frequency](Glossary#frequency-glossary) slot receives its own signal | Use two slots on two channels for one way | [Frequencies](Frequencies) |
| `parkAtStation` returns at once, `parkAtCoord` waits | Loop on `isParking()` after `parkAtStation` | [Parking](Parking) |
| A station name stays for about four blocks after the stop ends | Read `getStationState()` when you mean parked | [Station](Station) |
| `getCarriage` gives an object, not a table | Write `c.getPassengerCount()`, not `c.passengers` | [Carriages](Carriages) |
| Routing only looks forward | A target behind the train throws an error | [Routing and distance](Routing-and-distance) |
| `getDisToStation` is not a braking measurement | Let `parkAtStation` or `stopIn` do the braking | [Routing and distance](Routing-and-distance) |
| Signals only stop a schedule driven train | A train driven by hand or by a computer runs through reds and fires no signal events | [Signals](Signals) |
| Nothing returns [nil](Glossary#nil-glossary) to mean "that went wrong" | A failed call throws, a nil is a real answer | [Conventions](Conventions) |

<a id="stationmarker-explained"></a>Stopping near a marker parks you on it. A car that stops within three blocks of a station is
pulled onto the marker instead of being left where it stopped, so `stopped` always means the same
position. Stop further out than that and the train stays where it is and reads `running`.

The terminal is 51 characters wide. A longer print wraps and scrolls the screen, which looks like
flicker on a display that redraws. Pad and cut text to that width, and only redraw lines whose text
changed.

<a id="coroutine-explained"></a>A `mainThread` call waits with its own event filter, and ComputerCraft drops every event that
does not match while it waits. Key presses are lost during a read. Put the display and the key reader
in separate [coroutines](Glossary#coroutine-glossary) under `parallel.waitForAny`.
