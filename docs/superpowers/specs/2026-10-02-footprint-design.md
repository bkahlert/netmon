# The footprint: apt next to the live stack on a 512 MB board

Date: 2026-10-02. Status: approved design, ready for planning. Continues follow-ups 2 and 3 of
[the tier 2 design](2026-10-01-tier2-vm-end-to-end-design.md).

## Intent

The scanner and the kiosk need about 600 MB on a board with 415 MB. Raspberry Pi OS swaps the difference into zram,
the kernel evicts the shared libraries of WebKit and the JVM and reads them back from the SD card, and apt has no memory
next to the two. The board's device file therefore stops both units around every dpkg run with an apt hook, the fleet
playbook stops them before every update, and the runbook tells everyone to stop the stack before `apt`. The sample
device file in [devices/sample/user-data](../../../devices/sample/user-data) deliberately has no such hook.

The finish line: `apt-get update` followed by `apt-get install --reinstall` of a small package runs on the live board
with the scanner and the kiosk running, without a stop, without thrash, without the hardware watchdog resetting the
board. Then the hook leaves the board's device template, the playbook exception goes, and the runbook's tripwire is
rewritten with the measured numbers.

Three decisions frame the work. The finish line stands, so the heavy levers are planned now rather than after a first
round: the scanner becomes a GraalVM native image and the page gets calmer. The scanner package becomes arm64-only,
since a native image exists for no 32-bit ARM. And the kiosk keeps cog and the page; a native renderer for the panel is
named as the escalation if this design still falls short, not designed here.

Out of scope: the display's rendering differences between cog and WebKit, the `MQTT::Disconnected` header, weekly CI,
and any change to pihero.

## Numbers

Board, 2026-10-02 07:40, two and a half days of uptime, in MB:

| Unit | RAM | zram | total |
|---|---|---|---|
| `netmon-scanner` | 35 | 77 | 112 |
| `pihero-kiosk` | 62 | 194 | 256 |
| both units | 97 | 271 | 368 |
| everything else | about 75 | about 80 | about 155 |

Load was 6.8 with `kswapd0` at 76 % of a core and 44 % iowait. The iowait is the decisive observation: the swap device
is zram in RAM, so iowait can only come from the SD card, which means the kernel is evicting file-backed pages, the
libraries, and reading them back. The budget therefore has to keep hot code resident, not only keep anonymous memory
under the RAM size.

VM, tier 2, 1 GB and no swap: the scanner's `MemoryCurrent` grows from 51 MB after the first scan to 148 MB as the heap
fills its `-Xmx128m`; the kiosk's `MemoryPeak` is 315 MB and its `MemoryCurrent` 227 to 241 MB. That is what the page
wants without any pressure.

The budget: the two units' working sets, anonymous plus file-backed, must fit into the RAM size minus everything else
minus apt's own peak minus a 40 MB margin. On 2026-10-02 the user settled the two terms. Everything else is used RAM
(`MemTotal` minus `MemAvailable`) minus the two units' RAM (`MemoryCurrent`) minus the zram pool (`mem_used_total`), all
within one measure. Apt's term is its anonymous peak from the apt probe, with the cgroup's `MemoryPeak` as the upper
bound. The gate (Task 20) takes "everything else" from the board's last soak row and apt's anonymous peak from the
probe's summary line, the VM's 60 MB until the board's own probe runs. Judging by today's numbers the two units have to
land near 150 to 200 MB, half of what they take. The board baseline below supersedes this estimate. The scanner's share
is its measured steady state after step 3; the kiosk gets the remainder, and the web process's share of that is what its
memory limit is sized from.

### VM, 1 GB, zram swap

The "no swap" above was wrong. The VM image swaps into zram like the board: the soak shows 960 MB of swap free at the
start, and the kiosk's zram share grew from 7 to 80 MB during the five minutes.

VM soak, 2026-10-02, five minutes, finished 09:56. The summary line:

```
soak: scanner peak 123 MB, kiosk peak 371 MB RAM+zram, web process private dirty up to 158 MB, 1.3 major faults/s; table in dist/tier2/soak.md
```

The last row of the table, in MB:

| t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 305 | 122+0 | 72/54 | 233+80 | 114/40 | 92/46 | 535 | 888 | 0.3 | 0 | 0 | 4 | 0.00 |

The VM's `apt-get update` and reinstall of `netmon-display` next to the stack, 2026-10-02, 10:43, took 11 s and 82 major
faults, with a PSI full10 peak of 0.00. The transient unit's `MemoryPeak` was 323874816 bytes, which is 309 MB.
System-wide `available` fell by 3 MB during the run, from 543 to 540 MB. The cgroup's peak is therefore mostly the
package lists and the downloaded deb charged as page cache, which the kernel reclaims under pressure, so the formula's
apt term, taken as `MemoryPeak`, is an upper bound.

### Board baseline

Board soak, 2026-10-02, 11:03 to 11:13, ten minutes, in MB. The summary line:

```
soak: scanner peak 98 MB, kiosk peak 262 MB RAM+zram, web process private dirty up to 61 MB, 2188.5 major faults/s; table in dist/ssh/soak.md
```

