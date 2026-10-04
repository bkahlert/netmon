package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"math"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	metricspb "go.opentelemetry.io/proto/otlp/metrics/v1"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

var update = flag.Bool("update", false, "rewrite testdata/metrics.json")

const golden = "testdata/metrics.json"

func TestRequest(t *testing.T) {
	cur, prev, o := goldenSnapshots()

	t.Run("matches the golden message", func(t *testing.T) {
		got := request(cur, &prev, o)

		if *update {
			b, err := protojson.MarshalOptions{UseEnumNumbers: true, Multiline: true, Indent: "  "}.Marshal(got)
			if err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(golden, append(b, '\n'), 0o644); err != nil {
				t.Fatal(err)
			}
		}
		b, err := os.ReadFile(golden)
		if err != nil {
			t.Fatal(err)
		}
		var want metricspb.MetricsData
		if err := protojson.Unmarshal(b, &want); err != nil {
			t.Fatal(err)
		}
		if !proto.Equal(got, &want) {
			t.Fatalf("%s is out of date; run go test ./... -update", golden)
		}
	})

	t.Run("marshals to OTLP/JSON", func(t *testing.T) {
		b, err := marshal(request(cur, &prev, o))

		equal(t, err, nil)
		var compact bytes.Buffer
		if err := json.Compact(&compact, b); err != nil {
			t.Fatal(err)
		}
		text := compact.String()
		for _, want := range []string{`"aggregationTemporality":2`, `"timeUnixNano":"1759450005000000000"`, `"schemaUrl":"https://opentelemetry.io/schemas/1.43.0"`, `"asInt":"160000000"`} {
			if !strings.Contains(text, want) {
				t.Errorf("missing %s", want)
			}
		}
		var back metricspb.MetricsData
		equal(t, protojson.Unmarshal(b, &back), nil)
		equal(t, proto.Equal(&back, request(cur, &prev, o)), true)
	})

	t.Run("host", func(t *testing.T) {
		got := request(cur, &prev, o)

		r := resource(t, got, "service.name", "netmon-metrics")
		equal(t, attribute(r, "host.name"), "netmon")
		equal(t, attribute(r, "host.boot.id"), "6f1c2a8e-2c4b-4a51-9d3e-0b7c1e2f3a4d")
		equal(t, attribute(r, "service.version"), "v3.0.0")
		equal(t, r.SchemaUrl, "https://opentelemetry.io/schemas/1.43.0")
		equal(t, r.ScopeMetrics[0].Scope.Name, "netmon-metrics")
		faults := metric(t, r, "system.paging.faults")
		equal(t, faults.GetSum().AggregationTemporality, metricspb.AggregationTemporality_AGGREGATION_TEMPORALITY_CUMULATIVE)
		equal(t, faults.GetSum().IsMonotonic, true)
		equal(t, faults.GetSum().DataPoints[0].StartTimeUnixNano, uint64(o.boot.UnixNano()))
		equal(t, faults.GetSum().DataPoints[0].GetAsInt(), int64(126517772))
		equal(t, point(t, r, "system.paging.usage", "system.paging.state", "used").GetAsInt(), int64(41943040))
		near(t, point(t, r, "system.linux.memory.pressure.stall_time", "kind", "full").GetAsDouble(), 4.25)
		equal(t, metric(t, r, "system.linux.memory.pressure.stall_time").Unit, "s")
		equal(t, metric(t, r, "system.paging.operations").Unit, "{operation}")
		equal(t, faults.Unit, "{fault}")
		cpus := metric(t, r, "system.cpu.logical.count")
		equal(t, cpus.Unit, "{cpu}")
		equal(t, cpus.GetSum().GetDataPoints()[0].GetAsInt(), int64(4))
		limit := metric(t, r, "system.memory.limit").GetSum()
		equal(t, limit.AggregationTemporality, metricspb.AggregationTemporality_AGGREGATION_TEMPORALITY_CUMULATIVE)
		equal(t, limit.IsMonotonic, false)
		equal(t, limit.DataPoints[0].StartTimeUnixNano, uint64(o.boot.UnixNano()))
		equal(t, metric(t, r, "system.linux.cpu.load_1m").GetGauge() != nil, true)
	})

	t.Run("unit", func(t *testing.T) {
		got := request(cur, &prev, o)

		r := unitResource(t, got, "pihero-kiosk.service")
		equal(t, point(t, r, "systemd.unit.state", "systemd.unit.active_state", "active").GetAsInt(), int64(1))
		equal(t, point(t, r, "systemd.unit.state", "systemd.unit.active_state", "failed").GetAsInt(), int64(0))
		cpu := metric(t, r, "systemd.unit.cpu.time").GetSum().DataPoints[0]
		near(t, cpu.GetAsDouble(), 6.9)
		equal(t, cpu.StartTimeUnixNano, uint64(o.boot.Add(40*time.Second).UnixNano()))
		near(t, metric(t, r, "systemd.unit.cpu.utilization").GetGauge().DataPoints[0].GetAsDouble(), 0.295)
		equal(t, point(t, r, "systemd.unit.memory.usage", "type", "swap").GetAsInt(), int64(8820736))
		usage := metric(t, r, "systemd.unit.memory.usage").GetSum()
		equal(t, usage.IsMonotonic, false)
		equal(t, usage.DataPoints[0].StartTimeUnixNano, uint64(o.boot.Add(40*time.Second).UnixNano()))
		equal(t, metric(t, r, "systemd.unit.memory.peak").GetGauge() != nil, true)
		equal(t, metric(t, r, "systemd.unit.restarts").Unit, "{restart}")
		equal(t, metric(t, r, "systemd.unit.memory.oom_kills").Unit, "{kill}")
	})

	t.Run("process", func(t *testing.T) {
		got := request(cur, &prev, o)

		r := resource(t, got, "process.executable.name", "WPEWebProcess")
		equal(t, attribute(r, "systemd.unit.name"), "pihero-kiosk.service")
		equal(t, attribute(r, "process.pid"), "1234")
		near(t, point(t, r, "process.cpu.time", "cpu.mode", "system").GetAsDouble(), 1.7)
		near(t, metric(t, r, "process.cpu.utilization").GetGauge().DataPoints[0].GetAsDouble(), 0.285)
		equal(t, point(t, r, "process.linux.memory.usage", "type", "anon").GetAsInt(), int64(63504384))
		rss := metric(t, r, "process.memory.usage").GetSum()
		equal(t, rss.IsMonotonic, false)
		equal(t, rss.DataPoints[0].StartTimeUnixNano, uint64(o.boot.Add(45*time.Second).UnixNano()))
	})

	t.Run("utilization", func(t *testing.T) {
		t.Run("on a first sample", func(t *testing.T) {
			t.Run("is absent", func(t *testing.T) {
				got := request(cur, nil, o)

				absent(t, unitResource(t, got, "pihero-kiosk.service"), "systemd.unit.cpu.utilization")
				absent(t, resource(t, got, "process.executable.name", "WPEWebProcess"), "process.cpu.utilization")
			})
		})

		t.Run("on a restarted unit", func(t *testing.T) {
			t.Run("is absent", func(t *testing.T) {
				restarted := cur
				restarted.units = append([]unitReading{}, cur.units...)
				state := restarted.units[1].state.value
				state.since = o.boot.Add(4000 * time.Second)
				restarted.units[1].state = some(state)

				got := request(restarted, &prev, o)

				absent(t, unitResource(t, got, "pihero-kiosk.service"), "systemd.unit.cpu.utilization")
			})
		})

		t.Run("on a replaced web process", func(t *testing.T) {
			t.Run("is absent", func(t *testing.T) {
				replaced := cur
				replaced.processes = append([]processReading{}, cur.processes...)
				replaced.processes[0].pid = 1985
				replaced.processes[0].start = o.boot.Add(4000 * time.Second)
				replaced.processes[0].user, replaced.processes[0].system = 100*time.Millisecond, 0

				got := request(replaced, &prev, o)

				absent(t, resource(t, got, "process.executable.name", "WPEWebProcess"), "process.cpu.utilization")
			})
		})

		t.Run("on a counter below the previous one", func(t *testing.T) {
			t.Run("is absent", func(t *testing.T) {
				behind := cur
				behind.units = append([]unitReading{}, cur.units...)
				behind.units[1].cpu = some(500 * time.Millisecond)

				got := request(behind, &prev, o)

				absent(t, unitResource(t, got, "pihero-kiosk.service"), "systemd.unit.cpu.utilization")
			})
		})
	})

	t.Run("on a unit without a state", func(t *testing.T) {
		t.Run("has its memory usage without a start time", func(t *testing.T) {
			stateless := cur
			stateless.units = append([]unitReading{}, cur.units...)
			stateless.units[1].state = opt[unitState]{}

			got := request(stateless, &prev, o)

			r := unitResource(t, got, "pihero-kiosk.service")
			equal(t, point(t, r, "systemd.unit.memory.usage", "type", "ram").StartTimeUnixNano, uint64(0))
			absent(t, r, "systemd.unit.cpu.time")
		})
	})

	t.Run("on absent sources", func(t *testing.T) {
		t.Run("leaves out their points and an empty resource", func(t *testing.T) {
			bare := snapshot{at: cur.at, units: []unitReading{{name: "pihero-kiosk.service"}}}

			got := request(bare, nil, o)

			equal(t, len(got.ResourceMetrics), 1)
			r := resource(t, got, "service.name", "netmon-metrics")
			equal(t, len(r.ScopeMetrics[0].Metrics), 0)
		})
	})
}

