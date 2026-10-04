package main

import (
	"time"

	semconv "go.opentelemetry.io/otel/semconv/v1.43.0"
	commonpb "go.opentelemetry.io/proto/otlp/common/v1"
	metricspb "go.opentelemetry.io/proto/otlp/metrics/v1"
	resourcepb "go.opentelemetry.io/proto/otlp/resource/v1"
	"google.golang.org/protobuf/encoding/protojson"
)

const serviceName = "netmon-metrics"

// origin is who publishes: the node of the topic, the build version, and the boot time the host's counters start at.
type origin struct {
	node    string
	version string
	boot    time.Time
}

// activeStates are the points of systemd.unit.state, as the collector-contrib systemd receiver publishes them.
var activeStates = []string{"active", "reloading", "inactive", "failed", "activating", "deactivating"}

// request returns cur as one message, with utilization against prev where prev holds the same unit or process run.
// MetricsData encodes as the body of /v1/metrics; the collector package's ExportMetricsServiceRequest would link grpc.
func request(cur snapshot, prev *snapshot, o origin) *metricspb.MetricsData {
	req := &metricspb.MetricsData{}
	add := func(attributes []*commonpb.KeyValue, metrics metricList, always bool) {
		if len(metrics) == 0 && !always {
			return
		}
		req.ResourceMetrics = append(req.ResourceMetrics, &metricspb.ResourceMetrics{
			Resource:     &resourcepb.Resource{Attributes: attributes},
			ScopeMetrics: []*metricspb.ScopeMetrics{{Scope: &commonpb.InstrumentationScope{Name: serviceName, Version: o.version}, Metrics: metrics}},
			SchemaUrl:    semconv.SchemaURL,
		})
	}
	// The host is always there, so a consumer tells a message without figures from no message at all.
	add(hostAttributes(cur.host, o), hostMetrics(cur, o), true)
	for _, u := range cur.units {
		add([]*commonpb.KeyValue{text("host.name", o.node), text("systemd.unit.name", u.name)}, unitMetrics(cur, prev, u, o), false)
	}
	for _, p := range cur.processes {
		add([]*commonpb.KeyValue{text("host.name", o.node), text("systemd.unit.name", p.unit), integer("process.pid", p.pid), text("process.executable.name", p.comm)}, processMetrics(cur, prev, p), false)
	}
	return req
}

// marshal returns data as OTLP/JSON, which wants enums as numbers rather than protojson's default names.
func marshal(data *metricspb.MetricsData) ([]byte, error) {
	return protojson.MarshalOptions{UseEnumNumbers: true}.Marshal(data)
}

func hostAttributes(h hostReading, o origin) []*commonpb.KeyValue {
	attributes := []*commonpb.KeyValue{text("host.name", o.node), text("service.name", serviceName), text("service.version", o.version)}
	if h.bootID.ok {
		attributes = append(attributes, text("host.boot.id", h.bootID.value))
	}
	return attributes
}

func hostMetrics(s snapshot, o origin) metricList {
	h, at := s.host, s.at
	var ms metricList
	ms.upDownCounter("system.cpu.logical.count", "{cpu}", since(o.boot, intOf(h.cpus, at)))
	ms.upDownCounter("system.memory.limit", "By", since(o.boot, intIn(h.meminfo, "MemTotal", at)))
	ms.upDownCounter("system.memory.linux.available", "By", since(o.boot, intIn(h.meminfo, "MemAvailable", at)))
	var swapUsed opt[int64]
	if total, ok := h.meminfo["SwapTotal"]; ok {
		if free, ok := h.meminfo["SwapFree"]; ok {
			swapUsed = some(total - free)
		}
	}
	ms.upDownCounter("system.paging.usage", "By",
		since(o.boot, intOf(swapUsed, at, text("system.paging.state", "used"))),
		since(o.boot, intIn(h.meminfo, "SwapFree", at, text("system.paging.state", "free"))))
	ms.counter("system.paging.operations", "{operation}",
		since(o.boot, intIn(h.vmstat, "pswpin", at, text("system.paging.direction", "in"))),
		since(o.boot, intIn(h.vmstat, "pswpout", at, text("system.paging.direction", "out"))))
	ms.counter("system.paging.faults", "{fault}", since(o.boot, intIn(h.vmstat, "pgmajfault", at, text("system.paging.fault.type", "major"))))
	ms.gauge("system.linux.cpu.load_1m", "1", doubleOf(h.load1, at))
	ms.counter("system.linux.memory.pressure.stall_time", "s",
		since(o.boot, secondsIn(h.stall, "some", at, text("kind", "some"))),
		since(o.boot, secondsIn(h.stall, "full", at, text("kind", "full"))))
	ms.upDownCounter("system.linux.zram.memory.usage", "By", since(o.boot, intOf(h.zram, at)))
	return ms
}

