// Command netmon-metrics publishes the board's metrics every interval as one retained OTLP/JSON message on
// dt/netmon/<node>/metrics.
package main

import (
	"context"
	"flag"
	"log"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/coreos/go-systemd/v22/dbus"
)

// version is set at build time with -ldflags "-X main.version=…".
var version = "unknown"

type names []string

func (n *names) String() string     { return strings.Join(*n, ",") }
func (n *names) Set(v string) error { *n = append(*n, v); return nil }

func main() {
	log.SetFlags(0)
	var units, processes names
	flag.Var(&units, "unit", "a systemd unit to measure; repeatable")
	flag.Var(&processes, "process", "the name of a process to measure in the units; repeatable")
	interval := flag.Duration("interval", 5*time.Second, "the time between two samples")
	server := flag.String("broker", "tcp://localhost:1883", "the MQTT broker")
	root := flag.String("root", "/", "the directory holding proc/ and sys/")
	node := flag.String("node", "", "the node in the topic (default: the unqualified hostname)")
	flag.Parse()

	if *node == "" {
		hostname, err := os.Hostname()
		if err != nil {
			log.Fatalf("reading the hostname: %v", err)
		}
		*node, _, _ = strings.Cut(hostname, ".")
	}
	u, err := url.Parse(*server)
	if err != nil {
		log.Fatalf("--broker: %v", err)
	}
	f := fs{*root}
	// The boot time is read once: the kernel derives btime from the wall clock, which a time sync steps.
	boot, err := readBootTime(f)
	if err != nil {
		log.Fatalf("reading the boot time: %v", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	defer stop()
	var states unitStates
	if conn, err := dbus.NewSystemConnectionContext(ctx); err != nil {
		log.Printf("reading the units' state: %v", err)
		states = unavailable{err}
	} else {
		defer conn.Close()
		states = systemdStates{conn}
	}

	topic := "dt/netmon/" + *node + "/metrics"
	// The connection outlives ctx, so the topic can still be cleared after the signal.
	b, err := connect(context.Background(), u, topic, "netmon-metrics-"+*node)
	if err != nil {
		log.Fatalf("connecting to %s: %v", u, err)
	}
	ticker := time.NewTicker(*interval)
	defer ticker.Stop()
	s := sampler{fs: f, states: states, units: units, processes: processes, boot: boot, now: time.Now}
	publish := func(ctx context.Context, payload []byte) error {
		ctx, cancel := context.WithTimeout(ctx, *interval)
		defer cancel()
		return b.publish(ctx, payload)
	}
	log.Printf("publishing %s every %s to %s", topic, *interval, u)
	loop(ctx, ticker.C, func() snapshot { return s.sample(ctx) }, origin{node: *node, version: version, boot: boot}, publish)

	closing, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := b.close(closing); err != nil {
		log.Printf("clearing %s: %v", topic, err)
	}
}

// loop publishes a sample at once and one per tick until ctx ends. A failed publish drops that sample; it is logged
// once per run of failures.
func loop(ctx context.Context, ticks <-chan time.Time, take func() snapshot, o origin, publish func(context.Context, []byte) error) {
	var prev *snapshot
	failing := false
	for {
		cur := take()
		payload, err := marshal(request(cur, prev, o))
		if err == nil {
			err = publish(ctx, payload)
		}
		switch {
		case err != nil && ctx.Err() == nil && !failing:
			log.Printf("publishing the metrics: %v", err)
			failing = true
		case err == nil && failing:
			log.Printf("publishing the metrics again")
			failing = false
		}
		prev = &cur
		select {
		case <-ctx.Done():
			return
		case <-ticks:
		}
	}
}