| t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | 13+75 | 8/2 | 74+165 | 47/21 | 50/127 | 111 | 109 | 3.6 | 0 | 0 | 0 | n/a |
| 36 | 34+60 | 22/8 | 87+161 | 54/25 | 49/127 | 107 | 100 | 3.5 | 74233 | 70035 | 78138 | n/a |
| 73 | 18+73 | 12/11 | 97+151 | 64/24 | 49/131 | 105 | 93 | 3.0 | 65002 | 69857 | 73890 | n/a |
| 108 | 23+71 | 14/7 | 85+155 | 56/14 | 38/146 | 102 | 86 | 2.9 | 52368 | 52137 | 58670 | n/a |
| 144 | 31+66 | 16/4 | 66+176 | 43/11 | 38/151 | 120 | 66 | 3.5 | 73860 | 80092 | 81586 | n/a |
| 181 | 18+68 | 13/1 | 80+170 | 58/14 | 61/127 | 94 | 88 | 3.5 | 110387 | 106965 | 119266 | n/a |
| 216 | 29+67 | 17/4 | 60+186 | 44/11 | 40/155 | 108 | 67 | 3.3 | 75265 | 81418 | 84529 | n/a |
| 251 | 30+61 | 17/3 | 80+173 | 57/18 | 47/142 | 99 | 83 | 3.8 | 82342 | 78640 | 90674 | n/a |
| 286 | 23+74 | 11/5 | 68+187 | 48/15 | 44/155 | 111 | 57 | 3.6 | 78351 | 84172 | 85945 | n/a |
| 320 | 19+74 | 11/5 | 85+171 | 66/13 | 50/149 | 93 | 76 | 3.1 | 54451 | 50742 | 62131 | n/a |
| 355 | 20+71 | 14/4 | 68+168 | 44/14 | 29/155 | 110 | 81 | 2.2 | 48326 | 52201 | 55052 | n/a |
| 391 | 28+69 | 16/10 | 68+168 | 47/14 | 45/136 | 106 | 82 | 1.9 | 56855 | 56815 | 65507 | n/a |
| 426 | 14+78 | 5/7 | 80+158 | 55/18 | 45/136 | 112 | 81 | 2.4 | 58298 | 59573 | 66396 | n/a |
| 463 | 22+72 | 12/15 | 66+171 | 46/13 | 31/153 | 112 | 78 | 2.5 | 54348 | 53437 | 61791 | n/a |
| 498 | 28+69 | 16/10 | 74+166 | 50/14 | 40/145 | 111 | 74 | 2.7 | 66190 | 69042 | 74109 | n/a |
| 535 | 31+66 | 19/10 | 77+185 | 36/34 | 43/136 | 108 | 73 | 3.5 | 75569 | 75268 | 83525 | n/a |
| 574 | 40+54 | 37/10 | 77+171 | 59/24 | 45/139 | 102 | 82 | 3.5 | 90656 | 89282 | 100877 | n/a |
| 612 | 16+72 | 11/3 | 94+153 | 68/18 | 52/132 | 109 | 74 | 3.4 | 88085 | 88166 | 97903 | n/a |

The board ran at about 2,200 major faults per second, and its PSI is unavailable, shown as n/a.

The budget from the last row, with the two units at 88 + 247 = 335 and `available` at 109, so used memory is 415 - 109 =
306:

```
415 (RAM) - (306 - 335) (everything else) - 309 (apt) - 40 (margin) = 95
```

Everything else comes out at -29 MB. The likely reason is that used memory counts the compressed pages in zram, while
the units' zram figures are probably the uncompressed sizes of what they swapped out. If so, the subtraction mixes two
measures and the 95 is not a reliable budget. The harness samples the zram pool's `mem_used_total` and renders it from
the next soak on, which will confirm or refute this. With the 75 MB of RAM that the 07:40 table gives for everything
else, the same formula yields 415 - 75 - 309 - 40 = -9.

The user decided both open points on 2026-10-02. "Everything else" is used memory minus the units' RAM minus the zram
pool, within one measure, which avoids the -29 MB above. Apt's term is its anonymous peak, which the probe now reads,
and the cgroup's `MemoryPeak` stays as its upper bound, because it includes the page cache that the kernel can
reclaim. It read 309 MB at the first probe and 420 MB today.

### Scanner on the JVM after the slimming

Tier 2 VM, 2026-10-02 14:00. Tier 1 passed with 16 tests, and tier 2 passed with 26 tests and 1 skipped. The boot tests
printed:

```
pihero-kiosk after the first scan: MemoryCurrent=300298240 MemoryPeak=314802176
netmon-scanner after the first scan: MemoryCurrent=36421632 MemoryPeak=36827136
```

This is the JVM baseline before the native image. The scanner's `MemoryCurrent` after the first scan fell from 51 MB on
main to 35 MB.

### Scanner as a native image

Tier 2 VM, 2026-10-02 16:00. The boot tests printed:

```
netmon-scanner after the first scan: MemoryCurrent=5640192 MemoryPeak=5947392
pihero-kiosk after the first scan: MemoryCurrent=291008512 MemoryPeak=315076608
```

The binary built in 42 s with a 2.1 GB peak and is 40 MB. `MemoryCurrent` probably understates the scanner's working
set, because the binary's 40 MB are file-backed pages that dpkg wrote and that are charged outside the scanner's cgroup,
so the board soak of Task 13 also records the process's `smaps_rollup`.

### After the native scanner

The board runs `1.1.1+47.4f1d788`, deployed 2026-10-02 at 16:36. The scanner started with `max heap: 50331648 bytes`,
connected to the broker 3.5 s after the unit started, and published its first scan 13.5 s after that. The installed
tests passed with 15 tests and 4 skipped.

Board soak, 2026-10-02, 16:38 to 16:48, ten minutes. The summary line:

```
soak: scanner peak 27 MB, scanner rss up to 18 MB, kiosk peak 235 MB RAM+zram, web process private dirty up to 84 MB, 798.0 major faults/s; table in dist/ssh/soak.md
```

The last row of the table, in MB:

| t | scanner RAM+zram | scanner anon/file | scanner rss/anon | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 625 | 18+3 | 7/10 | 10/4 | 102+123 | 83/11 | 70/100 | 116 | 190 | 46 | 2.3 | 22754 | 23690 | 29467 | n/a |

Against the baseline's last row, the scanner's RAM+zram fell from 88 to 21 MB and its process rss is 10 MB; the baseline
did not sample the process, so the rss has no earlier figure.

VM soak, 2026-10-02, finished 17:01, ten minutes. The summary line:

```
soak: scanner peak 49 MB, scanner rss up to 37 MB, kiosk peak 475 MB RAM+zram, web process private dirty up to 196 MB, 0.5 major faults/s; table in dist/tier2/soak.md
```

The last row of the table, in MB:

| t | scanner RAM+zram | scanner anon/file | scanner rss/anon | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 610 | 49+0 | 10/36 | 37/10 | 287+188 | 183/29 | 171/151 | 495 | 779 | 56 | 0.2 | 16 | 3427 | 32 | 0.00 |

The scanner's cgroup holds 36 MB of file-backed pages in the VM, the binary resident. On the board it holds 3 to 16 MB
over the rows, probably because the kernel evicts the binary's pages under pressure. The cap below has to leave room
for them.

The scanner's steady RAM+zram on the board is 17 to 22 MB over the last five rows. The cap is the larger of three times
the highest, rounded up to a multiple of 16 MB, and 128 MB, which is the 48 MiB heap cap plus the 40 MB mapped image
plus headroom. A cap below heap plus image would make the kernel push the binary's own pages out to the SD card. The
floor wins, so `MemoryMax` is `128M`, 134217728 bytes:

