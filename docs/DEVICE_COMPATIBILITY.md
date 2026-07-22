# Device compatibility

Adaptive Remote aims to support a broad range of Bluetooth Low Energy intimate devices without claiming that every Bluetooth toy is automatically controllable.

Bluetooth discovery exposes a device name, address, advertisements, GATT services, and characteristics. It does not describe the commands that make a device vibrate, rotate, oscillate, constrict, inflate, or move linearly. Adaptive Remote must have a verified protocol implementation before it sends commands to a device.

## Compatibility definition

A device is **supported** only when all of the following are true:

1. The app can identify its protocol from documented scan data and/or discovered GATT services.
2. The app has an explicit adapter for that protocol and the device's advertised capabilities.
3. Stop behavior is defined and exercised on disconnect, service shutdown, and command failure paths.
4. Automated tests cover protocol recognition, command bounds, and stop commands.
5. The protocol/device family has been physically tested and is listed in the verified-device table.

A device that merely appears in a Bluetooth scan is **discovered**, not supported. Adaptive Remote must not guess commands or probe unknown writable characteristics.

## Recognition model

Device recognition should follow the same general model used by Buttplug:

1. Scan for nearby BLE advertisements.
2. Compare advertised names, manufacturer data, and service UUIDs with protocol-specific identifiers.
3. When required, connect read-only and discover GATT services and characteristics.
4. Match the result to one unambiguous protocol adapter.
5. Expose only the actuator capabilities declared by that adapter and device configuration.
6. Require explicit user confirmation before the first controlled low-output test.

Recognition must not depend only on a product's marketing name. Multiple products can share a Bluetooth protocol, and names can be missing, localized, duplicated, or changed by firmware.

## Protocol sources

The primary compatibility reference is the maintained [Buttplug device configuration](https://github.com/buttplugio/buttplug-device-config), paired with the [Buttplug device implementation guide](https://buttplug.io/docs/dev-guide/inflating-buttplug/devices/intro/) and protocol research in [STPIHKAL](https://stpihkal.docs.buttplug.io/).

Manufacturer documentation or an authorized SDK should be preferred when available. For example, Lovense publishes a [developer platform](https://developer.lovense.com/). An SDK's license, account requirements, network dependency, data handling, and store-distribution terms must be reviewed before it is added.

The external catalog is a research source, not code that Adaptive Remote silently downloads or executes. Protocol identifiers used by the app should be reviewed, versioned, and tested in this repository.

## Current support

| Protocol or ecosystem | Recognition | Control path | Status |
| --- | --- | --- | --- |
| JoyHub `FFA0`/`FFA1` | GATT service `FFA0` with writable `FFA1` | Direct BLE, seven-byte JoyHub oscillation command | Implemented; physically verified only with Cafatop Knight advertised as `J-Mars` |
| Buttplug/Intiface catalog | Configuration-based identifiers for many protocols | Requires either an embedded compatible engine or individual native adapters | Research source; not yet integrated |
| Lovense developer platform | Manufacturer-managed device/API discovery | Manufacturer SDK or API, subject to terms and architecture review | Not yet integrated |
| Unknown BLE device | Name and GATT inspection only | None | Discoverable but intentionally not controllable |

## Capability model

The product UI and control engine must describe devices by capabilities rather than assuming one oscillating motor. The initial model should allow a device to expose one or more independently addressable actuators:

- vibration
- rotation
- oscillation
- constriction
- inflation
- linear movement
- position or scalar control when the protocol supports it

Battery, sensor, and telemetry support should remain separate from output actuators. Unsupported capabilities must not be simulated or mislabeled.

## Implementation sequence

1. Introduce protocol-neutral device, connection, actuator, and command interfaces around the existing JoyHub implementation.
2. Convert JoyHub into the first adapter without changing its verified command bytes or safety behavior.
3. Replace Knight-specific setup text with capability-based device language.
4. Add one additional protocol family from a documented source and physically test representative hardware.
5. Expand protocol families incrementally, updating the verified-device table for every tested model.

Embedding or connecting to a Buttplug/Intiface engine may provide the widest coverage, but it is a separate product decision. The Android integration, dependency health, license compatibility, offline behavior, binary size, privacy, and update path must be evaluated before adopting it.

## Adding a device family

Every new adapter pull request must include:

- the authoritative or research documentation used
- exact advertisement and/or GATT matching rules
- supported actuator types, count, ranges, and units
- command encoding and stop semantics
- behavior for disconnects, timeouts, partial writes, and unsupported commands
- unit tests for recognition, bounds, command bytes, and stop behavior
- the hardware and firmware versions physically tested
- any SDK license, account, cloud, privacy, or distribution constraints

If physical hardware is unavailable, the adapter may be developed behind an experimental flag, but it must not be presented as verified support.
