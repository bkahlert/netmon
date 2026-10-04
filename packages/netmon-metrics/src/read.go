package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// clockTicks is USER_HZ, the unit of the CPU times in /proc/<pid>/stat, 100 on every Linux architecture.
const clockTicks = 100

// fs reads the kernel's files below root: / on the board, a fixture directory in the tests.
type fs struct{ root string }

func (f fs) read(path string) ([]byte, error) { return os.ReadFile(filepath.Join(f.root, path)) }

func (f fs) text(path string) (string, error) {
	b, err := f.read(path)
	return strings.TrimSpace(string(b)), err
}

func (f fs) int64(path string) (int64, error) {
	text, err := f.text(path)
	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(text, 10, 64)
}

// keyValues returns the `name value` lines of files such as /proc/vmstat, memory.stat and memory.events.
func (f fs) keyValues(path string) (map[string]int64, error) {
	b, err := f.read(path)
	if err != nil {
		return nil, err
	}
	result := map[string]int64{}
	for _, line := range strings.Split(string(b), "\n") {
		words := strings.Fields(line)
		if len(words) != 2 {
			continue
		}
		if value, err := strconv.ParseInt(words[1], 10, 64); err == nil {
			result[words[0]] = value
		}
	}
	return result, nil
}

// kbLines returns the `Name: value kB` lines of /proc/meminfo and /proc/<pid>/status in bytes.
func (f fs) kbLines(path string) (map[string]int64, error) {
	b, err := f.read(path)
	if err != nil {
		return nil, err
	}
	result := map[string]int64{}
	for _, line := range strings.Split(string(b), "\n") {
		words := strings.Fields(strings.Replace(line, ":", " ", 1))
		if len(words) != 3 || words[2] != "kB" {
			continue
		}
		if value, err := strconv.ParseInt(words[1], 10, 64); err == nil {
			result[words[0]] = value * 1024
		}
	}
	return result, nil
}

// opt is a reading that is absent when its source did not open or did not parse.
type opt[T any] struct {
	value T
	ok    bool
}

func some[T any](value T) opt[T] { return opt[T]{value, true} }

func optOf[T any](value T, err error) opt[T] {
	if err != nil {
		return opt[T]{}
	}
	return some(value)
}

type hostReading struct {
	bootID  opt[string]
	cpus    opt[int64]
	meminfo map[string]int64
	vmstat  map[string]int64
	load1   opt[float64]
	stall   map[string]time.Duration
	zram    opt[int64]
}

func readHost(f fs) hostReading {
	var r hostReading
	r.bootID = optOf(f.text("proc/sys/kernel/random/boot_id"))
	r.cpus = optOf(readCPUCount(f))
	r.meminfo, _ = f.kbLines("proc/meminfo")
	r.vmstat, _ = f.keyValues("proc/vmstat")
	r.load1 = optOf(readLoad1(f))
	r.stall, _ = readStall(f)
	r.zram = optOf(readZram(f))
	return r
}

func readCPUCount(f fs) (int64, error) {
	text, err := f.text("sys/devices/system/cpu/online")
	if err != nil {
		return 0, err
	}
	var count int64
	for _, part := range strings.Split(text, ",") {
		first, last, isRange := strings.Cut(part, "-")
		if !isRange {
			last = first
		}
		from, errFrom := strconv.ParseInt(first, 10, 64)
		to, errTo := strconv.ParseInt(last, 10, 64)
		if err := errors.Join(errFrom, errTo); err != nil {
			return 0, err
		}
		count += to - from + 1
	}
	return count, nil
}

func readLoad1(f fs) (float64, error) {
	text, err := f.text("proc/loadavg")
	if err != nil {
		return 0, err
	}
	first, _, _ := strings.Cut(text, " ")
	return strconv.ParseFloat(first, 64)
}

// readStall returns the `total` of /proc/pressure/memory by kind, `some` and `full`.
func readStall(f fs) (map[string]time.Duration, error) {
	b, err := f.read("proc/pressure/memory")
	if err != nil {
		return nil, err
	}
	result := map[string]time.Duration{}
	for _, line := range strings.Split(string(b), "\n") {
		words := strings.Fields(line)
		if len(words) == 0 {
			continue
		}
		for _, word := range words[1:] {
			if total, ok := strings.CutPrefix(word, "total="); ok {
				if usec, err := strconv.ParseInt(total, 10, 64); err == nil {
					result[words[0]] = time.Duration(usec) * time.Microsecond
				}
			}
		}
	}
	return result, nil
}

