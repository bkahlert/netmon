package main

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"time"
)

func TestReadHost(t *testing.T) {
	t.Run("reads every source", func(t *testing.T) {
		f := fixture(t, map[string]string{
			"proc/sys/kernel/random/boot_id": "6f1c2a8e-2c4b-4a51-9d3e-0b7c1e2f3a4d\n",
			"sys/devices/system/cpu/online":  "0-3\n",
			"proc/meminfo":                   "MemTotal:         424960 kB\nMemAvailable:      95312 kB\nSwapTotal:        204800 kB\nSwapFree:         163840 kB\n",
			"proc/vmstat":                    "pswpin 117856217\npswpout 98765\npgmajfault 126517772\n",
			"proc/loadavg":                   "3.10 2.20 1.30 1/100 1234\n",
			"proc/pressure/memory":           "some avg10=1.50 avg60=0.80 avg300=0.40 total=12500000\nfull avg10=0.75 avg60=0.30 avg300=0.10 total=4250000\n",
			"sys/block/zram0/mm_stat":        "240640000 62609626 69357568        0 111222784       75 11928529     4924    55706\n",
		})

		got := readHost(f)

		equal(t, got, hostReading{
			bootID:  some("6f1c2a8e-2c4b-4a51-9d3e-0b7c1e2f3a4d"),
			cpus:    some(int64(4)),
			meminfo: map[string]int64{"MemTotal": 424960 * 1024, "MemAvailable": 95312 * 1024, "SwapTotal": 204800 * 1024, "SwapFree": 163840 * 1024},
			vmstat:  map[string]int64{"pswpin": 117856217, "pswpout": 98765, "pgmajfault": 126517772},
			load1:   some(3.1),
			stall:   map[string]time.Duration{"some": 12500 * time.Millisecond, "full": 4250 * time.Millisecond},
			zram:    some(int64(69357568)),
		})
	})

	t.Run("counts the cpus of a list of ranges", func(t *testing.T) {
		f := fixture(t, map[string]string{"sys/devices/system/cpu/online": "0,2-3\n"})

		got := readHost(f)

		equal(t, got.cpus, some(int64(3)))
	})

	t.Run("on an empty root", func(t *testing.T) {
		t.Run("has no reading", func(t *testing.T) {
			got := readHost(fixture(t, nil))

			equal(t, got, hostReading{})
		})
	})
}

func TestReadUnit(t *testing.T) {
	state := some(unitState{active: "active", restarts: 1, since: time.Unix(1759400040, 0)})

	t.Run("reads the cgroup", func(t *testing.T) {
		f := fixture(t, map[string]string{
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/cpu.stat":            "usage_usec 6900000\nuser_usec 5000000\nsystem_usec 1900000\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.current":      "160000000\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.swap.current": "8820736\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.peak":         "323874816\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.swap.peak":    "125829120\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.stat":         "anon 52428800\nfile 20971520\n",
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.events":       "low 0\nhigh 0\nmax 0\noom 0\noom_kill 0\n",
		})

		got := readUnit(f, "pihero-kiosk.service", state)

		equal(t, got, unitReading{
			name:     "pihero-kiosk.service",
			state:    state,
			cpu:      some(6900 * time.Millisecond),
			ram:      some(int64(160000000)),
			swap:     some(int64(8820736)),
			ramPeak:  some(int64(323874816)),
			swapPeak: some(int64(125829120)),
			stat:     map[string]int64{"anon": 52428800, "file": 20971520},
			events:   map[string]int64{"low": 0, "high": 0, "max": 0, "oom": 0, "oom_kill": 0},
		})
	})

	t.Run("on a unit without a cgroup", func(t *testing.T) {
		t.Run("has only the state", func(t *testing.T) {
			got := readUnit(fixture(t, nil), "pihero-kiosk.service", state)

			equal(t, got, unitReading{name: "pihero-kiosk.service", state: state})
		})
	})
}

