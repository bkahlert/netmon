SHELL := /bin/bash
.DEFAULT_GOAL := help
# Gradle's output directory is called build, so the targets are declared phony.
.PHONY: help gradle build test-jvm test-tier0 test-tier1 test deploy clean release

PLATFORM ?= linux/arm64
TARGET ?=
UV := uv run --frozen
GRADLE_ARGS ?= --no-daemon --console=plain

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-14s %s\n", $$1, $$2}'

gradle: ## build the scanner jar and the web bundle
	./gradlew $(GRADLE_ARGS) shadowJar jsBrowserProductionWebpack

build: gradle ## build the .deb packages into dist/
	@$(UV) python -m pihero_testkit.build

test-jvm: ## the scanner's JVM unit tests (those that need Docker, macOS bundles or nmap are left out)
	./gradlew $(GRADLE_ARGS) jvmTest -PunitOnly

test-tier0: ## unit tests and static checks
	@$(UV) pytest -m tier0

test-tier1: ## install the packages into a systemd container and test
	@$(UV) pytest -m installed --target=podman --platform=$(PLATFORM)

test: test-jvm test-tier0 test-tier1 ## JVM unit tests, tiers 0 and 1

deploy: build ## install the built packages on TARGET over SSH
	@test -n "$(TARGET)" || { echo "usage: make deploy TARGET=pi@host"; exit 2; }
	@$(UV) python -m pihero_testkit.deploy "$(TARGET)"

clean: ## remove build outputs
	rm -rf dist packages/*/.build build

release: ## run the tiers, then tag VERSION (make release VERSION=1.0.0)
	@test -n "$(VERSION)" || { echo "usage: make release VERSION=X.Y.Z"; exit 2; }
	@git diff --quiet HEAD || { echo "working tree is dirty"; exit 1; }
	@$(MAKE) test
	git tag -a "v$(VERSION)" -m "v$(VERSION)"
	@echo "Tagged v$(VERSION). Push with: git push origin v$(VERSION)"
