package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/coreos/go-systemd/v22/dbus"
)

// systemdStates reads the units' state from systemd's D-Bus API. A failed call on a lost connection ends the
// process, since the connection is never dialled again; Restart= starts the sampler anew.
type systemdStates struct{ conn *dbus.Conn }

func (s systemdStates) state(ctx context.Context, unit string) (unitState, error) {
	state, err := s.read(ctx, unit)
	// The connection closes on a shutdown too; ctx has ended then.
	if err != nil && !s.conn.Connected() && ctx.Err() == nil {
		log.Fatalf("reading the units' state: the system bus is gone: %v", err)
	}
	return state, err
}

func (s systemdStates) read(ctx context.Context, unit string) (unitState, error) {
	props, err := s.conn.GetUnitPropertiesContext(ctx, unit)
	if err != nil {
		return unitState{}, err
	}
	// systemd answers for a unit it does not know too, with LoadState not-found and ActiveState inactive.
	if load, _ := props["LoadState"].(string); load != "loaded" {
		return unitState{}, fmt.Errorf("%s is %s", unit, load)
	}
	service, err := s.conn.GetUnitTypePropertiesContext(ctx, unit, "Service")
	if err != nil {
		return unitState{}, err
	}
	active, _ := props["ActiveState"].(string)
	since, _ := props["ActiveEnterTimestamp"].(uint64)
	restarts, _ := service["NRestarts"].(uint32)
	return unitState{active: active, restarts: int64(restarts), since: time.UnixMicro(int64(since))}, nil
}