func TestReadProcesses(t *testing.T) {
	boot := time.Unix(1759400000, 0)

	t.Run("reads the named process of the unit", func(t *testing.T) {
		f := fixture(t, map[string]string{
			"sys/fs/cgroup/system.slice/pihero-kiosk.service/cgroup.procs": "1\n1234\n",
			"proc/1/comm":      "cog\n",
			"proc/1234/comm":   "WPEWebProcess\n",
			"proc/1234/stat":   "1234 (WPEWebProcess) S 1 1234 1234 0 -1 4194560 52040 0 812 0 600 170 0 0 20 0 9 0 4500 400000000 25000\n",
			"proc/1234/status": "Name:\tWPEWebProcess\nVmRSS:\t  117760 kB\nRssAnon:\t   62016 kB\nRssFile:\t   51200 kB\nVmSwap:\t  112640 kB\n",
		})

		got := readProcesses(f, "pihero-kiosk.service", []string{"WPEWebProcess"}, boot)

		equal(t, got, []processReading{{
			unit:   "pihero-kiosk.service",
			comm:   "WPEWebProcess",
			pid:    1234,
			start:  boot.Add(45 * time.Second),
			user:   6 * time.Second,
			system: 1700 * time.Millisecond,
			status: map[string]int64{"VmRSS": 117760 * 1024, "RssAnon": 62016 * 1024, "RssFile": 51200 * 1024, "VmSwap": 112640 * 1024},
		}})
	})

	t.Run("counts the fields after a command name with spaces and parentheses", func(t *testing.T) {
		f := fixture(t, map[string]string{
			"sys/fs/cgroup/system.slice/a.service/cgroup.procs": "7\n",
			"proc/7/comm":   "a) (b\n",
			"proc/7/stat":   "7 (a) (b) S 1 7 7 0 -1 4194560 1 0 0 0 1500 250 0 0 20 0 1 0 3456 1 1\n",
			"proc/7/status": "VmRSS:\t1 kB\n",
		})

		got := readProcesses(f, "a.service", []string{"a) (b"}, boot)

		equal(t, got[0].user, 15*time.Second)
		equal(t, got[0].system, 2500*time.Millisecond)
		equal(t, got[0].start, boot.Add(34560*time.Millisecond))
	})

	t.Run("takes the first process of each name", func(t *testing.T) {
		f := fixture(t, map[string]string{
			"sys/fs/cgroup/system.slice/a.service/cgroup.procs": "7\n8\n",
			"proc/7/comm": "worker\n", "proc/7/stat": stat(7), "proc/7/status": "VmRSS:\t1 kB\n",
			"proc/8/comm": "worker\n", "proc/8/stat": stat(8), "proc/8/status": "VmRSS:\t1 kB\n",
		})

		got := readProcesses(f, "a.service", []string{"worker"}, boot)

		equal(t, len(got), 1)
		equal(t, got[0].pid, int64(7))
	})

	t.Run("on a process gone between the listing and its stat", func(t *testing.T) {
		t.Run("skips it", func(t *testing.T) {
			f := fixture(t, map[string]string{
				"sys/fs/cgroup/system.slice/a.service/cgroup.procs": "7\n8\n",
				"proc/7/comm": "worker\n",
				"proc/8/comm": "worker\n", "proc/8/stat": stat(8), "proc/8/status": "VmRSS:\t1 kB\n",
			})

			got := readProcesses(f, "a.service", []string{"worker"}, boot)

			equal(t, len(got), 1)
			equal(t, got[0].pid, int64(8))
		})
	})

	t.Run("on a unit without a cgroup", func(t *testing.T) {
		t.Run("finds none", func(t *testing.T) {
			got := readProcesses(fixture(t, nil), "a.service", []string{"worker"}, boot)

			equal(t, len(got), 0)
		})
	})
}

func TestReadBootTime(t *testing.T) {
	t.Run("reads btime", func(t *testing.T) {
		f := fixture(t, map[string]string{"proc/stat": "cpu  1 2 3 4\ncpu0 1 2 3 4\nbtime 1759400000\nprocesses 4242\n"})

		got, err := readBootTime(f)

		equal(t, err, nil)
		equal(t, got, time.Unix(1759400000, 0))
	})

	t.Run("on a stat without btime", func(t *testing.T) {
		t.Run("fails", func(t *testing.T) {
			_, err := readBootTime(fixture(t, map[string]string{"proc/stat": "cpu  1 2 3 4\n"}))

			if err == nil {
				t.Fatal("expected an error")
			}
		})
	})
}

func fixture(t *testing.T, files map[string]string) fs {
	t.Helper()
	root := t.TempDir()
	for path, content := range files {
		full := filepath.Join(root, path)
		if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	return fs{root}
}

func stat(pid int) string {
	return fmt.Sprintf("%d (worker) S 1 1 1 0 -1 0 0 0 0 0 100 50 0 0 20 0 1 0 1000 1 1\n", pid)
}

func equal[T any](t *testing.T, got, want T) {
	t.Helper()
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got  %#v\nwant %#v", got, want)
	}
}