func goldenSnapshots() (cur, prev snapshot, o origin) {
	boot := time.Unix(1759400000, 0)
	o = origin{node: "netmon", version: "v3.0.0", boot: boot}
	host := hostReading{
		bootID:  some("6f1c2a8e-2c4b-4a51-9d3e-0b7c1e2f3a4d"),
		cpus:    some(int64(4)),
		meminfo: map[string]int64{"MemTotal": 435159040, "MemAvailable": 97599488, "SwapTotal": 209715200, "SwapFree": 167772160},
		vmstat:  map[string]int64{"pswpin": 117856217, "pswpout": 98765, "pgmajfault": 126517772},
		load1:   some(3.1),
		stall:   map[string]time.Duration{"some": 12500 * time.Millisecond, "full": 4250 * time.Millisecond},
		zram:    some(int64(69357568)),
	}
	scanner := unitReading{
		name:  "netmon-scanner.service",
		state: some(unitState{active: "active", restarts: 0, since: boot.Add(30 * time.Second)}),
		cpu:   some(120 * time.Second),
		ram:   some(int64(41943040)), swap: some(int64(31457280)), ramPeak: some(int64(62914560)), swapPeak: some(int64(41943040)),
		stat:   map[string]int64{"anon": 31457280, "file": 10485760},
		events: map[string]int64{"oom_kill": 0},
	}
	kiosk := unitReading{
		name:  "pihero-kiosk.service",
		state: some(unitState{active: "active", restarts: 1, since: boot.Add(40 * time.Second)}),
		cpu:   some(6900 * time.Millisecond),
		ram:   some(int64(160000000)), swap: some(int64(8820736)), ramPeak: some(int64(323874816)), swapPeak: some(int64(125829120)),
		stat:   map[string]int64{"anon": 52428800, "file": 20971520},
		events: map[string]int64{"oom_kill": 0},
	}
	web := processReading{
		unit: "pihero-kiosk.service", comm: "WPEWebProcess", pid: 1234, start: boot.Add(45 * time.Second),
		user: 6 * time.Second, system: 1700 * time.Millisecond,
		status: map[string]int64{"VmRSS": 120586240, "RssAnon": 63504384, "RssFile": 52428800, "VmSwap": 115343360},
	}
	scannerProcess := processReading{
		unit: "netmon-scanner.service", comm: "netmon-scanner", pid: 812, start: boot.Add(31 * time.Second),
		user: 100 * time.Second, system: 20 * time.Second,
		status: map[string]int64{"VmRSS": 47185920, "RssAnon": 32505856, "RssFile": 14680064, "VmSwap": 0},
	}
	cur = snapshot{at: time.Unix(1759450005, 0), host: host, units: []unitReading{scanner, kiosk}, processes: []processReading{web, scannerProcess}}

	prevScanner, prevKiosk := scanner, kiosk
	prevScanner.cpu = some(119500 * time.Millisecond)
	prevKiosk.cpu = some(1 * time.Second)
	prevWeb, prevScannerProcess := web, scannerProcess
	prevWeb.user, prevWeb.system = 1500*time.Millisecond, 500*time.Millisecond
	prevScannerProcess.user, prevScannerProcess.system = 99600*time.Millisecond, 19900*time.Millisecond
	prev = snapshot{at: time.Unix(1759450000, 0), host: host, units: []unitReading{prevScanner, prevKiosk}, processes: []processReading{prevWeb, prevScannerProcess}}
	return cur, prev, o
}

