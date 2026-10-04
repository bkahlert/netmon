package main

import (
	"context"
	"time"
)

// unitStates reports a unit's state as systemd keeps it.
type unitStates interface {
	state(ctx context.Context, unit string) (unitState, error)
}

// unavailable is the unitStates of a sampler without systemd's bus: every state fails with err.
type unavailable struct{ err error }

func (u unavailable) state(context.Context, string) (unitState, error) { return unitState{}, u.err }

// snapshot holds the readings of one moment, at.
type snapshot struct {
	at        time.Time
	host      hostReading
	units     []unitReading
	processes []processReading
}

type sampler struct {
	fs        fs
	states    unitStates
	units     []string
	processes []string
	boot      time.Time
	now       func() time.Time
}

func (s sampler) sample(ctx context.Context) snapshot {
	snap := snapshot{at: s.now(), host: readHost(s.fs)}
	for _, unit := range s.units {
		snap.units = append(snap.units, readUnit(s.fs, unit, optOf(s.states.state(ctx, unit))))
		snap.processes = append(snap.processes, readProcesses(s.fs, unit, s.processes, s.boot)...)
	}
	return snap
}
