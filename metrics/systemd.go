package main

import (
	"context"
	"time"

	"github.com/coreos/go-systemd/v22/dbus"
)

// systemdStates reads the units' state from systemd's D-Bus API.
type systemdStates struct{ conn *dbus.Conn }

func (s systemdStates) state(ctx context.Context, unit string) (unitState, error) {
	props, err := s.conn.GetUnitPropertiesContext(ctx, unit)
	if err != nil {
		return unitState{}, err
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