func unitMetrics(cur snapshot, prev *snapshot, u unitReading, o origin) metricList {
	at := cur.at
	var ms metricList
	// The cgroup's figures start over with every start of the unit, so without its start they have no start time.
	var started time.Time
	if u.state.ok {
		state := u.state.value
		started = state.since
		var points []*metricspb.NumberDataPoint
		for _, active := range statesWith(state.active) {
			value := int64(0)
			if active == state.active {
				value = 1
			}
			points = append(points, intPoint(at, value, text("systemd.unit.active_state", active)))
		}
		ms.gauge("systemd.unit.state", "1", points...)
		ms.counter("systemd.unit.restarts", "{restart}", since(o.boot, intPoint(at, state.restarts)))
		if u.cpu.ok {
			ms.counter("systemd.unit.cpu.time", "s", since(state.since, doublePoint(at, u.cpu.value.Seconds())))
			if before, ok := previousUnit(prev, u.name, state.since); ok && before.cpu.ok {
				ms.gauge("systemd.unit.cpu.utilization", "1", doubleOf(utilization(u.cpu.value, before.cpu.value, at.Sub(prev.at), cur.host.cpus), at))
			}
		}
		ms.counter("systemd.unit.memory.oom_kills", "{kill}", since(state.since, intIn(u.events, "oom_kill", at)))
	}
	ms.upDownCounter("systemd.unit.memory.usage", "By",
		since(started, intOf(u.ram, at, text("type", "ram"))),
		since(started, intOf(u.swap, at, text("type", "swap"))),
		since(started, intIn(u.stat, "anon", at, text("type", "anon"))),
		since(started, intIn(u.stat, "file", at, text("type", "file"))))
	ms.gauge("systemd.unit.memory.peak", "By",
		intOf(u.ramPeak, at, text("type", "ram")),
		intOf(u.swapPeak, at, text("type", "swap")))
	return ms
}

func processMetrics(cur snapshot, prev *snapshot, p processReading) metricList {
	at := cur.at
	var ms metricList
	ms.counter("process.cpu.time", "s",
		since(p.start, doublePoint(at, p.user.Seconds(), text("cpu.mode", "user"))),
		since(p.start, doublePoint(at, p.system.Seconds(), text("cpu.mode", "system"))))
	if before, ok := previousProcess(prev, p.pid, p.start); ok {
		ms.gauge("process.cpu.utilization", "1", doubleOf(utilization(p.user+p.system, before.user+before.system, at.Sub(prev.at), cur.host.cpus), at))
	}
	ms.upDownCounter("process.memory.usage", "By", since(p.start, intIn(p.status, "VmRSS", at)))
	ms.upDownCounter("process.linux.memory.usage", "By",
		since(p.start, intIn(p.status, "RssAnon", at, text("type", "anon"))),
		since(p.start, intIn(p.status, "RssFile", at, text("type", "file"))),
		since(p.start, intIn(p.status, "VmSwap", at, text("type", "swap"))))
	return ms
}

func statesWith(active string) []string {
	for _, state := range activeStates {
		if state == active {
			return activeStates
		}
	}
	return append(append([]string{}, activeStates...), active)
}

func previousUnit(prev *snapshot, name string, started time.Time) (unitReading, bool) {
	if prev == nil {
		return unitReading{}, false
	}
	for _, u := range prev.units {
		if u.name == name && u.state.ok && u.state.value.since.Equal(started) {
			return u, true
		}
	}
	return unitReading{}, false
}

