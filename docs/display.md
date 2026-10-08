# Display ownership and behavior

The display owns broker consumption, scan state, kiosk metrics state, rendering, layout, clocks, and presentation assets.
Its composition entry points are
[`src/jsMain/kotlin/com/bkahlert/netmon/display/app/app.kt`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/app.kt)
and
[`src/jsMain/kotlin/com/bkahlert/netmon/display/app/main.kt`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/main.kt).
Its JS tests live under
[`src/jsTest/kotlin/com/bkahlert/netmon/display`](../src/jsTest/kotlin/com/bkahlert/netmon/display),
and its production-bundle WebKit checks live under [`tests/browser`](../tests/browser).
Run `make test-js` for JS behavior and `make test-layout` for the production bundle in Playwright WebKit.

## Ownership

The display packages are split by responsibility:

- [`display/app`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app): app lifetime,
  broker settings, scan freshness settings, and browser startup.
- [`display/broker`](../src/jsMain/kotlin/com/bkahlert/netmon/display/broker): MQTT connection,
  subscription, scan decoding, and metrics-topic filtering.
- [`display/networks`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks): scan state,
  grouping, card rendering, and elapsed-time presentation.
- [`display/metrics`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics): OTLP/JSON decoding,
  kiosk stats, and status data preparation.
- [`display/presentation`](../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation): model labels,
  icons, and URI helpers.
- [`display/support`](../src/jsMain/kotlin/com/bkahlert/netmon/display/support): browser clocks,
  console interception, and owned rendering helpers.

## Broker consumption and state

[`brokerMessages`](../src/jsMain/kotlin/com/bkahlert/netmon/display/broker/events.kt)
opens one MQTT connection and subscribes to scan and metrics topics together.
The broker package decodes scan events and keeps metrics payloads separate.
It does not render.

[`ScanEventsStore`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks/ScanEventsStore.kt)
owns scan replacement and freshness.
Each decoded scan replaces the current host list of its source.
A scan older than `scan.outdatedThreshold` is ignored and removed.

[`KioskStatsStore`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics/KioskStatsStore.kt)
owns OTLP/JSON parsing and freshness of optional kiosk samples.
Metrics stay in the status flow.
They do not affect grouping, icon choice, or card content.

## Rendering, layout, and clocks

[`networks`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks/network.kt)
renders grouped cards from `ScanEventsStore` plus the minute clock.
[`status`](../src/jsMain/kotlin/com/bkahlert/netmon/display/support/status.kt)
renders console messages and kiosk figures from the current-time clock and metrics store.

The app owns two clocks:

- a refresh clock for relative timestamps and stale-data checks
- a minute clock for card age styling

That ownership stays inside the display app lifetime.
A disposed app stops both clocks and all subscriptions.

Layout is validated against the production bundle, not the dev server.
[`tests/browser/test_layout.py`](../tests/browser/test_layout.py)
loads [`build/dist/js/productionExecutable`](../build/dist/js/productionExecutable)
in Playwright WebKit and checks geometry, grouping order, styling, and the loading animation.

## Presentation assets

Icon and model presentation stays in display ownership:

- [`device-model-codes.json`](../src/jsMain/resources/assets/device-model-codes.json)
  maps recognized Apple-style model codes to display labels and SF Symbols.
- [`device-icons.json`](../src/jsMain/resources/assets/device-icons.json)
  maps kinds and specific vendor or model matchers to shipped SVGs.
- [`DeviceIcons.kt`](../src/commonMain/kotlin/com/bkahlert/netmon/display/presentation/DeviceIcons.kt)
  reads those assets.

The icon choice order is:

1. a shipped SF Symbol for a known Apple-style model code
2. the first matching specific brand or model icon
3. the host kind icon
4. the generic display glyph

## Extending the display safely

### Change grouping or card content

- Edit the display code under
  [`src/jsMain/kotlin/com/bkahlert/netmon/display/networks`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks).
- Cover the behavior under
  [`src/jsTest/kotlin/com/bkahlert/netmon/display/networks`](../src/jsTest/kotlin/com/bkahlert/netmon/display/networks).
- Run `make test-js` and `make test-layout`.

### Change metrics presentation

- Keep decoding under
  [`src/jsMain/kotlin/com/bkahlert/netmon/display/metrics`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics).
- Keep rendering under
  [`src/jsMain/kotlin/com/bkahlert/netmon/display/support`](../src/jsMain/kotlin/com/bkahlert/netmon/display/support).
- Cover the parser or store in
  [`src/jsTest/kotlin/com/bkahlert/netmon/display/metrics`](../src/jsTest/kotlin/com/bkahlert/netmon/display/metrics).
- Run `make test-js`.

### Change icons or model presentation

- Update the generator inputs under [`tools/netmon_dev/assets`](../tools/netmon_dev/assets).
- Regenerate with `make device-model-codes` or `make device-icons`.
- Cover matcher behavior and asset fetching in the JS suite.
- Run `make test-js`, `make test-layout`, and the affected Python unit tests.
