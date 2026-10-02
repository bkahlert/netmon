SHELL := /bin/bash
.DEFAULT_GOAL := help
# Gradle's output directory is called build, so the targets are declared phony.
.PHONY: help gradle build browser test-jvm test-js test-tier0 test-tier1 test-tier2 test test-all vm-device vm-prepare vm display deploy device-model-codes clean release

PLATFORM ?= linux/arm64
TARGET ?=
QEMU_ACCEL ?= hvf
URL ?=
UV := uv run --frozen
GRADLE_ARGS ?= --no-daemon --console=plain

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-14s %s\n", $$1, $$2}'

gradle: ## build the scanner jar and the web bundle
	./gradlew $(GRADLE_ARGS) shadowJar jsBrowserDistribution

build: gradle ## build the .deb packages into dist/
	@$(UV) python -m pihero_testkit.build

browser: ## download Playwright's WebKit, the kiosk's engine family, for the display tests
	@$(UV) playwright install webkit

test-jvm: ## the scanner's JVM unit tests (those that need Docker, macOS bundles, nmap or quiet thread timing are left out)
	./gradlew $(GRADLE_ARGS) jvmTest -PunitOnly

test-js: ## the display's JS unit tests (Karma, headless Chrome)
	./gradlew $(GRADLE_ARGS) jsBrowserTest

test-tier0: ## unit tests and static checks
	@$(UV) pytest -m tier0

test-tier1: ## install the packages into a systemd container and test
	@$(UV) pytest -m installed --target=podman --platform=$(PLATFORM)

test-tier2: ## boot a VM from the sample device file and run the installed and boot tests
	@$(UV) pytest -m 'installed or boot' --target=vm --qemu-accel=$(QEMU_ACCEL)

test: test-jvm test-js test-tier0 test-tier1 ## JVM and JS unit tests, tiers 0 and 1, what CI runs

test-all: test test-tier2 ## everything, what make release runs

vm-device: ## render the sample device file for the VM into dist/vm-device
	@$(UV) python tests/vm_device.py

vm-prepare: ## build and cache the tier-2 base image under ~/.cache/pihero
	@$(UV) python -m pihero_testkit.prepare

vm: vm-device ## boot the tier-2 VM from the sample device file and keep it running
	@$(UV) python -m pihero_testkit.vm --keep --qemu-accel=$(QEMU_ACCEL) --device=dist/vm-device

display: ## open URL in Playwright's WebKit at the panel's 800x480 (make display URL='http://netmon.local/?broker.host=netmon.local&broker.port=8080')
	@test -n "$(URL)" || { echo "usage: make display URL='http://host/?broker.host=host&broker.port=8080'"; exit 2; }
	@$(UV) playwright open -b webkit --viewport-size=800,480 "$(URL)"

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