```
max(ceil(3 x 22 = 66 up to 80), 128) = 128 MB = 134217728 bytes
```

Both soaks above ran under the previous cap of 320 MB, which their tables show as `memory.max=335544320`. The first
deploy of this commit applies 128 MB, and a short board soak then confirms it.
### Kiosk A/Bs in the VM

Tier 2 VM, 2026-10-02 18:00. 1 GB VM, five-minute soaks, the page of this branch. Each soak wrote one variant into
`/etc/pihero/kiosk.conf` on a fresh boot. The soak's summary lines:

```
00-baseline.conf:   scanner peak 47 MB, kiosk peak 254 MB RAM+zram, web process private dirty up to 126 MB, 0.0 major faults/s
01-cog.conf:        scanner peak 47 MB, kiosk peak 252 MB RAM+zram, web process private dirty up to 127 MB, 0.0 major faults/s
02-jit-tiers.conf:  scanner peak 47 MB, kiosk peak 223 MB RAM+zram, web process private dirty up to 117 MB, 0.0 major faults/s
03-jit-off.conf:    scanner peak 47 MB, kiosk peak 207 MB RAM+zram, web process private dirty up to 105 MB, 0.2 major faults/s
04-paint.conf:      scanner peak 47 MB, kiosk peak 229 MB RAM+zram, web process private dirty up to 116 MB, 0.0 major faults/s
```

The last row of each table:

| variant | t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 00-baseline | 305 | 47+0 | 10/36 | 254+0 | 176/39 | 126/0 | 590 | 967 | 1 | 0.1 | 0 | 0 | 0 | 0.00 |
| 01-cog | 305 | 47+0 | 10/36 | 245+0 | 173/44 | 123/0 | 600 | 968 | 0 | 0.5 | 0 | 0 | 0 | 0.00 |
| 02-jit-tiers | 305 | 47+0 | 10/36 | 223+0 | 165/35 | 116/0 | 601 | 968 | 0 | 0.7 | 0 | 0 | 0 | 0.00 |
| 03-jit-off | 305 | 47+0 | 10/36 | 205+0 | 153/32 | 104/0 | 630 | 967 | 1 | 0.7 | 0 | 0 | 0 | 0.00 |
| 04-paint | 305 | 47+0 | 10/36 | 228+0 | 164/40 | 114/0 | 600 | 967 | 0 | 0.6 | 0 | 0 | 0 | 0.00 |

No `WPEWebProcess` appeared among the five top CPU lines at the last sample of any variant, because the page was idle
and every listed process was at 0.0 %CPU. No variant swapped during the soak: swap in and out stayed at 0 throughout,
and PSI full10 stayed at 0.00. Major faults were at most 5 per sample, except a burst of 48 at 214 s in
`03-jit-off.conf`, which gives its 0.2 per second. All five soaks passed and every kiosk came back after the restart.

The conclusions of this first round are superseded by the rerun below, since WebKit relaunched the web process of 01 to
04 every ten seconds; the numbers above stay as the record. The only valid run of the Cog flags alone is the rerun's
`11-cog.conf`: 323 MB RAM+zram and 188 MB private dirty against the baseline's 254 and 126 MB, its private dirty memory
climbing from 122 to 188 MB with the full JIT on. The acceptance tier 2 run with the sample passed 28 tests and 1
skipped and `kiosk.png` showed the hosts. The display test exercises Playwright's WebKit on the Mac through a tunnel,
not the kiosk, so the kiosk's picture is the only check of the page without a JIT.

WebKit's footprint on WPE 2.48.3 is the web process's whole resident set, read from `/proc/self/statm`: 240 to 270 MB in
the VM, libraries included. The soak's private dirty memory is the page's own share of it. `--web-mem-limit=200` puts
the conservative threshold at 66 MB and the strict one at 100 MB, so with any limit below about 500 MiB the web process
is above the strict line on every check, whatever its private dirty memory does. WebKit then runs its critical release
on every ten-second check: it purges caches and style data, discards all JIT and JavaScript code and schedules a full
garbage collection. In the VM the limit's measurable effect is within noise: `13-jit-off.conf` against
`14-no-limit.conf` below, 218 against 222 MB RAM+zram, 104 against 104 MB private dirty and 15.8 against 16.2 s of the
web process's CPU. The board A/B runs the sample's lines with the limit against no limit, judged on memory and on the
web process's CPU time; whether the limit stays is the user's decision after that A/B.

The web-process PID invariant, added to the soak afterwards, failed the first two-minute soak of the sample: the PID
changed between samples, the kiosk's cgroup sat near its 300M cap and the system took 240 to 320 major faults per
sample. In the kept VM the journal shows WebKit killing the web process at its first ten-second check, `Unable to shrink
memory footprint of process (239 MB) below the kill thresold (190 MB). Killed`, and cog relaunching it, every ten
seconds since boot: eight kills in 90 s with the sample as booted, after a restart, and with the Cog flags alone. WPE
2.48.3 takes its footprint from bmalloc, which reads the resident pages in `/proc/self/statm`, so the libraries mapped
into the web process count: 240 to 270 MB against 100 to 124 MB of anonymous memory, with `libWPEWebKit` at 63 MB,
`libLLVM` at 52 MB and `libgallium` at 12 MB resident. Variants 01 to 04 carried the same threshold, and 01 loops in the
kept VM too, so all four ran in the loop unseen; their rows describe a web process at most ten seconds old, and their
higher mean load, 0.31 to 0.42 against the baseline's 0.12, is the relaunches' cost. The sample drops
`--web-kill-threshold`: a threshold that holds would sit between the resident set and `MemoryMax=300M`, where it guards
nothing, and the cgroup's limit stays the leak guard. Without it the web process kept its PID through the two-minute
soak, with about 2 s of CPU per 30-second sample and no major faults after the first. The kiosk's cgroup still reads
293+7 MB, 129 MB of it file pages charged at boot, against 175 MB after a restart. In a single 120 s run in the kept VM
after a restart the web process used 8.8 % of a core, against 6.6 % with no memory flags; the five-minute rerun below
does not show that difference. The soak's summary line:

