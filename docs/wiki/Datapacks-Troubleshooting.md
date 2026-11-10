# Troubleshooting

Check `logs/latest.log` first. Each loader prints a tag.

## Log lines

| Line | Meaning |
| --- | --- |
| `[VariantPicker] loaded N datapack families` | Variant families loaded. |
| `[VariantPicker] bad family <file>: ...` | That file is broken JSON or has the wrong shape. It was skipped. |
| `[VariantPicker] block X is not registered, skipped` | A block id in a family does not exist. Check the spelling and that the mod is installed. |
| `[HoseSpray] loaded N bindings, N color overrides` | Hose spray files loaded. |
| `[HoseSpray] unknown fluid X, binding skipped` | The fluid id does not exist. |
| `[HoseSpray] unknown effect 'X'` | `effect` must be `douse` or `ignite`. |
| `[HoseSpray] unknown mob_effect X` / `unknown entity type X` | The id does not exist. The rule does nothing. |

## Common mistakes

- **Picker does not open.** The held block must be in a family, and the family needs at least two entries.
- **Entry missing from the picker.** Its block id is not registered. The log names it.
- **Tag entry ignored.** Entries marked `"required": false` are silently ignored when missing. Remove that flag while testing to see the error.
- **Changes not showing.** Run `/reload`. If it still looks old, rejoin the world.
