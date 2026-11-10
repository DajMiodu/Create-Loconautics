## Assembly

### `turntable.isAssembled()`

True when the plate exists and the servo is holding it.

### `turntable.assemble()`

Assembles the blocks in front of the bearing into a plate. Returns true if it assembled,
false if it was already assembled. Throws Create's message if the structure cannot be
assembled, for example when it touches an unmovable block or an unloaded chunk.

```lua
if not turntable.isAssembled() then
    local ok, err = pcall(turntable.assemble)
    if not ok then print("cannot assemble: " .. err) end
end
```

### `turntable.disassemble()`

Returns the plate to the world at its current angle. Returns true if it disassembled, false
if there was nothing assembled. This resets the angle to 0, and it clears any alignment.

### `turntable.getAssemblyError()`

The last assembly failure as text, or [nil](Glossary#nil-glossary) if the last attempt worked. The
same message the bearing shows in the tooltip.