func previousProcess(prev *snapshot, pid int64, started time.Time) (processReading, bool) {
	if prev == nil {
		return processReading{}, false
	}
	for _, p := range prev.processes {
		if p.pid == pid && p.start.Equal(started) {
			return p, true
		}
	}
	return processReading{}, false
}

// utilization returns the CPU time spent since before as a share of elapsed on all cpus, as semconv defines it.
func utilization(cpu, before, elapsed time.Duration, cpus opt[int64]) opt[float64] {
	if !cpus.ok || cpus.value <= 0 || elapsed <= 0 || cpu < before {
		return opt[float64]{}
	}
	return some((cpu - before).Seconds() / (elapsed.Seconds() * float64(cpus.value)))
}

type metricList []*metricspb.Metric

func (l *metricList) gauge(name, unit string, points ...*metricspb.NumberDataPoint) {
	if points = present(points); len(points) > 0 {
		*l = append(*l, &metricspb.Metric{Name: name, Unit: unit, Data: &metricspb.Metric_Gauge{Gauge: &metricspb.Gauge{DataPoints: points}}})
	}
}

func (l *metricList) counter(name, unit string, points ...*metricspb.NumberDataPoint) {
	l.sum(name, unit, true, points)
}

// upDownCounter appends a cumulative sum that may fall, the OTLP form of a semconv updowncounter.
func (l *metricList) upDownCounter(name, unit string, points ...*metricspb.NumberDataPoint) {
	l.sum(name, unit, false, points)
}

func (l *metricList) sum(name, unit string, monotonic bool, points []*metricspb.NumberDataPoint) {
	if points = present(points); len(points) > 0 {
		*l = append(*l, &metricspb.Metric{Name: name, Unit: unit, Data: &metricspb.Metric_Sum{Sum: &metricspb.Sum{
			DataPoints:             points,
			AggregationTemporality: metricspb.AggregationTemporality_AGGREGATION_TEMPORALITY_CUMULATIVE,
			IsMonotonic:            monotonic,
		}}})
	}
}

func present(points []*metricspb.NumberDataPoint) []*metricspb.NumberDataPoint {
	var result []*metricspb.NumberDataPoint
	for _, p := range points {
		if p != nil {
			result = append(result, p)
		}
	}
	return result
}

func intPoint(at time.Time, value int64, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	return &metricspb.NumberDataPoint{TimeUnixNano: uint64(at.UnixNano()), Value: &metricspb.NumberDataPoint_AsInt{AsInt: value}, Attributes: attributes}
}

func doublePoint(at time.Time, value float64, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	return &metricspb.NumberDataPoint{TimeUnixNano: uint64(at.UnixNano()), Value: &metricspb.NumberDataPoint_AsDouble{AsDouble: value}, Attributes: attributes}
}

func intOf(o opt[int64], at time.Time, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	if !o.ok {
		return nil
	}
	return intPoint(at, o.value, attributes...)
}

func doubleOf(o opt[float64], at time.Time, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	if !o.ok {
		return nil
	}
	return doublePoint(at, o.value, attributes...)
}

func intIn(m map[string]int64, key string, at time.Time, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	value, ok := m[key]
	return intOf(opt[int64]{value, ok}, at, attributes...)
}

func secondsIn(m map[string]time.Duration, key string, at time.Time, attributes ...*commonpb.KeyValue) *metricspb.NumberDataPoint {
	value, ok := m[key]
	return doubleOf(opt[float64]{value.Seconds(), ok}, at, attributes...)
}

// since returns p starting at start; a zero start leaves the start time unknown, as OTLP allows.
func since(start time.Time, p *metricspb.NumberDataPoint) *metricspb.NumberDataPoint {
	if p != nil && !start.IsZero() {
		p.StartTimeUnixNano = uint64(start.UnixNano())
	}
	return p
}

func text(key, value string) *commonpb.KeyValue {
	return &commonpb.KeyValue{Key: key, Value: &commonpb.AnyValue{Value: &commonpb.AnyValue_StringValue{StringValue: value}}}
}

func integer(key string, value int64) *commonpb.KeyValue {
	return &commonpb.KeyValue{Key: key, Value: &commonpb.AnyValue{Value: &commonpb.AnyValue_IntValue{IntValue: value}}}
}
