SHELL := /bin/bash
.DEFAULT_GOAL := help
# Gradle's output directory is called build, so the targets are declared phony.
.PHONY: help gradle metrics build browser test-jvm test-js test-metrics test-layout test-preview test-tier0 test-tier1 test-tier2 soak apt-probe test test-all vm-device vm-prepare vm broker preview preview-browser preview-vm preview-board deploy device-model-codes clean release

PLATFORM ?= linux/arm64
TARGET ?=
QEMU_ACCEL ?= hvf
BROWSER_ARGS ?=
UV := uv run --frozen
GRADLE_ARGS ?= --no-daemon --console=plain

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-14s %s\n", $$1, $$2}'

NATIVE_IMAGE := localhost/netmon-native:$(shell shasum -a 256 packages/netmon-scanner/native/Containerfile | cut -c1-12)

gradle: ## build the scanner jar, its native binary and the web bundle
	./gradlew $(GRADLE_ARGS) shadowJar jsBrowserDistribution
	@$(MAKE) build/native/netmon-scanner

build/native/netmon-scanner: build/libs/netmon-all.jar packages/netmon-scanner/native/Containerfile packages/netmon-scanner/native/compile
	@podman image exists $(NATIVE_IMAGE) || podman build --platform linux/arm64 -t $(NATIVE_IMAGE) -f packages/netmon-scanner/native/Containerfile packages/netmon-scanner/native
	podman run --rm --platform linux/arm64 -v "$(CURDIR):/work" -w /work $(NATIVE_IMAGE) packages/netmon-scanner/native/compile

metrics: ## build the metrics sampler, a static arm64 binary, into build/native
	cd metrics && CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags "-s -w -X main.version=$(shell git describe --tags --always)" -o ../build/native/netmon-metrics .

build: gradle metrics ## build the .deb packages into dist/
	@$(UV) python -m pihero_testkit.build

browser: ## download Playwright's WebKit, the kiosk's engine family, for the display tests
	@$(UV) playwright install $(BROWSER_ARGS) webkit

test-jvm: ## the scanner's JVM unit tests (those that need Docker, macOS bundles, nmap or quiet thread timing are left out)
	./gradlew $(GRADLE_ARGS) jvmTest -PunitOnly

test-js: ## the display's JS unit tests (Karma, headless Chrome)
	./gradlew $(GRADLE_ARGS) jsBrowserTest

test-metrics: ## the metrics sampler's Go tests
	cd metrics && go vet ./... && go test ./...

test-layout: ## the page's geometry in Playwright's WebKit at three sizes and several host counts (needs make browser)
	./gradlew $(GRADLE_ARGS) jsBrowserDistribution
	@$(UV) pytest -m layout

test-preview: ## the fake broker's container on this Mac (needs Podman)
	@$(UV) pytest -m preview

test-tier0: ## unit tests and static checks
	@$(UV) pytest -m tier0

test-tier1: ## install the packages into a systemd container and test
	@$(UV) pytest -m installed --target=podman --platform=$(PLATFORM)

test-tier2: ## boot a VM from the sample device file and run the installed and boot tests
	@$(UV) pytest -m 'installed or boot' --target=vm --qemu-accel=$(QEMU_ACCEL)

soak: ## sample both units for ten minutes: TARGET=pi@host for the board, else the VM; KIOSK_CONF=file for an A/B in the VM
	@$(UV) pytest -m soak $(if $(TARGET),--target=ssh --target-uri=$(TARGET),--target=vm --qemu-accel=$(QEMU_ACCEL)) $(if $(KIOSK_CONF),--kiosk-conf=$(KIOSK_CONF)) $(SOAK_ARGS)

apt-probe: ## apt update and a reinstall next to the live stack, with apt's peak and timing: TARGET=pi@host for the board, else the VM
	@$(UV) pytest -m apt $(if $(TARGET),--target=ssh --target-uri=$(TARGET),--target=vm --qemu-accel=$(QEMU_ACCEL)) $(APT_ARGS)

test: test-jvm test-js test-metrics test-tier0 test-tier1 ## JVM, JS and Go unit tests, tiers 0 and 1, what CI runs

test-all: test test-tier2 ## everything, what make release runs

vm-device: ## render the sample device file for the VM into dist/vm-device
	@$(UV) python tests/vm_device.py

vm-prepare: ## build and cache the tier-2 base image under ~/.cache/pihero
	@$(UV) python -m pihero_testkit.prepare

vm: vm-device ## boot the tier-2 VM from the sample device file and keep it running
	@$(UV) python -m pihero_testkit.vm --keep --qemu-accel=$(QEMU_ACCEL) --device=dist/vm-device

broker: ## run the preview's fake broker (Mosquitto with the fixture) until Ctrl-C (SCAN=14+39 sets the hosts)
	@$(UV) python tests/preview_broker.py

preview-browser: ## the broker, the dev server and the page in a browser tab (BROKER=fake|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on browser

preview-vm: ## the broker, the dev server and the kiosk's WebKit in a VM window, its inspector in Safari (BROKER=fake|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on vm

preview-board: ## the broker, the dev server and the kiosk of a real Pi, its inspector in Safari (TARGET=pi@host BROKER=fake|board|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on board

preview: preview-vm ## the same as preview-vm

deploy: build ## install the built packages on TARGET over SSH
	@test -n "$(TARGET)" || { echo "usage: make deploy TARGET=pi@host"; exit 2; }
	@$(UV) python -m pihero_testkit.deploy "$(TARGET)"

device-model-codes: ## regenerate the model codes and symbols the display draws, from this Mac with device-icons
	@$(UV) python tests/device_model_codes.py

clean: ## remove build outputs
	rm -rf dist packages/*/.build build

release: ## build, run the tiers, then tag VERSION (make release VERSION=1.0.0)
	@test -n "$(VERSION)" || { echo "usage: make release VERSION=X.Y.Z"; exit 2; }
	@git diff --quiet HEAD || { echo "working tree is dirty"; exit 1; }
	@$(MAKE) gradle
	@$(MAKE) test-all
	git tag -a "v$(VERSION)" -m "v$(VERSION)"
	@echo "Tagged v$(VERSION). Push with: git push origin v$(VERSION)"
