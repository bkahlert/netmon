package main

import (
	"context"
	"errors"
	"testing"
	"time"

	metricspb "go.opentelemetry.io/proto/otlp/metrics/v1"
	"google.golang.org/protobuf/encoding/protojson"
)

func TestLoop(t *testing.T) {
	cur, prev, o := goldenSnapshots()

	t.Run("publishes a sample at once and one per tick, the later ones with utilization", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		ticks := make(chan time.Time)
		snapshots := []snapshot{prev, cur}
		var published [][]byte
		take := func() snapshot { s := snapshots[0]; snapshots = snapshots[1:]; return s }
		publish := func(_ context.Context, payload []byte) error {
			published = append(published, payload)
			if len(published) == 2 {
				cancel()
			} else {
				go func() { ticks <- time.Time{} }()
			}
			return nil
		}

		loop(ctx, ticks, take, o, publish)

		equal(t, len(published), 2)
		absent(t, unitResource(t, decoded(t, published[0]), "pihero-kiosk.service"), "systemd.unit.cpu.utilization")
		metric(t, unitResource(t, decoded(t, published[1]), "pihero-kiosk.service"), "systemd.unit.cpu.utilization")
	})

	t.Run("on a failed publish", func(t *testing.T) {
		t.Run("keeps sampling", func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			ticks := make(chan time.Time)
			calls := 0
			publish := func(context.Context, []byte) error {
				calls++
				if calls == 3 {
					cancel()
					return nil
				}
				go func() { ticks <- time.Time{} }()
				return errors.New("connection down")
			}

			loop(ctx, ticks, func() snapshot { return cur }, o, publish)

			equal(t, calls, 3)
		})
	})
}

func decoded(t *testing.T, payload []byte) *metricspb.MetricsData {
	t.Helper()
	var data metricspb.MetricsData
	if err := protojson.Unmarshal(payload, &data); err != nil {
		t.Fatal(err)
	}
	return &data
}
