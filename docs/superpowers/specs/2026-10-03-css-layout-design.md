# The display's layout without JavaScript

## Intent

The page fits its host cards to the viewport with JavaScript: a mutation observer per card section reads the scroll height,
shrinks a `zoom` factor in two animation frames, and starts over on every change; every text line measures itself once
after it renders. On the board the web process burns 93 to 128 % of one core with the page idle, and its busiest thread is
the main thread (97 % in a read-only `top -H` on 2026-10-03; the Skia painting worker used 1.5 %), so the cost is script,
style and layout, not rasterising.

This change lets the browser do the fitting. The page is locked to the viewport, the card area is a flex item that
shrinks, and CSS computes the grid from two counts that Kotlin writes as custom properties. The look stays: the same
cards, the unchanged hosts one step smaller and dimmer under "1h+ unchanged", the same maximum size.

## Current mechanism

`ui/network.kt` and `ui/zoom.kt`, the latter removed by this change:

- Each section's `ul` is a grid of `repeat(auto-fill, 185px)` cells inside a `div` that clips and carries a `zoom`.
- `zoomedToFitClientHeight` observes the `div` for child changes, reads `scrollHeight`, and lowers `zoom` by the square
  root of the coverage or by 5 %. The clock tick replaces the "since" text of every recent host each second, which is a
  child change, so the loop runs once a second.
- `zoomToFitClientWidth` schedules a frame per text line, reads two layout properties and writes a `zoom`.
- A resize, debounced, and a change of the host count reset all zooms.
- The stable section takes `zoom: 0.75` and half opacity.
- The cards never grow past their natural 185 by 84 px and never fill the space they have left.

For 53 hosts on 800 by 480 the board shows 14 recent cards at about 43 % and 39 stable cards at about 32 %, and a quarter
of the panel stays black.

## Design

### Page

`#root.app` is `100dvh`, a flex column, clipped. `.networks` is `flex: 1; min-height: 0`. Inside it the rendered root is a
grid of `repeat(auto-fit, minmax(min(15rem, 100%), 1fr))` columns and `minmax(0, 1fr)` rows, so several scans share the
space, as today, and a single scan fills it. The small-screen stacking stays: one column below 15rem per scan.

### Card

A scan card is a flex column that clips: the header at its natural height, then the hosts area with `flex: 1; min-height: 0`.
The hosts area is a size container. Inside it sit the unstable `ul`, the divider, and the stable `ul`. Kotlin sets two
unitless custom properties on the area, `--unstable` and `--stable`, the counts of the two sections.

### Columns, rows and heights

Everything below is CSS, in [styles.css](../../../src/jsMain/resources/styles.css).

- The natural cell is 11.5625 rem by 5.25 rem (185 by 84 px at 16 px), times the section's scale `--sigma`: 1 for unstable,
  0.75 for stable.
- The hosts area's width-to-height ratio comes from nine container queries on `min-aspect-ratio`; each sets `--ratio` to
  the middle of its step. CSS cannot divide two lengths. The first attempt, `tan(atan2(100cqw, 100cqh))`, is correct in
  Playwright's WebKit but wrong in WPE 2.48.3: the kiosk evaluated it to 0.69 where the ratio was 2, and to an invalid
  value for `vw` and `rem` arguments.
- Equal scale for both sections needs `balanced = sqrt(ratio / aspect * (unstable + stable * sigma^2))` columns for the
  unstable cells and `balanced / sigma` for the stable ones, where `aspect` is the natural cell's width to height.
- Columns: `repeat(auto-fill, minmax(min(natural width * sigma, width / round(balanced / sigma)), 1fr))`. The browser
  turns the minimum width into a count, so the section gets the larger of the balanced count and as many natural columns
  as the width holds. That keeps the old look when everything fits at natural size.
- A section's height is `flex: 0 1 auto`: its rows at natural size, so a short list stacks at the top as before. When
  space is short the sections shrink in proportion to their rows times scale, which is what equal scale needs.

A simulation over 72 combinations of area size and counts, against an exhaustive search of both column counts, put this
at 98 % of the best scale on average and 86 % at the worst.

### Cell

Each `li` is a size container (`container-type: size`) with its track as size. The `.host` inside sets its `font-size` to
`min(1rem * sigma, 100cqw / 11.5625, 100cqh / 5.25)` and lays out everything in `em`: icon 2.5 em, gap 0.5 em, padding
0.25 by 0.5 em, margin 0.25 em, name 0.875 em, the other lines 0.75 em. The cell scales as one piece.

### Text lines

The old code shrank each line that overflowed. The text column is 7.06 em wide, so a line of `len` characters at an
estimated `char` em per character fits when its font size is at most `7.06 em / (len * char)`. `char` is 0.68 for the
bold name, 0.6 for the vendor, the address and the status, and 0.62 for the model label; the kiosk's DejaVu is wider than
the fonts of a Mac, so the estimates err on the wide side. Kotlin writes `len` as a custom property on a span in the line;
CSS takes `clamp(0.6em, 7.06em / (max(len, 1) * char), 1em)`, and `overflow: hidden; text-overflow: ellipsis` ends what
still does not fit. The model label under the icon uses the longest word and may wrap, but its height stops at two
lines: a longer description would grow the card past its row (the layout test has descriptions of four and six words).

### What goes

`zoom.kt`, `observers.kt`, the `resizes` and `verticalScrollCoverageRatio` helpers, every `zoom` and `data-zoomed`
write, and the `resetZoomed` subscriptions.

## Alternatives

| Alternative | Why not |
|---|---|
| A `ResizeObserver` that writes `--cols` | Works everywhere, costs a script and a test, and the CSS formula reaches 99 % of its result. It is the fallback if the kiosk's WebKit lacks a function |
| `repeat(auto-fit, minmax(8rem, 1fr))` alone | The browser picks the column count by the minimum width, not by the count of items, so 53 cards leave rows of unequal fill and one size for both sections |
| `tan(atan2(100cqw, 100cqh))` for the ratio | Wrong in WPE 2.48.3, see above |
| Scaling the whole list with `transform: scale()` | Blurs text on the CPU renderer and still needs the factor |

## Browser support

WPE WebKit 2.48.3 and cog 0.18.4 on the board (the footprint spec's Sources). In the 2.48.3 tag,
`CSSCalcTree+Parser.cpp` parses `atan2()`, which the kiosk then evaluates wrongly for container units, see above. A page of
feature probes in the VM kiosk passed `cqw`, `sqrt()`, `round()` with and without a step, `min()` and `clamp()` with
units, calculated grid tracks, `dvh` and calculated line heights. The VM kiosk runs the board's WPE; its picture settles
what the probes cannot.

## Tests

- Kotlin (Karma): a card writes `--unstable` and `--stable`, updates them when hosts move between sections, writes `--len`
  on every fitted line, and no element carries a `zoom` style or a `data-zoomed` attribute.
- Layout (`make test-layout`, Playwright's WebKit against the built page with a scripted MQTT broker): for 1 to 120
  hosts in one scan and for two and three scans of 53 hosts at 800 by 480, 1440 by 900 and 390 by 844, the page does not scroll, no card
  leaves its scan, no two cards overlap, and at 3 hosts the cards have their natural font size.
- The VM kiosk (`make test-tier2`) pictures the page after the first scan; a hand-fed scan of 53 hosts pictures the
  dense case in WPE itself.

## Out of scope

Pagination or scrolling at large counts. A card has no minimum size, so 100 hosts become tiny, as before.