// readZram returns mem_used_total, the third field of mm_stat.
func readZram(f fs) (int64, error) {
	text, err := f.text("sys/block/zram0/mm_stat")
	if err != nil {
		return 0, err
	}
	fields := strings.Fields(text)
	if len(fields) < 3 {
		return 0, fmt.Errorf("mm_stat has %d fields", len(fields))
	}
	return strconv.ParseInt(fields[2], 10, 64)
}

func readBootTime(f fs) (time.Time, error) {
	stat, err := f.keyValues("proc/stat")
	if err != nil {
		return time.Time{}, err
	}
	btime, ok := stat["btime"]
	if !ok {
		return time.Time{}, errors.New("proc/stat has no btime")
	}
	return time.Unix(btime, 0), nil
}

type unitState struct {
	active   string
	restarts int64
	since    time.Time
}

type unitReading struct {
	name                         string
	state                        opt[unitState]
	cpu                          opt[time.Duration]
	ram, swap, ramPeak, swapPeak opt[int64]
	stat, events                 map[string]int64
}

func cgroupOf(unit string) string { return filepath.Join("sys/fs/cgroup/system.slice", unit) }

func readUnit(f fs, name string, state opt[unitState]) unitReading {
	dir := cgroupOf(name)
	r := unitReading{name: name, state: state}
	if cpu, err := f.keyValues(filepath.Join(dir, "cpu.stat")); err == nil {
		if usec, ok := cpu["usage_usec"]; ok {
			r.cpu = some(time.Duration(usec) * time.Microsecond)
		}
	}
	r.ram = optOf(f.int64(filepath.Join(dir, "memory.current")))
	r.swap = optOf(f.int64(filepath.Join(dir, "memory.swap.current")))
	r.ramPeak = optOf(f.int64(filepath.Join(dir, "memory.peak")))
	r.swapPeak = optOf(f.int64(filepath.Join(dir, "memory.swap.peak")))
	r.stat, _ = f.keyValues(filepath.Join(dir, "memory.stat"))
	r.events, _ = f.keyValues(filepath.Join(dir, "memory.events"))
	return r
}

type processReading struct {
	unit, comm   string
	pid          int64
	start        time.Time
	user, system time.Duration
	status       map[string]int64
}

// readProcesses returns the first process of each of names among the unit's processes, in the order of cgroup.procs.
func readProcesses(f fs, unit string, names []string, boot time.Time) []processReading {
	b, err := f.read(filepath.Join(cgroupOf(unit), "cgroup.procs"))
	if err != nil {
		return nil
	}
	wanted := map[string]bool{}
	for _, name := range names {
		wanted[name] = true
	}
	var result []processReading
	for _, field := range strings.Fields(string(b)) {
		pid, err := strconv.ParseInt(field, 10, 64)
		if err != nil {
			continue
		}
		comm, err := f.text(fmt.Sprintf("proc/%d/comm", pid))
		if err != nil || !wanted[comm] {
			continue
		}
		p, err := readProcess(f, pid, boot)
		if err != nil {
			continue
		}
		p.unit, p.comm = unit, comm
		result = append(result, p)
		delete(wanted, comm)
	}
	return result
}

func readProcess(f fs, pid int64, boot time.Time) (processReading, error) {
	stat, err := f.text(fmt.Sprintf("proc/%d/stat", pid))
	if err != nil {
		return processReading{}, err
	}
	// The command name in parentheses may hold spaces and parentheses itself, so the fields count from the last one.
	end := strings.LastIndexByte(stat, ')')
	if end < 0 {
		return processReading{}, fmt.Errorf("proc/%d/stat has no command name", pid)
	}
	fields := strings.Fields(stat[end+1:])
	if len(fields) < 20 {
		return processReading{}, fmt.Errorf("proc/%d/stat has %d fields after the command name", pid, len(fields))
	}
	utime, errUser := strconv.ParseInt(fields[11], 10, 64)
	stime, errSystem := strconv.ParseInt(fields[12], 10, 64)
	started, errStart := strconv.ParseInt(fields[19], 10, 64)
	if err := errors.Join(errUser, errSystem, errStart); err != nil {
		return processReading{}, err
	}
	status, err := f.kbLines(fmt.Sprintf("proc/%d/status", pid))
	if err != nil {
		return processReading{}, err
	}
	return processReading{pid: pid, start: boot.Add(ticks(started)), user: ticks(utime), system: ticks(stime), status: status}, nil
}

func ticks(n int64) time.Duration { return time.Duration(n) * time.Second / clockTicks }