func resource(t *testing.T, req *metricspb.MetricsData, key, value string) *metricspb.ResourceMetrics {
	t.Helper()
	for _, r := range req.ResourceMetrics {
		if attribute(r, key) == value {
			return r
		}
	}
	t.Fatalf("no resource with %s=%s", key, value)
	return nil
}

func unitResource(t *testing.T, req *metricspb.MetricsData, unit string) *metricspb.ResourceMetrics {
	t.Helper()
	for _, r := range req.ResourceMetrics {
		if attribute(r, "systemd.unit.name") == unit && attribute(r, "process.pid") == "" {
			return r
		}
	}
	t.Fatalf("no resource of unit %s", unit)
	return nil
}

func attribute(r *metricspb.ResourceMetrics, key string) string {
	for _, kv := range r.Resource.Attributes {
		if kv.Key == key {
			if s := kv.Value.GetStringValue(); s != "" {
				return s
			}
			return strconv.FormatInt(kv.Value.GetIntValue(), 10)
		}
	}
	return ""
}

func metric(t *testing.T, r *metricspb.ResourceMetrics, name string) *metricspb.Metric {
	t.Helper()
	for _, m := range r.ScopeMetrics[0].Metrics {
		if m.Name == name {
			return m
		}
	}
	t.Fatalf("no metric %s", name)
	return nil
}

func absent(t *testing.T, r *metricspb.ResourceMetrics, name string) {
	t.Helper()
	for _, m := range r.ScopeMetrics[0].Metrics {
		if m.Name == name {
			t.Fatalf("unexpected metric %s", name)
		}
	}
}

func point(t *testing.T, r *metricspb.ResourceMetrics, name, key, value string) *metricspb.NumberDataPoint {
	t.Helper()
	m := metric(t, r, name)
	points := m.GetGauge().GetDataPoints()
	if m.GetSum() != nil {
		points = m.GetSum().GetDataPoints()
	}
	for _, p := range points {
		for _, kv := range p.Attributes {
			if kv.Key == key && kv.Value.GetStringValue() == value {
				return p
			}
		}
	}
	t.Fatalf("no point of %s with %s=%s", name, key, value)
	return nil
}

func near(t *testing.T, got, want float64) {
	t.Helper()
	if math.Abs(got-want) > 1e-9 {
		t.Fatalf("got %v, want %v", got, want)
	}
}
