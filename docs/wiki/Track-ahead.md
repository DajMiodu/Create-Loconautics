## Track ahead

What the rail does in front of the train: where it ends, where it bends, how it climbs, and where it changes
between world track and [sub level](Glossary#sublevel-glossary) track.

Everything is measured from the nose, the front wheel of the end the cab faces, along the rail. It is
not the world distance in a straight line.

The route follows the straightest branch at a junction, the same as the signal look ahead.

Range is optional. Every call takes a last `range` argument in blocks. The default is 256 and the most is 1024.

Nil means nothing inside the range, and also a derailed train, which has no rail ahead of it. No call here
needs a [control lease](Glossary#controllease-glossary).

Only the front is searched, so a train reversing away from its cab does not see what is behind it.

### World track and sub level track

A railway here is two kinds of rail. World track sits in the world. Sub level track sits on a moving
body such as a turntable plate or a bridge. They join at a seam, and a seam is not always closed: a turned
plate leaves a gap in the track.

| Call | Returns |
| --- | --- |
| `train.getTrackType()` | `"world"` or `"sublevel"`, the rail under the nose |
| `train.getTrackSubLevelId()` | The id of the sub level the rail sits on, nil on world track |

Every table below carries `type` (or `from` and `to`) with the same two words, and a `subLevelId` string when the
rail is on a sub level.

### End of track

#### `train.getDisToTrackEnd([range])`

Blocks from the nose to the last rail of the track ahead. Nil when the track goes on past the range.

#### `train.getTrackEnd([range])`

The same, as a table:

- `distance`, blocks from the nose
- `x`, `y`, `z`, the last rail point in world coordinates
- `type`, `"world"` or `"sublevel"`, what the last rail sits on
- `subLevelId`, only on a sub level

A dead end is not always the end of the line. A turntable plate turned away leaves the track ending at the
gap. Check [`getNextSubLevelTrack`](#trainGetNextSubLevelTrackrange) before treating it as a buffer stop.

```lua
local gap = train.getDisToTrackEnd(120)
if gap and gap < train.getBrakingDistance() + 4 then
    train.stop(true)
end
```

### Curves

#### `train.getNextCurve([range])`

The first bend ahead. Bends that turn the same way, one after another, count as one.

- `distance`, blocks to where the bend starts, 0 when the nose is already in it
- `length`, blocks of rail in the bend, from the nose when already inside
- `angle`, degrees turned, negative to the left and positive to the right
- `direction`, `"left"` or `"right"`
- `radius`, blocks, length divided by the angle in radians
- `type`, `"world"` or `"sublevel"`

#### `train.getDisToCurve([range])`

Just the distance.

```lua
local curve = train.getNextCurve()
if curve and curve.distance < 40 and curve.radius < 30 then
    print(string.format("%s %.0f degree bend in %.0f blocks", curve.direction, math.abs(curve.angle), curve.distance))
end
```

```result
right 90 degree bend in 31 blocks
```

### Slope

Slope here is the same signed number as [`getSlope`](Motion#traingetslope): positive climbs in the direction of travel,
negative drops.

#### `train.getSlopeAhead(distance)`

The slope of the rail `distance` blocks ahead, in degrees. Nil when the track ends before that point.

#### `train.getNextSlopeChange([minDelta [, range]])`

The first place the slope differs from the slope under the nose by `minDelta` degrees or more, 1 when left out.

- `distance`, blocks to that point
- `from`, the slope under the nose
- `to`, the slope there

#### `train.getDisToSlopeChange([minDelta [, range]])`

Just the distance.

```lua
local change = train.getNextSlopeChange(2)
if change and change.to < -3 then
    print(string.format("downhill in %.0f blocks", change.distance))
end
```

### World track and sub level track ahead

#### `train.getNextTrackChange([range])`

The first place the rail changes from world to sub level or back, or from one sub level to another.

- `distance`, blocks to the seam
- `from`, `to`, `"world"` or `"sublevel"`
- `joined`, true when the seam is closed and the train can roll over it, false when there is an air gap
- `gap`, blocks of air, 0 when joined
- `x`, `y`, `z`, the seam in world coordinates
- `subLevelId`, the sub level the rail leads onto, when it is one

A gap only shows up past a dead end. The look for loose rail goes 12 blocks beyond the last rail, and it only
counts rail that lines up with the track.

#### <a id="trainGetNextSubLevelTrackrange"></a>`train.getNextSubLevelTrack([range])`

The first rail ahead that sits on a sub level other than the one under the nose. Same table as
`getNextTrackChange`, skipping any world track in between.

#### `train.getNextWorldTrack([range])`

The first world rail ahead. Same table.

#### `train.getDisToSubLevelTrack([range])` and `train.getDisToWorldTrack([range])`

Just the distance of each.

```lua
local ahead = train.getNextSubLevelTrack()
if ahead and not ahead.joined then
    print(string.format("plate is %.1f blocks away, gap %.1f", ahead.distance, ahead.gap))
end
```

```result
plate is 18.5 blocks away, gap 2.0
```

### An invisible wall at the end of the track

Stop short of a dead end, and keep reversing allowed:

```lua
local train = loconautics.getTrain()
while true do
    local gap = train.getDisToTrackEnd()
    local ahead = train.getNextSubLevelTrack(40)
    local open = ahead and ahead.distance <= (gap or math.huge) + 12
    if gap and not open and train.getSpeed() > 0
            and gap < train.getBrakingDistance() * 1.3 + 3 then
        train.stop(true)
    end
    os.sleep(0.05)
end
```

This is a brake and not a wall: `stop` takes the lease, so a player in the cab is overridden while it runs,
and the brake stays on until `release()` or `setBrakeScale(0)`. See [Braking](Braking).