```
soak: scanner peak 47 MB, kiosk peak 300 MB RAM+zram, web process private dirty up to 93 MB, web process cpu 8.1 s, 0.0 major faults/s
```

**Rerun without the kill threshold.** Tier 2 VM, 2026-10-02 19:23 to 19:53. 1 GB VM, five-minute soaks, the page of this
branch, each variant on a fresh boot and without `--web-kill-threshold`. These four variants supersede 01 to 04 above,
whose web processes WebKit relaunched every ten seconds. `11-cog.conf` is the Cog flags alone, `12-jit-tiers.conf` adds
the two upper JIT tiers off and one painting thread, `13-jit-off.conf` replaces the tiers with the JIT off, and
`14-no-limit.conf` is `13-jit-off.conf` without the memory limit. `00-baseline.conf` is unaffected. The soak also fails
when the web process is replaced, and all four passed. Its summary lines:

```
11-cog.conf:       scanner peak 47 MB, kiosk peak 323 MB RAM+zram, web process private dirty up to 188 MB, web process cpu 17.8 s, 0.8 major faults/s
12-jit-tiers.conf: scanner peak 47 MB, kiosk peak 249 MB RAM+zram, web process private dirty up to 116 MB, web process cpu 15.8 s, 0.0 major faults/s
13-jit-off.conf:   scanner peak 47 MB, kiosk peak 218 MB RAM+zram, web process private dirty up to 104 MB, web process cpu 15.8 s, 0.0 major faults/s
14-no-limit.conf:  scanner peak 47 MB, kiosk peak 222 MB RAM+zram, web process private dirty up to 104 MB, web process cpu 16.2 s, 0.0 major faults/s
```

The last row of each table:

| variant | t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | Δweb cpu | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 11-cog | 306 | 47+0 | 10/36 | 218+41 | 122/10 | 109/7 | 1.9 | 582 | 927 | 12 | 0.0 | 16 | 3952 | 16 | 0.00 |
| 12-jit-tiers | 306 | 47+0 | 10/36 | 248+0 | 158/50 | 107/0 | 1.7 | 611 | 967 | 0 | 0.0 | 0 | 0 | 0 | 0.00 |
| 13-jit-off | 306 | 47+0 | 10/36 | 217+0 | 146/46 | 98/0 | 1.5 | 642 | 968 | 0 | 0.1 | 0 | 0 | 0 | 0.00 |
| 14-no-limit | 306 | 47+0 | 10/36 | 220+0 | 149/48 | 100/0 | 1.2 | 635 | 967 | 0 | 0.3 | 0 | 0 | 0 | 0.00 |

`11-cog.conf`, the one variant without a JIT or painting line, climbed to 323 MB RAM+zram and 188 MB private dirty and
swapped out in five samples, so it joins neither comparison. `13-jit-off.conf` beats `12-jit-tiers.conf` on both kiosk
numbers, 218 against 249 MB RAM+zram and 104 against 116 MB private dirty, and the web process ran the same 15.8 s of
CPU over the run. `13-jit-off.conf` and `14-no-limit.conf`, which differ in the memory limit, peaked at 218 and 222 MB
RAM+zram and at 104 MB private dirty each, and the web process ran 15.8 s of CPU with the limit and 16.2 s without it.

`13-jit-off.conf` beats `12-jit-tiers.conf` on both the kiosk peak RAM+zram and the web private dirty peak, so the
sample keeps `JSC_useJIT=false`. The painting thread line is untested in the VM, since 12 and 13 both carry it; the
board decides whether it earns its place. The memory limit stays in the sample until the board A/B of the limit against
no limit described above.

### After the kiosk changes

The board runs `1.1.1+64.0251dad`, both packages deployed 2026-10-02 at 20:59 from the phase 4 branch. The sample's
five kiosk lines went into `/etc/pihero/kiosk.conf` by hand, the previous file kept as `kiosk.conf.before-phase4`, and
the kiosk was restarted. The two kiosk boot tests passed over SSH: cog's command line carries the four flags and the web
process's environment the two variables. The user judged the panel and kept the configuration.

Board soak, 2026-10-02, 21:03 to 21:14, ten minutes. The summary line:

```
soak: scanner peak 38 MB, kiosk peak 160 MB RAM+zram, web process private dirty up to 49 MB, web process cpu 724.3 s, 260.7 major faults/s; table in dist/ssh/soak.md
```

The last row of the table, in MB, from the harness of the phase 4 branch, which had no scanner rss column yet:

| t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | Δweb cpu | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 631 | 22+5 | 8/13 | 101+59 | 53/42 | 46/34 | 37.3 | 140 | 278 | 34 | 1.5 | 3731 | 3322 | 9116 | n/a |

Against the 16:38 soak after the native scanner: the kiosk's peak fell from 235 to 160 MB RAM+zram, the web process's
private dirty peak from 84 to 49 MB, and major faults from 798 to 261 per second. The scanner's peak rose from 27 to
38 MB, file pages after the fresh installation. The web process ran 724 s of CPU over 631 s, about one core; the 16:38 soak's
top block already showed `WPEWebProcess` at 100 %, so the load predates the kiosk changes. In the VM the same page costs
16 s over a 306 s soak.

Board A/Bs, 21:20 to 21:48, the same boot, ten minutes each, the kiosk restarted between them, the web process's PID
unchanged within every soak. N is the sample's lines with `COG_ARGS="--doc-viewer --webprocess-failure=restart"`, so no
memory limit and no check interval; G is the sample's lines plus `WEBKIT_SKIA_ENABLE_CPU_RENDERING=0`. The summary
lines:

```
N: scanner peak 30 MB, kiosk peak 156 MB RAM+zram, web process private dirty up to 49 MB, web process cpu 706.5 s, 269.1 major faults/s
G: scanner peak 42 MB, kiosk peak 178 MB RAM+zram, web process private dirty up to 59 MB, web process cpu 685.3 s, 24.4 major faults/s
```

The last rows, with the sample's row repeated:

| variant | t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | Δweb cpu | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| the sample | 631 | 22+5 | 8/13 | 101+59 | 53/42 | 46/34 | 37.3 | 140 | 278 | 34 | 1.5 | 3731 | 3322 | 9116 | n/a |
| N, no limit | 630 | 19+2 | 12/6 | 90+64 | 50/33 | 45/36 | 39.7 | 140 | 264 | 34 | 2.3 | 2686 | 4932 | 6907 | n/a |
| G, GPU painting | 619 | 29+0 | 11/18 | 102+70 | 64/32 | 58/43 | 39.5 | 81 | 295 | 33 | 1.4 | 8 | 0 | 26 | n/a |

