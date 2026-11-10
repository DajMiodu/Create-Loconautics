## Drivetrain

A train only drives if its drivetrain is built right. The drivetrain is the
[Transmission](Glossary#transmission-glossary) and the [Bearing Axle](Glossary#bearingaxle-glossary), and the axle has to be
set up on a [bogey](Glossary#bogey-glossary).

#### All four of these have to be true:

1. The carriage has **exactly one** transmission.
2. The bearing axle sits **right next to a bogey**.
3. The axle **faces along that bogey's rails**, not across them.
4. The transmission sits on the **shaft end** of the axle, the side opposite the front, with its own shaft
   running in the same line as the axle's.

These calls refuse to run when it is not built right: `setManualControl(true)`, `setTargetSpeed`,
`parkAtStation` and `parkAtCoord`. They throw an error that says what to fix:

```
the train cannot be driven, the transmission is on the wrong side of the bearing axle, move it to the shaft end, the side opposite the front
```

Computer speed goes through the transmission. `setTargetSpeed` does not move the train directly. It
sets the transmission to the RPM and direction that give that speed, and the engine has to supply the rotation.
Without engine power the train does not move. The train never exceeds the speed you set, or what the
engine and the transmission can give. `getTargetSpeed()` reads what the drivetrain is really asking for.

Small speed changes are filtered. The transmission is only retuned when the wanted RPM changes
by about 8 or more, and at most every quarter second, because every retune briefly cuts its shaft. Releasing
the train hands the transmission back to its redstone and links.

A drivetrain that breaks mid drive stops the train. While it is broken the speed stays at 0, a running park
ends as `false` and the brake, doors and steering still work. Fix the build and take the lease again.

### `train.getDrivetrain()`

Asks the question without driving anything. Returns a table:

| Field | Meaning |
| --- | --- |
| `ok` | true when the drivetrain is built right |
| `problem` | a short key, only when `ok` is false |
| `message` | the same problem in plain words, only when `ok` is false |

```lua
local d = train.getDrivetrain()
if not d.ok then
    print(d.message)
end
```

```result
the bearing axle is not touching a bogey, place it right next to the bogey it drives
```

| `problem` | What to do |
| --- | --- |
| `no_transmission` | Place a transmission on the shaft end of the axle |
| `many_transmissions` | Keep one transmission per carriage |
| `no_bearing_axle` | Place a bearing axle |
| `bearing_axle_no_bogey` | Move the axle right next to its bogey |
| `bearing_axle_wrong_axis` | Turn the axle so it faces along the bogey's rails |
| `transmission_not_touching` | Move the transmission against the shaft end of the axle |
| `transmission_wrong_side` | The transmission is on a side face, move it to the shaft end |
| `transmission_wrong_axis` | Rotate the transmission with a wrench until both shafts line up |

### `train.getTransmission()`

The [transmission peripheral](#transmission-peripheral) of this train's drivetrain. Throws the `message` above
when the carriage has no transmission.

### The transmission peripheral

<a id="transmission-peripheral"></a>The transmission is a peripheral too, with the type `loconautics:transmission`. Get it the same two ways
as the axle:

```lua
local transmission = loconautics.getTransmission()
local transmission = peripheral.find("loconautics:transmission")
```

`loconautics.getTransmission()` works from any computer in the cart and throws when there is none.
`peripheral.find` needs the computer touching it or on its modem network and returns nil when it finds nothing.

All read only. You set its speed through `train.setTargetSpeed`, not on the transmission itself. Without a
driving computer it follows its redstone and Create links.

| Call | Returns |
| --- | --- |
| `getPower()` | The redstone speed setting, 0 to 15. 0 means off. A driving computer or schedule drive reports the power its RPM equals, see `isComputerControlled()` |
| `getTargetRpm()` | The RPM it is asking for right now, 0 to 256, including a computer's setting |
| `isComputerControlled()` | True while a computer holds the speed and direction |
| `getInputRpm()` | The RPM coming in from the engine |
| `getOutputRpm()` | The RPM going out to the axle |
| `isReversed()` | True while the direction signal is on |
| `getSpeedFrequency()` | `{first, second}`, the item ids of the speed link, `""` for an empty slot |
| `getDirectionFrequency()` | `{first, second}`, the item ids of the direction link |
| `isConnected()` | True when the drivetrain is built right |
| `getConnection()` | The same table `train.getDrivetrain()` returns |

```lua
local t = loconautics.getTransmission()
print(string.format("power %d, out %.0f rpm%s", t.getPower(), t.getOutputRpm(),
    t.isReversed() and ", reversed" or ""))
if not t.isConnected() then
    print(t.getConnection().message)
end
```

```result
power 8, out 137 rpm
```

A transmission at power 0 is off, not broken. It lets the axle stop turning, so `getOutputRpm()` reads 0.
`isConnected()` only checks how it is built, so it stays true.
