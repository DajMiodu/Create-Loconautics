## Geometry

### `turntable.getFacing()`

Which way the bearing points, one of `up`, `down`, `north`, `south`, `east`, `west`. The plate turns
around this axis.

### `turntable.getPos()` / `turntable.getPlatePos()`

Block positions as `{x = , y = , z = }`. `getPos` is the bearing itself, in world coordinates.

`getPlatePos` is the plate's anchor block, and it returns nil when the turntable is not assembled.

These are not world coordinates. An assembled plate is in a sub level plot far from the
world origin, so the values can be in the millions. Use it to tell plates apart, not to find
the plate's world position. For that, use [`getAxisPos()`](#turntablegetaxispos--turntablegetaxisnormal).

### `turntable.getSubLevelId()`

The UUID of the plate's sub level as a string, or nil when not assembled.

Do not save this. A sub level gets a fresh id every time the world loads.

### `turntable.getAxisPos()` / `turntable.getAxisNormal()`

The rotation axis in world coordinates, as `{x = , y = , z = }` with decimals. `getAxisPos` is the
point the plate turns about, `getAxisNormal` is the unit vector it turns around. Both account for a
bearing that is itself riding inside another sub level. Both need the turntable assembled.