N against the sample is a tie: the kiosk's peak 156 against 160 MB, the private dirty peak 49 MB in both, faults and CPU
within run-to-run noise. The web process stayed near 76 MB resident, so the 200 MB limit never fired; it stays as the
guard against a leak, which a ten-minute soak cannot show. G cuts major faults about tenfold, 24 against 261 per second,
and costs memory: the kiosk's peak plus 18 MB, the private dirty peak plus 10 MB, `available` 68 to 102 MB against 125
to 157 under the sample. Two of its samples, at t=489 and t=521, show the web process idle for about a minute without a
known cause, and nothing confirmed that painting reached the GPU: the variable reached the web process and the journal
shows no error, but cog names the `modeset` renderer under both configurations and no DRM state was captured for a
comparison. The user decided on 2026-10-02: the board keeps the sample's five lines, the memory limit stays, and GPU
painting is not adopted.

The gate, from the last row of the board soak in the kept configuration: used memory is 415 - 140 = 275 MB, the two
units' RAM 22 + 101 = 123 MB, the zram pool 34 MB, so everything else is 275 - 123 - 34 = 118 MB. Apt's term is the VM's
anonymous peak, 60 MB, until the board's probe runs:

```
415 (RAM) - 118 (everything else) - 60 (apt) - 40 (margin) = 197
```

The two units' RAM+zram in the row is 27 + 160 = 187 MB: within the budget by 10 MB. The kiosk's RAM+zram moved between
130 and 160 MB over the last three rows, so the margin is the size of the row-to-row noise; the probe on the board
decides.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Measurement | A soak and an apt probe as opt-in tests in the testkit, markers `soak` and `apt`, never part of `installed or boot` | The display test and the memory lines already run against the VM and the board over the same harness; every lever is judged by the same numbers in the VM first and on the board second |
| What the soak asserts | Only invariants: both units active, restart counts, boot id and the web process's PID unchanged, no OOM kill in either cgroup | A threshold on a number means nothing before the numbers exist; the table is the result |
| The apt probe's victim | A reinstall of `netmon-display` | Ours, small, and its postinst only restarts lighttpd, so a passing probe proves apt and dpkg next to the stack without touching either unit |
| The hook during the probe | The probe refuses to run while `/etc/apt/apt.conf.d/52netmon-dpkg` exists | The hook would stop the units and make the result meaningless |
| Scanner runtime | GraalVM native image, built by the `native-image` command on the shadow jar inside a container, not by the Gradle plugin | The plugin needs Gradle's Java plugin, which cannot be applied in the Kotlin Multiplatform module; with the slim classpath below the plugin's main value, the reachability metadata repository, is not needed |
| Native image distribution | GraalVM Community 25 from `ghcr.io/graalvm/native-image-community`, pinned by digest; Mandrel 21 as the fallback | Both ship linux-aarch64 and need no license; Community 21 ended in January 2024 |
| CPU target | `-march=compatibility` | GraalVM for JDK 24 and later targets ARMv8.1 by default; the Zero 2 W's Cortex-A53 is ARMv8.0 |
| Heap | A build-time default cap of 64 MB, a runtime `-Xmx48m` in the unit through an options variable, the effective maximum logged at startup | The default maximum heap of a native image is 80 % of RAM; the log line gives tier 1 the same proof the picked-up Java options gave |
| MQTT client | Eclipse Paho MQTT 3, in-memory persistence, automatic reconnect; `tcp://` by default, `ws://` for ports 8080 and 8081 as today; the unit's default broker port becomes 1883 | HiveMQ brings netty, RxJava, Dagger and JCTools, which need pins, substitutions and build-time initialization lists under native image; Paho has no transitive dependencies; the broker's local listener is on the same host |
| Logging | slf4j-simple replaces logback and the logstash encoder; the verbosity tables become the simple logger's per-logger level properties | Joran's reflection and Jackson 3 leave the image; structured arguments only ever rendered through the pattern; runtime level changes are not used |
| nmap output | A StAX parser over nmap's XML yields the hosts directly | Removes the Python child process per scan and the `python3` dependency, and the JSON model that existed only for the converter's output |
| Scan cadence | The pause between scans defaults to 30 s instead of 10 s, overridable as before | nmap runs a third as often; a change still shows within a minute, under the display's two-minute dated threshold |
| Kiosk knobs | Cog arguments through `COG_ARGS` and JavaScriptCore and Skia variables, written into `/etc/pihero/kiosk.conf` by the sample device file | WPE 2.48.3 reads no environment variable for memory limits; the limits are reachable only through cog's flags, and pihero's launcher passes `COG_ARGS` to cog |
| Remote inspector | Switched on by hand in a kept VM when a JavaScript heap figure is wanted, never in the sample | The file users copy is the file tier 2 boots |
| Page | Auto-reload polls once a minute; the radar icons pulse for ten seconds after a scan arrives; cards in the stable section take a one-minute clock; unused Tailwind plugins, prototyping CSS and the headless module go | The feature that reloads the panel after a display upgrade stays, at a twelfth of the requests; the infinite animation goes; the per-second work shrinks to the cards whose texts change; MQTT.js stays as documented |
| Package | `netmon-scanner` is `arch: arm64`, depends on nmap and mosquitto, ships the binary at `/usr/lib/netmon/netmon-scanner` | No JRE, no Python |
| The testkit | Unchanged, pinned at v2.4.0 | Its build module names every deb `_all.deb` and reuses self-built debs; both are harmless here since apt-ftparchive reads the control file and the deploy filter accepts the suffix, and the native build stays out of the testkit's self-build path |
| CI | The tier 1 matrix and the release lose `linux/arm/v7` and the QEMU setup that existed for it | The scanner no longer installs there |
| Version | The release that ships the native scanner is 2.0.0 | Dropping 32-bit ARM and the JRE dependency is a breaking change |
| Gate | After the board soak of step 3 and the A/Bs of step 4, the numbers go against the budget before the finish line is attempted | Short means the table names the side that is over and the escalation is a new brainstorm, not a longer list of knobs |

## Commands

