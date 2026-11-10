## Glossary

Every term this guide uses, in one place. Terms in the text link here, and each entry links back to the
section that uses it. Read it when a term is unfamiliar, there is no need to read it in order.

### Lua and ComputerCraft basics

- <a id="pcall-glossary"></a>**pcall**: catches an error instead of letting it crash your program.
  Returns true and the result on success, false and the error message on failure. Pass the
  function and its arguments separately, `pcall(f, x)`, not `pcall(f(x))`. See it in
  [Conventions](Conventions#pcall-explained).
- <a id="nil-glossary"></a>**nil**: Lua's way of saying there is no value. It is not false and not
  zero, it is its own value. `if x then` treats nil the same as false, so test for nil directly when
  you need to tell the two apart. See it in [Conventions](Conventions#nil-explained).
- <a id="coroutine-glossary"></a>**coroutine**: code that can pause itself, usually at
  `os.pullEvent` or `os.sleep`, let other code run, then carry on from where it stopped. This is how
  one computer runs two loops that both wait. See it in [Known behavior](Known-behavior#coroutine-explained).
- **parallel.waitForAny**: runs several functions as coroutines at the same time and returns as soon
  as one of them finishes. Normally one loop reads state and one loop waits for events. See it in
  [Known behavior](Known-behavior#coroutine-explained).
- <a id="pullevent-glossary"></a>**os.pullEvent**: pauses the program until an event fires, then
  returns the event name and whatever came with it. Pass a name to wait for one kind of event, or
  pass nothing to get them all. See it in [Events](Events#pullevent-explained).
- <a id="stringformat-glossary"></a>**string.format**: builds a string from a template, filling in
  `%d` for a whole number, `%s` for text and `%.1f` for one decimal place. This guide uses it for
  anything with a decimal point. See it in [Examples](Examples#stringformat-explained).
- <a id="ipairs-glossary"></a>**ipairs**: loops over a plain list, 1, 2, 3 in order, stopping at the
  first gap. Use it for arrays, like the one `getWorldPosOf` returns. See it in
  [World position](World-position#ipairs-explained).
- <a id="pairs-glossary"></a>**pairs**: loops over every key in a table, in no set order. Use it for
  lookup tables like `items` or `fluids`. `ipairs` finds nothing in those. See it in
  [Carriages](Carriages#pairs-explained).
- <a id="peripheralfind-glossary"></a>**peripheral.find and peripheral.wrap**: how a computer reaches
  a block it is not inside. `find` searches the six touching faces and any wired modem network for a
  block of the given type. `wrap` takes one from a side you name. See it in
  [Getting a handle](Getting-a-handle#peripheralfind-explained).

### Loconautics and Create terms

- <a id="bearingaxle-glossary"></a>**Bearing Axle**: the block that turns a cart into a train. One
  axle covers the whole consist, and it is the block this API talks to. With no axle you cannot
  drive. See it in the [intro](Home#bearingaxle-explained).
- <a id="transmission-glossary"></a>**Transmission**: the block that sets how fast and which way the Bearing Axle
  turns. It sits on the shaft end of the axle. A train does not drive without a correctly placed one. See it in
  [Drivetrain](Drivetrain).
- <a id="consist-glossary"></a>**consist**: the whole coupled train, the locomotive and every
  carriage behind it. One handle drives all of it. Doors, name and mass cover the whole consist,
  never one car. See it in the [intro](Home#consist-explained).
- <a id="bogey-glossary"></a>**bogey**: the wheeled part under a carriage that rides on the rail. The
  carriage body sits on top of its bogeys, and body offset moves the body without moving them. See
  it in [Body offset](Body-offset#bogey-explained).
- <a id="sublevel-glossary"></a>**sub level**: the physics body a carriage is built as. **It is not a
  separate world. Its blocks are normal blocks in the same level, kept in a reserved plot far from
  the player, and a pose draws them where the train really is. Every carriage is its own sub level,
  which is why a computer has to be inside one to find its train. See it in
  [Getting a handle](Getting-a-handle#sublevel-explained).
- <a id="controllease-glossary"></a>**control lease**: permission for one computer to drive the
  train. Only one exists at a time, and taking a new one clears any value the last program set.
  Without it the four driving setters throw an error. See it in
  [Who owns the train](Who-owns-the-train#controllease-explained).
- <a id="frequency-glossary"></a>**frequency**: a Create redstone link channel, named by two item
  ids. Bind one to a slot name and the train can send and receive a strength from 0 to 15 on it. See
  it in [Frequencies](Frequencies#frequency-explained).
- <a id="redstonelink-glossary"></a>**redstone link**: the Bearing Axle's own left and right steering
  inputs, read from the block itself rather than through a frequency slot. Read only. See it in
  [Redstone links](Redstone-links#redstonelink-explained).
- **schedule**: runs a train on its own from a list of stops and conditions, with no computer needed.
  See [Schedule](Schedule) and [Writing a schedule](Writing-a-schedule).
- **manual control**: gives the throttle to a computer until it releases it. Only one of schedule
  or manual control drives at a time, and manual control wins while it is held. See
  [Who owns the train](Who-owns-the-train).
- <a id="stationmarker-glossary"></a>**station marker**: the exact spot a train parks on. Stopping
  within three blocks of one pulls the train onto it, so `stopped` always means the same position.
  See it in [Known behavior](Known-behavior#stationmarker-explained).
- <a id="stopline-glossary"></a>**stop line**: the exact spot a distance to a signal is measured to,
  whatever color the signal shows. See it in [Signals](Signals#stopline-explained).
