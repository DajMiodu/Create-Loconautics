## Full programs

Two finished programs that read the whole railway and need no train on the computer. They are long, so each lives in its own file next to these pages. Copy the file onto a computer in game, the code is not repeated here.

### Track map

Draws the whole railway on a monitor wall: track, curves, sub level track such as turntable plates, signals
colored by state, stations, turntables, portal points and every train. It uses the calls from
[Network](Network) and needs no train on the computer, so put it beside any advanced monitor.

Copy `trackmap.lua` from the same folder as these pages onto a computer next to the monitor and save it as `trackmap`. Then run
`trackmap`. Every option is optional:

| Option | Default | Meaning |
| --- | --- | --- |
| `monitor=<side or name>` | the first monitor found | Which monitor to draw on |
| `dimension=<id>` | `minecraft:overworld` | Which dimension to map |
| `box=minX,minZ,maxX,maxZ` | everything found, plus padding | The area to show |
| `pad=<blocks>` | `48` | Extra space around the automatic area |
| `refresh=<seconds>` | `1` | How often trains and moving track redraw |
| `scale=<0.5 to 5>` | `0.5` | Monitor text scale, smaller means a finer map |

```
trackmap monitor=top dimension=minecraft:the_nether box=-200,-200,200,200
```

Colors. Gray is track. A signal is a dot, lime for clear, yellow for a chain signal with only some routes free, red for stopped. Pink is a
station and cyan a turntable, filled while a train is on it. Purple marks a portal point. A train is light
blue while moving, white when stopped, orange when held at a red and magenta when derailed.

How it works. The track is fetched once in boxes small enough for `getTrack`, then cached. Moving
track, stations, turntables and trains are redrawn every `refresh` seconds, and signals redraw on the
`signal_changed` event. Each text character on the monitor holds a 2 by 3 block of pixels, so a 5 by 3
monitor at scale `0.5` gives about 200 by 114 map points. Hold Ctrl+T to stop it.

Limits. One monitor shows one dimension. A character cell has two colors, so where a train sits on
track the cell shows the train. A signal shows its worst side, it does not show direction.

### Command room

A board of every train: where it is, where it is going, when it gets there, and what is wrong. It reads
[`getTrains`](Network) once a second and listens for the network events, so it needs no train on the
computer.

Copy `commandroom.lua` from the same folder as these pages onto a computer and save it as `commandroom`. With no options it draws on the
computer's own screen. Give it a monitor to use a wall:

| Option | Default | Meaning |
| --- | --- | --- |
| `monitor=<side or name>` | the computer screen | Which monitor to draw on |
| `scale=<0.5 to 5>` | `1` | Monitor text scale, only used with `monitor` |
| `refresh=<seconds>` | `1` | How often the board redraws |
| `page=<seconds>` | `6` | How long each page of trains shows when they do not all fit |
| `feed=<lines>` | `6` | How many event lines to show at the bottom |

```
commandroom monitor=monitor_3 scale=1
```

The table. Each train gets one row: name, state, where it is, ETA, speed in blocks per second, and a
note. Rows sort with trouble first, derailed or passed a red, then held and paused trains, then moving
trains by the nearest ETA, then stopped trains by name. The header turns red while any alert is open.

ETA is the blocks left to the next stop divided by the train's recent average speed. It is an
estimate. It shows `-` while the train stands still or has no schedule, and it does not know about the
braking at a platform or a red ahead, so it reads a little short for a train that is about to slow.

The event feed shows arrivals, departures, derails, reds, cleared reds, and trains added or removed,
newest first. A train that passes a red keeps the `RAN RED` alert for 30 seconds. The feed starts empty,
nothing is reported for what was already true when the program started.

Keys. `e` sends an emergency stop to every train with `loconautics.stopTrains`, `r` gives them back with
`loconautics.releaseTrains`. Any other key or a touch flips the page. Both need remote control to be on, see
[Controlling the network](Controlling-the-network).

More trains than rows. The board flips pages on its own. Touch the monitor or press any other key to flip by
hand. Hold Ctrl+T to stop it.

Run it next to the track map. On an advanced computer, `bg trackmap` opens the map in a second tab, then
run `commandroom` here. Each needs its own monitor, or the computer screen for one of them.
