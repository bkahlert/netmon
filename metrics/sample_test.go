package main

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestSampler(t *testing.T) {
	at := time.Unix(1759450005, 0)
	boot := time.Unix(1759400000, 0)
	files := map[string]string{
		"sys/devices/system/cpu/online":                                      "0-3\n",
		"sys/fs/cgroup/system.slice/netmon-scanner.service/memory.current":   "41943040\n",
		"sys/fs/cgroup/system.slice/netmon-scanner.service/cgroup.procs":     "812\n",
		"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.current":     "160000000\n",
		"sys/fs/cgroup/system.slice/pihero-kiosk.service/cgroup.procs":       "1234\n",
		"proc/812/comm":    "netmon-scanner\n",
		"proc/812/stat":    stat(812),
		"proc/812/status":  "VmRSS:\t1 kB\n",
		"proc/1234/comm":   "WPEWebProcess\n",
		"proc/1234/stat":   stat(1234),
		"proc/1234/status": "VmRSS:\t1 kB\n",
	}

	t.Run("reads the host, each unit with its state and the named processes of each unit", func(t *testing.T) {
		states := fakeStates{"netmon-scanner.service": {active: "active"}, "pihero-kiosk.service": {active: "activating"}}
		s := sampler{fs: fixture(t, files), states: states, units: []string{"netmon-scanner.service", "pihero-kiosk.service"}, processes: []string{"WPEWebProcess", "netmon-scanner"}, boot: boot, now: func() time.Time { return at }}

		got := s.sample(context.Background())

		equal(t, got.at, at)
		equal(t, got.host.cpus, some(int64(4)))
		equal(t, []string{got.units[0].name, got.units[1].name}, []string{"netmon-scanner.service", "pihero-kiosk.service"})
		equal(t, got.units[1].state, some(unitState{active: "activating"}))
		equal(t, got.units[1].ram, some(int64(160000000)))
		equal(t, []string{got.processes[0].comm, got.processes[1].comm}, []string{"netmon-scanner", "WPEWebProcess"})
		equal(t, got.processes[1].unit, "pihero-kiosk.service")
	})

	t.Run("on a state that cannot be read", func(t *testing.T) {
		t.Run("has the unit without a state", func(t *testing.T) {
			s := sampler{fs: fixture(t, files), states: unavailable{errors.New("no bus")}, units: []string{"pihero-kiosk.service"}, boot: boot, now: func() time.Time { return at }}

			got := s.sample(context.Background())

			equal(t, got.units[0].state, opt[unitState]{})
			equal(t, got.units[0].ram, some(int64(160000000)))
		})
	})
}

type fakeStates map[string]unitState

func (f fakeStates) state(_ context.Context, unit string) (unitState, error) {
	if s, ok := f[unit]; ok {
		return s, nil
	}
	return unitState{}, errors.New("unknown unit")
}
