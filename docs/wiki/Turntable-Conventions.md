## Conventions

Angles are degrees. `getAngle()` returns 0 to 360, measured from the angle the plate had when it
was assembled. Arguments accept any number, positive turns one way and negative the other.

Speed is RPM. `getSpeed()` is the rotation speed reaching the bearing's own cogwheel, signed the
way Create signs rotation, and capped at 96.

A call that cannot succeed throws an error. Wrap calls that can fail in `pcall`:

```lua
local ok, err = pcall(turntable.turnBy, 90)
if not ok then print(err) end
```

Everything runs on the server tick. A call returns after the game has applied it, so back to back
calls are applied in order.
