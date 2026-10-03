# The display's page load and runtime cost

## Intent

A trace of the page in a desktop browser found a 47 ms evaluation of the bundle, 1.4 MB transferred, 37.1 kB of legacy
JavaScript, 1.2 MB without long-lived caching, and the loading image as the largest paint. The board's web process also
burns 93 to 128 % of one core with the page idle. This change decides what of that pays on a Pi Zero 2 W behind
lighttpd, shown by cog, and measures it in the VM kiosk, which runs the board's WPE WebKit 2.48.3.

## What the kiosk does with each finding

| Finding | In the kiosk | Decision |
|---|---|---|
| Legacy JavaScript, 37.1 kB | Kotlin/JS compiled to ES5: constructors, prototypes, no arrow functions | Tried ES2015 and dropped it, see ES2015 below |
| 1.4 MB transferred | Loopback, nothing compressed; 3.65 MB on disk before (the source map was 2.28 MB of it), 1.34 MB after | No compression: it would spend the board's CPU on a copy through the loopback device |
| Long-lived caching | cog runs with `--doc-viewer`, the document viewer cache model: a second start of the kiosk fetched the immutable bundle again, see Measurements | Do it anyway for any other browser that opens the page, and say that the kiosk gains nothing |
| Serve some resources separately | The bundle is 1.0 MB, MQTT.js 321 kB of it | No split, see below |
| Loading image as the largest paint | A CSS background of the empty page, a 3 kB SVG | `rel=preload` with `fetchpriority=high`; the kiosk fetches it twice because it has no cache |
| Production mode, dead code | webpack already runs in production mode; the Kotlin compiler removes unreachable code per module | Nothing to change |

### Why no vendor chunk

MQTT.js is the largest single part of the bundle (321 kB of 1.0 MB), and a chunk of its own is cached separately in a
browser that caches. The kiosk loads the page once per boot from the same machine and does not cache, the browser still
parses and runs every byte of the chunk, and the extra script must be ordered before the bundle in `index.html`, found
by the auto refresh, and hashed. It saves nothing here and adds two requests.

## The files

- The bundle, the images and the model codes carry an eight character content hash:
  `webpack.config.d/hashed-names.js` and the asset generators, in production mode only, so the development server keeps
  its plain names.
- `jsBrowserDistribution` drops the unhashed copies the resources bring along and the source map, and points
  `index.html` at the hashed names. It fails when it does not find exactly one bundle and one loading image.
- lighttpd's `90-netmon.conf` loads `mod_setenv` and answers the hashed files with
  `public, max-age=31536000, immutable`, and `/`, `index.html` and `stats.json` with `no-cache`.

### The auto refresh

[AutoRefreshers.kt](../../../src/jsMain/kotlin/com/bkahlert/kommons/browser/AutoRefreshers.kt) polls the ETags of the
page and of the scripts the page names once a minute and reloads when one changes. Content hashes change the script's
URL, so after an upgrade the poll of the old script gets a 404 and changes nothing, and the page's own ETag changes and
reloads it. In the VM, with lighttpd's access log on, an upgrade of the package at 07:34:56 was followed by the poll at
07:35:08, a reload, and requests for the new bundle in the same second.

A pre-existing gap remains: the first poll of a page sets the ETag it compares against. An upgrade in the minute between
the page's load and its first poll sets the baseline to the new page, and the old code stays on the panel. The first
test run of this change fell into it. It needs a compare against the script the page was loaded with and is left open.

## ES2015

Tried and dropped. `compilerOptions.target = "es2015"` shrinks the bundle from 1181206 to 1000770 bytes, and 14 % of
that is Kotlin's generator-based suspend functions. In the VM kiosk, which runs without a JIT, the page then costs 1.2
points of CPU more (11.5 against 10.2, two runs each), because the interpreter runs generators slower than the ES5
state machines. With `-Xes-generators=false` the cost is gone (10.6) and so is most of the gain (1126906 bytes against
1152050). The change needs three more things, and a branch with all of them is kept as `backup/page-load-es2015`:

- The import of the MQTT.js build needs its extension: `@JsModule("./mqtt.js")`.
- Tailwind scans the compiler's output for class names and must be told about `.mjs`, or it purges every utility class
  that only the Kotlin code uses. The layout test checks a border and a radius that come from such classes.
- A lambda that reads `arguments` sees the enclosing function's, not its own. The MQTT event flow used one and received
  the coroutine instead of the topic.

## The loading animation

The splash is an SVG with a full-screen animated gradient that WebKit paints on the CPU. In the VM it took 117 % of a
core for as long as it showed. Three turns of 11 s now move it while the page boots, then it holds still. A layout test
checks that none of its animations repeats forever.

## Measurements

Tier 2 VM, 2 vCPUs, 1 GB, the kiosk configuration of the sample device file (no JIT), 53 hosts published by a feeder
every 37 s as the board's scanner does, windows of 120 s after 80 s of settling, share of one core of all threads of the
web process, one value per run. The same page varied by up to 0.6 points between runs and 0.9 between VM boots.

| Page | All threads | Main thread |
|---|---|---|
| 2.1.1 | 9.4, 9.9, 9.8, 9.0, 9.6 | 5.0 to 5.4 |
| css layout | 10.2, 10.1, 10.3 | 5.5 |
| this branch, as shipped | 10.2, 9.0 | 5.3, 4.9 |
| ES2015 with generators, dropped | 11.5, 11.4, 11.5 | 5.8 |
| ES2015 without generators, dropped | 10.3, 10.9 | 5.5 |
| ES2015 with generators, animations off | 8.8 | 5.4 |
| ES2015 with generators, no scans arriving | 5.9 | 3.7 |
| ES2015 with generators, no polling of `stats.json` or scans | 5.4 | 3.4 |

The layout and the load changes do not lower the web process's CPU in the VM: the two pages are within the noise, the
CSS layout perhaps half a point above 2.1.1 because it draws larger cards. A build with the clock slowed to once a minute
cost the same as one with a second, and one with `distinctUntilChanged` on the clock texts cost the same, so neither is
the cost. What the VM does show:

- The radar pulse, 10 s after each scan, costs about a quarter of the page's CPU (11.5 against 8.8 points). The 4 steps
  of its keyframes do not reduce the 60 frames per second WebKit ticks it at.
- Each scan costs about 2 points of the main thread (5.9 with scans, 3.7 without). Decoding it is 12.5 ms of that: the
  rest is spread over many small tasks; in the one 10 s window read, the event loop lagged by at most 16 ms.
- The splash, see above: 117 % of a core before, 2 % after 35 s.
- Time from the start of the page to the first host card: 428 and 443 ms with 2.1.1, 403 and 396 ms with ES2015.
- The old fit left the page unreadable in the VM kiosk with 53 hosts: the icons squeezed into one strip and the text
  lines zoomed away. The new layout draws all of them.

The requests of one start of the kiosk, from lighttpd's access log: the page twice, the loading image twice, the bundle
and the model codes once. The second start fetched the same, immutable bundle again.

## Open

- The web process's main thread on the board (98 % in `top -H`) is not explained by any of this. A profile of the main
  thread in the VM kiosk would name it. `WEBKIT_INSPECTOR_HTTP_SERVER` accepted a connection in the VM but answered
  nothing in 8 s.
- Slowing the radar pulse and the host glow to a few frames per second would save most of the animation cost and changes
  how they look.
- The poll baseline gap in the auto refresh, above.