```shell
make build                                   # Gradle, the native binary in a container, then nfpm: dist/*.deb
make soak                                    # boot the VM and sample both units for ten minutes: dist/tier2/soak.md
make soak TARGET=pi@netmon.local             # the same against the board: dist/ssh/soak.md
make apt-probe                               # apt next to the stack in the VM, apt's peak for the budget
make apt-probe TARGET=pi@netmon.local        # the finish line
make vm                                      # keep a VM for A/Bs: edit /etc/pihero/kiosk.conf there, restart, soak
uv run pytest -m soak --target=ssh --target-uri=pi@netmon.local --soak-duration=15m
uv run pytest -m apt --target=ssh --target-uri=pi@netmon.local --apt-timeout=600
```

## Components

### The soak

[tests/test_soak.py](../../../tests/test_soak.py), marked `soak`. The root [conftest.py](../../../conftest.py) registers
the marker and two options, the duration and the interval, defaulting to ten minutes and thirty seconds. The test runs
against the VM and the board and skips on podman, like `boot`.

Each sample reads:

- For both units: `MemoryCurrent`, `MemorySwapCurrent`, `MemoryPeak`, `MemorySwapPeak`, `NRestarts` and `ActiveState`
  from `systemctl show`, and `anon` and `file` from the unit cgroup's `memory.stat`.
- For `WPEWebProcess`: its PID, utime plus stime from `/proc/<pid>/stat`, and `Private_Dirty` and `Swap` from its
  `smaps_rollup`. Private dirty memory is the page's own memory; WebKit's footprint is the whole resident set, libraries
  included, see Kiosk A/Bs in the VM.
- System-wide: `MemAvailable` and `SwapFree` from `/proc/meminfo`, the load average, `pswpin`, `pswpout` and
  `pgmajfault` from `/proc/vmstat` as deltas, `/proc/pressure/memory` where the kernel has it, `mm_stat` of `zram0`
  where it exists, and the top CPU consumers from `top -bn1`.

Once per run it reads each unit cgroup's `memory.max`, so a board whose memory controller is off is noticed rather than
measured wrongly. Raspberry Pi OS boots with the controller off; the sample device file turns it on through
`bootconfig`, and the boot test asserts the kernel line.

Assertions are invariants only: both units active, the kiosk only where `/dev/dri` exists; `NRestarts` and
`/proc/sys/kernel/random/boot_id` unchanged from the first sample to the last; the web process's PID unchanged where the
first sample has one; no increase of `oom_kill` in either unit's `memory.events`. Everything else is reported: a
Markdown table in `dist/tier2/soak.md` for the VM and `dist/ssh/soak.md` for the board, following the display test's
convention, and one summary line in the pytest output the way the boot test prints the memory lines today.

### The apt probe

[tests/test_apt.py](../../../tests/test_apt.py), marked `apt`, with the options `--apt-timeout`, default 300 seconds,
and `--apt-package`, default `netmon-display`. It runs against the VM and the board and skips on podman. It is not
marked `mutating`, because the plugin skips `mutating` tests on the board and the board is where the probe matters.

The probe fails early with a message while the hook file exists on the target. It records the boot id and both
restart counts, then runs `apt-get update` and reinstalls the package inside a transient unit with memory accounting that
remains after exit, so that `MemoryPeak` and the exit status can be read from `systemctl show` afterwards and the unit
is stopped and reset. While apt runs, a thread samples both units every ten seconds with the soak's reader. A sampler
on the target reads the apt unit's anonymous memory every half second, and the summary reports its peak next to the
cgroup's `MemoryPeak`, which includes the page cache.

It asserts: apt exited zero within the timeout; the boot id is unchanged; both units are active with unchanged restart
counts. It reports apt's peak, the wall time, the major-fault delta and the memory pressure during the run. In the VM
this yields apt's peak for the budget. On the board it is the finish line.

### The scanner on the JVM

Four changes that stay testable on the JVM and precede the native image:

- **nmap's XML in-process.** `NmapNetworkScanner` hands nmap's `-oX -` output to a StAX parser that yields `Host`
  instances: the status, the first IPv4 or IPv6 address, the MAC address's vendor, the hostname. `NmapOutput`, its
  unwrapping serializer, `XmlToJsonConverter`, `xml2json.py`, its tier 0 test and the `python3` dependency go. The
  parser's tests feed it the XML the current JSON fixtures were converted from, plus a host without a name, one without
  a MAC, an IPv6 host, a down host and an empty run.
- **Paho.** `MqttPublisher` keeps its constructor and `Publisher` contract on Paho's MQTT 3 client with
  `MemoryPersistence`, automatic reconnect, clean session, and QoS 1 retained publishes. The sealed generic client over
  MQTT 3 and 5 goes; only MQTT 3 was used. The unit's `BROKER_PORT` default becomes 1883. The dependency check at
  planning time confirms that Paho brings nothing else onto the classpath.
- **slf4j-simple.** `Verbosity` and `Debug` set `org.slf4j.simpleLogger.log.<logger>` and the default level as system
  properties before the first logger is created; `LoggingSettings.apply` stays the entry point. The `kv` and `v`
  structured arguments become plain `{}` arguments. The integration tests parse the simple logger's lines instead of
  JSON lines. `Logback.kt` goes.
- **Hygiene.** `Pid` reads `ProcessHandle.current().pid()` without reflection. `ScannerSettings.pauseDuration` defaults
  to 30 s.

### The native image

The Containerfile and a build script live in `packages/netmon-scanner/native/`, a subdirectory, because the testkit
treats a package directory with a `build` file next to a `Containerfile` as self-building. The image starts from
`ghcr.io/graalvm/native-image-community:25` pinned by digest and is built with `--platform linux/arm64` always,
tagged by the Containerfile's digest the way the testkit tags its images. The script runs `native-image` on
`build/libs/netmon-all.jar` and writes `build/native/netmon-scanner`; it sizes the builder's heap from the container's
memory and prints the build's peak.

Build arguments ride in the jar under `META-INF/native-image/com.bkahlert.netmon/netmon-scanner/`: `--no-fallback`,
`-march=compatibility`, `-R:MaxHeapSize=64m`, `--enable-url-protocols=http,https`, and a resource configuration that
includes `assets/device-model-codes.json` and JmDNS's property files. The unreferenced `sfsymbols5` directory is not
included. The https download of the MAC prefixes needs the protocol flag; reflection entries for the protocol handlers
alone do not enable it. Reflection metadata is not empty: Paho loads its logger and its message catalog by reflection,
so both classes need reflection entries and its two message bundles need bundle entries. If tier 1 or tier 2 shows a
missing-metadata failure, the tracing agent in the same container, run with the agent library against a broker, is the
tool that produces it.

