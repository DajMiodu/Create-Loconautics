## Schedule mode

A single `schedule` instruction disables redstone and Lua turns. The train router
controls the plate, aligns it with the leg a scheduled train needs, and
returns it to its home angle when it is empty.

### `turntable.isScheduleDriven()`

True when the turntable is assembled and the list is the `schedule` one.

### `turntable.isOccupied()`

True when a train's carriage or any of its bogeys is riding in the plate's own sub level. It answers
for the plate right now, in schedule mode or out of it.

The router holds the plate longer than `isOccupied` reports. Its clearance keeps the plate in
place for three seconds after the last bogey crosses the seam, because the carriage body can still be
over the rim after the bogeys leave. So `isOccupied` can be false while the plate has not
returned home.

### `turntable.isAligned()`

True when the plate is lined up with its current target, within 0.75 degrees.

### `turntable.getAlignTarget()`

The track node the router is aiming at, as `{x = , y = , z = }`, or nil when there is no target.

### `turntable.clearAlignment()`

Drops the current target. The router picks a new one on the next train, and the plate returns home
in the meantime. Do not call this while a train is crossing, the plate can turn with the train
on it.
