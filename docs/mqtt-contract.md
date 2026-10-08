# MQTT contract

This page describes the wire boundary between scanner, display, and the optional `netmon-metrics` sampler.
Cross-component documentation stops at the contract.
Component internals stay in [scanner.md](scanner.md) and [display.md](display.md).

## Topics

| Producer | Topic | Subscriber view | Notes |
|---|---|---|---|
| scanner scan event | `dt/netmon/${node}/${interface}/${cidr}/scan` | `dt/netmon/+/+/+/+/scan` | `cidr` spans two MQTT levels because it contains the slash |
| scanner host event | `dt/netmon/${node}/${interface}/${cidr}/host` | none in this repo | emitted for status changes only |
| optional metrics sampler | `dt/netmon/<node>/metrics` | `dt/netmon/+/metrics` | consumed by the display status flow only |

[`ScanTopics`](../src/commonMain/kotlin/com/bkahlert/netmon/contract/ScanTopics.kt)
builds topics and wildcard subscriptions from the configured templates.
The installed scanner defaults to the `scan` and `host` templates above.

## Event payloads

### Scan event

A scan publication is a contract `Event.ScanEvent`:

- `event`: always `scan`
- `type`: `restored` or `completed`
- `timestamp`: epoch seconds
- `hosts`: the full merged host list for that source

### Host event

A host publication is a contract `Event.HostEvent`:

- `event`: always `host`
- `type`: `up` or `down`
- `host`: one merged host value

### Host fields

[`Host`](../src/commonMain/kotlin/com/bkahlert/netmon/contract/Host.kt)
is shared by scanner and display.
Every field but `ip` is optional.
Fields with `null` values are omitted from JSON.
The current fields are:

- `ip`
- `name`
- `status`
- `since`
- `model`
- `vendor`
- `services`
- `lastSeen`
- `mac`
- `kind`
- `link`
- `speed`

## Delivery and retention

The scanner publishes scan and host events with QoS 1.
Both publications are retained.
That keeps the latest scan and the latest host status available to new subscribers.
The display subscribes to scan and metrics topics with QoS 1.
The display redraws from scan events only.
It does not read the host topic.

The metrics contract is narrower.
This repo defines the topic and the OTLP/JSON fields the display reads.
It does not add scanner-side retention or publication behavior for metrics.

## Compatibility rules

Compatibility is intentionally loose:

- Unknown JSON keys are ignored. The contract must ignore unknown keys.
- Optional fields may be absent.
- New host fields stay optional.
- Unknown `Kind` tokens decode as `Generic`.
- Scanner and display may run different versions as long as those rules hold.

[`JsonFormat`](../src/commonMain/kotlin/com/bkahlert/netmon/contract/serialization/JsonFormat.kt)
keeps ignoring unknown keys and omitting `null` values.
That is the reason this contract can evolve without lock-step deploys.

## Metrics payloads

[`KioskStats.kt`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics/KioskStats.kt)
reads OTLP/JSON and extracts the kiosk sample the status bar uses:

- `system.cpu.logical.count` from the `service.name = netmon-metrics` resource
- `systemd.unit.cpu.utilization` for `pihero-kiosk.service`
- `process.cpu.utilization` for `WPEWebProcess`
- `systemd.unit.memory.usage` for kiosk RAM and swap

A metrics payload that is empty, malformed, or missing those data points is ignored.