The unit runs `ExecStart=/usr/lib/netmon/netmon-scanner $NETMON_SCANNER_OPTIONS` with
`Environment=NETMON_SCANNER_OPTIONS=-Xmx48m`, overridable in `/etc/netmon/scanner.conf` as `JAVA_TOOL_OPTIONS` was.
`Application.start` logs the effective maximum heap in its configuration block. `MemoryMax` is set from the soak as a
leak guard, three times the steady state and never below the heap plus the image. `AmbientCapabilities` stays: nmap
inherits the capabilities across `execve` from a native binary as it did from the JVM.

Tests per tier: the installed tests check the package without a JRE and Python, the binary's start, the broker
connection over 1883, the logged heap cap and the capabilities; tier 2 proves that a scan completes and is published,
that the MAC prefixes download over https, and that the page shows the gateway; the board proves the CPU target.
The VM under hvf runs on the Mac's CPU and cannot catch an ARMv8.1 binary; the tcg VM emulates a Cortex-A72 and
can.

### The kiosk configuration

The sample device file writes these lines into `/etc/pihero/kiosk.conf`, tried in this order, each as an A/B in the kept
VM judged by the soak and `kiosk.png`, then confirmed on the board:

1. `COG_ARGS` with `--doc-viewer`, which selects the document-viewer cache model and switches off the web process's
   memory cache; `--web-mem-limit=<MiB>` with `--web-check-interval=10`; and `--webprocess-failure=restart` to relaunch
   a web process that dies. WebKit's footprint is the web process's resident set, libraries included, so the process
   sits above the limit's strict line on every check, see Kiosk A/Bs in the VM; the board A/B of the limit against no
   limit was a tie and the limit stays as the leak guard, see After the kiosk changes. No `--web-kill-threshold`: a
   threshold that holds would sit between the resident set and `MemoryMax=300M`, where it guards nothing. Without a
   limit cog ignores the other memory flags.
2. `JSC_useDFGJIT=false` and `JSC_useFTLJIT=false` first, then `JSC_useJIT=false`, which puts JavaScriptCore into its
   reduced-memory mode without generational GC. `JSC_logGC=1` once, to see in the journal that the environment reaches
   the web process.
3. `WEBKIT_SKIA_CPU_PAINTING_THREADS=1`. `WEBKIT_SKIA_ENABLE_CPU_RENDERING=0`, GPU painting, was a board-only A/B,
   since the VM paints in software anyway; it cut the card reads tenfold, cost 18 MB of kiosk peak and 60 MB of
   `available`, and is not adopted, see After the kiosk changes.

A new boot test proves the plumbing: cog's command line in `/proc/<pid>/cmdline` carries the configured arguments, and
`WPEWebProcess`'s environment carries the `JSC_` entries.

The remote inspector, `WEBKIT_INSPECTOR_HTTP_SERVER=<ip>:<port>` plus `--enable-developer-extras=true` in `COG_ARGS`,
is set by hand in a kept VM and opened with Playwright's WebKit when a heap figure is wanted.

### The page

- `AutoRefresher` takes its interval as a constructor argument, default one minute.
- A pure function maps the time since the last scan to the radar icons' class: the animation for ten seconds, the
  resting colour afterwards, the dated colour after two minutes as today.
- `hosts()` takes the clock as a parameter; the stable section gets a `CurrentTimeStore` ticking once a minute, the
  recent section keeps the second.
- `@tailwindcss/typography`, `tailwind-heropatterns`, the prototyping blocks in `utils.css` and `dev.fritz2:headless`
  leave the build; the one attribute name taken from headless becomes a literal.

The display test's selectors, `.host[data-status="up"]` and `.font-mono`, stay, so the test guards every page change.
MQTT.js stays. Leaner cards in the stable section are a visible change held back unless the DOM shows up as the memory
in the measurements.

### Packaging and CI

[packages/netmon-scanner/nfpm.yaml](../../../packages/netmon-scanner/nfpm.yaml): `arch: arm64`, `depends` nmap and
mosquitto, `contents` with the binary at `/usr/lib/netmon/netmon-scanner`, mode 0755, in place of the jar. The
description no longer says JVM.

[Makefile](../../../Makefile): a file rule makes `build/native/netmon-scanner` depend on `build/libs/netmon-all.jar`,
so `make build` rebuilds the binary only when the jar changed; `gradle` runs it after the Gradle tasks; `clean` already
removes `build`.

[.github/workflows/ci.yml](../../../.github/workflows/ci.yml) and
[release.yml](../../../.github/workflows/release.yml): the tier 1 matrix is `linux/arm64` only, the
`docker/setup-qemu-action` steps go. Every job that runs `make build` or `make gradle` builds the binary on the arm
runner.

[tests/test_static.py](../../../tests/test_static.py) stubs `/usr/lib/netmon/netmon-scanner` instead of `/usr/bin/java`
for `systemd-analyze verify`; shellcheck covers the native build script; the cloud-init schema check covers the new
kiosk lines.

### The board and choam.de

The board gets the new scanner by `make deploy` and the kiosk lines by editing `/etc/pihero/kiosk.conf` with a kiosk
restart, no reflash. The finish line moves the hook file aside for the probe and puts it back if the probe fails; that
is the one mutation of the live board in this work and is confirmed with the user first.

After a green probe, in the choam.de repository: the device template loses the hook entry and gains the kiosk and
scanner lines; the host variables lose `apt_stop_units`; the runbook's Netmon row describes a binary without a JRE,
its memory section carries the before-and-after table, the "stop the stack before `apt`" tripwire becomes a note with
the measured headroom and apt's timing, and the stop line leaves the command block. The zram tripwire stays.

System levers observed in the soak and decided in the runbook, not here: removing the armhf foreign architecture if no
armhf package is installed, which halves apt's list download and cache build; `tailscaled`'s CPU and memory; the zram
size once the working set fits. The kiosk's `MemoryMax=300M` is pihero's and stays, and it is the kiosk's only leak
guard, since WebKit's kill threshold cannot sit below the web process's resident set.

