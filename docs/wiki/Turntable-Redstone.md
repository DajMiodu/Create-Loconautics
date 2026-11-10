## Redstone

### `turntable.pulse(powered)`

Feeds the bearing a redstone level as if a wire had changed, and also sets the block's `powered`
state. Pass true then false for a pulse:

```lua
turntable.pulse(true)
turntable.pulse(false)
```

The rising edge is what acts, so two `pulse(true)` calls in a row do nothing the second time. On
`hold` the plate keeps repeating until you pass false.