### Make and documentation

`Makefile` gains `soak` and `apt-probe`. The README's install section says arm64; its build section names the native
step and podman. [devices/README.md](../../../devices/README.md) swaps `JAVA_TOOL_OPTIONS` for `NETMON_SCANNER_OPTIONS`
and names the kiosk lines. This spec's Numbers section receives the budget and the before-and-after table.

## Order of work

One pull request per step, every tier green at every step.

1. **Harness.** The soak, the apt probe, the make targets. The first VM soak and the VM apt peak give the budget, written
   into Numbers. The board baseline soak, which needs the board's SSH key unlocked in KeePassXC.
2. **Scanner on the JVM.** The StAX parser, Paho, slf4j-simple, the process id, the cadence. Still an all-architecture
   jar.
3. **Native image.** Container build, Makefile rule, package architecture, unit, static checks, CI matrix. Deploy to the
   board, the installed tests over SSH, soaks in the VM and on the board.
4. **Kiosk.** The configuration lines in the sample and the page changes, each A/B in the kept VM, then the board's
   configuration by hand.
5. **Finish line.** The gate: the numbers against the budget. Then the hook aside, the apt probe timed. Green: the
   choam.de edits, release 2.0.0, and the board upgraded from the repository next to the live stack, the first real use
   of the new path. Short: the table names the side that is over, and the escalation is a new brainstorm.

## Failure modes

- The native build runs out of memory in the podman machine, 3.8 GB today: the script reports the build's peak; raising
  the machine's memory is the user's decision.
- The binary dies with an illegal instruction on the Cortex-A53: the unit fails at first start after `make deploy`,
  which points at the CPU target; the tcg VM reproduces it.
- Missing reflection or resource metadata: a runtime exception in tier 1 or tier 2, not a build error; the tracing
  agent produces the entry.
- JavaScriptCore without its JIT is too slow for the page: the kiosk's picture in the VM and a look at the panel on the
  board catch it (the display test runs Playwright's WebKit on the Mac, not the kiosk), and the JIT step is dropped.
- The web process kill threshold is set too low: cog restarts the web process after a memory kill and shows its error
  page only after five failures within one second, which memory kills, at least ten seconds apart, never reach, so the
  page reloads forever without a systemd restart. The soak's web-process PID invariant fails on it, and the threshold
  goes back up. The sample's 190 MB did exactly that, and the sample now sets no threshold, see Kiosk A/Bs.
- The apt probe fails: by time, by a reset, or by a stopped unit, each reported distinctly, so the runbook learns which.
- The soak's invariants fail on the board without any change of ours: the baseline is what it is and the number stands
  in the table.

## Follow-ups, in order

1. **Weekly tier 2 CI** under software emulation, follow-up 4 of the tier 2 design.
2. **The testkit names debs by their architecture**, a one-line change in pihero's build module, when a pihero release
   is next due.
3. **The native build as a CI artifact** shared between jobs, if the per-job build time hurts.
4. **The display's rendering differences** seen in tier 2, and the `MQTT::Disconnected` header.
5. **The web process's CPU on the board**, about one core at 800x480 under cog's `modeset` renderer against 5 % in
   the VM, before and after the kiosk changes; the soak's Δweb cpu column shows it.

## Sources

Verified for this design on 2026-10-02 from the WPE WebKit 2.48.3 release tarball, cog 0.18.4, and the GraalVM
documentation. Trixie ships `libwpewebkit-2.0-1` 2.48.3-1 and cog 0.18.4-1.

- WebKit's periodic memory pressure handler, its footprint metric and thresholds:
  `Source/WTF/wtf/MemoryPressureHandler.cpp` and `Source/WTF/wtf/linux/MemoryFootprintLinux.cpp` in the tarball at
  <https://wpewebkit.org/releases/wpewebkit-2.48.3.tar.xz>; the UI process monitor polling `/proc/meminfo` and the
  cgroup: <https://github.com/WebKit/WebKit/blob/wpewebkit-2.48.3/Source/WebKit/UIProcess/linux/MemoryPressureMonitor.cpp>.
- The environment variables WPE reads: `Source/WebKit/glib/environment-variables.md.in` in the tarball; the downstream
  variables that do not exist upstream: <https://github.com/Igalia/cog/discussions/724>.
- JavaScriptCore's `JSC_` options: `Source/JavaScriptCore/runtime/Options.cpp` and `OptionsList.h` in the tarball.
- Skia CPU painting and its thread variables: `Source/WebKit/WebProcess/glib/WebProcessGLib.cpp` and
  `Source/WebCore/platform/graphics/skia/SkiaPaintingEngine.cpp` in the tarball.
- Cog's memory flags and `--doc-viewer`: <https://github.com/Igalia/cog/blob/0.18.4/launcher/cog-launcher.c>.
- Raspberry Pi OS and the memory controller: <https://github.com/RPi-Distro/pi-gen/issues/917>.
- No 32-bit ARM in native image:
  <https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/Platform.java>;
  the ARMv8.1 default and `-march=compatibility`:
  <https://github.com/oracle/graal/blob/master/substratevm/CHANGELOG.md> and
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/OptimizationsAndPerformance.md>;
  the Cortex-A53: <https://www.raspberrypi.com/documentation/computers/processors.html>.
- Native image memory management, the 80 % default and `-R:MaxHeapSize`:
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/MemoryManagement.md>; build resources:
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md>; the tracing agent:
  <https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/>.
- The Gradle plugin's dependence on the Java plugin and its incompatibility with Multiplatform modules:
  <https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/src/main/java/org/graalvm/buildtools/gradle/NativeImagePlugin.java>
  and <https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html>.
- Distributions: <https://github.com/graalvm/graalvm-ce-builds/releases> and <https://github.com/graalvm/mandrel/releases>.
- HiveMQ's client under native image, the precedent and its cost:
  <https://github.com/hivemq/mqtt-cli/blob/master/build.gradle.kts>,
  <https://github.com/hivemq/hivemq-mqtt-client/issues/467>, <https://github.com/hivemq/hivemq-mqtt-client/issues/578>;
  the reachability metadata repository: <https://github.com/oracle/graalvm-reachability-metadata>.
- Paho: <https://github.com/eclipse-paho/paho.mqtt.java>, release 1.2.5.
