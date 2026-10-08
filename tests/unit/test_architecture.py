import re
from pathlib import Path


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())
SCANNER_APP = "com.bkahlert.netmon.scanner.app.Application"


class TestArchitecture:

    def test_scanner_and_display_do_not_cross_import(self):
        sources = [(path, source_package(path), imported_packages(path)) for path in production_kotlin_sources()]
        scanner = [(path, imports) for path, package, imports in sources if package.startswith("com.bkahlert.netmon.scanner.")]
        display = [(path, imports) for path, package, imports in sources if package.startswith("com.bkahlert.netmon.display.")]

        assert scanner
        assert display
        violations = [
            (path, imported)
            for package_sources, forbidden in ((scanner, "com.bkahlert.netmon.display"), (display, "com.bkahlert.netmon.scanner"))
            for path, imports in package_sources
            for imported in imports
            if imported == forbidden or imported.startswith(forbidden + ".")
        ]
        assert not violations, violations

    def test_contract_does_not_import_components_or_settings(self):
        sources = [
            (path, imported)
            for path in production_kotlin_sources()
            if source_package(path).startswith("com.bkahlert.netmon.contract.")
            for imported in imported_packages(path)
        ]

        assert sources
        forbidden = (
            "com.bkahlert.netmon.scanner",
            "com.bkahlert.netmon.display",
            "com.bkahlert.netmon.settings",
            "com.bkahlert.netmon.support.config",
            "com.bkahlert.kommons.config",
        )
        violations = [(path, imported) for path, imported in sources if any(is_under(imported, prefix) for prefix in forbidden)]
        assert not violations, violations

    def test_shared_support_does_not_import_netmon_policy(self):
        support_sources = [
            path
            for path in production_kotlin_sources()
            if source_package(path).startswith(("com.bkahlert.kommons.", "com.bkahlert.netmon.support."))
        ]
        assert support_sources

        violations = [
            (path, imported)
            for path in support_sources
            for imported in imported_packages(path)
            if is_under(imported, "com.bkahlert.netmon")
        ]
        assert not violations, violations

    def test_scanner_support_does_not_import_application_policy(self):
        violations = [
            (path, imported)
            for path in production_kotlin_sources()
            if source_package(path).startswith("com.bkahlert.netmon.scanner.support.")
            for imported in imported_packages(path)
            if is_under(imported, "com.bkahlert.netmon.scanner.app")
        ]

        assert not violations, violations

    def test_gradle_executable_and_jar_manifest_use_scanner_application(self):
        build = (ROOT / "build.gradle.kts").read_text()

        assert f'mainClass.set("{SCANNER_APP}")' in build
        assert f'attributes["Main-Class"] = "{SCANNER_APP}"' in build

    def test_native_image_uses_scanner_application(self):
        metadata = ROOT / "src/jvmMain/resources/META-INF/native-image/com.bkahlert.netmon/netmon-scanner/native-image.properties"

        assert f"-H:Class={SCANNER_APP}" in metadata.read_text()

    def test_no_legacy_netmon_production_packages_remain(self):
        allowed = ("com.bkahlert.netmon.contract", "com.bkahlert.netmon.scanner", "com.bkahlert.netmon.display", "com.bkahlert.netmon.support")
        packages = [(path, source_package(path)) for path in production_kotlin_sources()]
        legacy = [
            (path, package)
            for path, package in packages
            if is_under(package, "com.bkahlert.netmon") and not any(is_under(package, prefix) for prefix in allowed)
        ]

        assert not legacy, legacy

    def test_python_tooling_lives_under_tools_netmon_dev(self):
        expected = {
            ROOT / "tools/netmon_dev/__init__.py",
            ROOT / "tools/netmon_dev/preview/__init__.py",
            ROOT / "tools/netmon_dev/preview/__main__.py",
            ROOT / "tools/netmon_dev/preview/broker.py",
            ROOT / "tools/netmon_dev/preview/scan_fixtures.py",
            ROOT / "tools/netmon_dev/bench/__main__.py",
            ROOT / "tools/netmon_dev/bench/command.py",
            ROOT / "tools/netmon_dev/bench/figures.py",
            ROOT / "tools/netmon_dev/bench/fixtures.py",
            ROOT / "tools/netmon_dev/bench/report.py",
            ROOT / "tools/netmon_dev/assets/device_model_codes.py",
            ROOT / "tools/netmon_dev/assets/device_icons.py",
            ROOT / "tools/netmon_dev/system/booted.py",
            ROOT / "tools/netmon_dev/system/sampling.py",
            ROOT / "tools/netmon_dev/system/aptprobe.py",
            ROOT / "tools/netmon_dev/system/vm_device.py",
        }

        missing = sorted(path.relative_to(ROOT).as_posix() for path in expected if not path.exists())
        assert not missing, missing

    def test_python_tests_are_split_by_boundary(self):
        expected = {
            ROOT / "tests/browser/layout.py",
            ROOT / "tests/browser/test_layout.py",
            ROOT / "tests/system/test_apt.py",
            ROOT / "tests/system/test_boot.py",
            ROOT / "tests/system/test_display.py",
            ROOT / "tests/system/test_soak.py",
            ROOT / "tests/unit/test_architecture.py",
            ROOT / "tests/unit/test_makefile.py",
            ROOT / "tests/unit/test_conftest.py",
        }
        legacy = {
            ROOT / "tests/preview.py",
            ROOT / "tests/preview_broker.py",
            ROOT / "tests/scan_fixtures.py",
            ROOT / "tests/bench.py",
            ROOT / "tests/device_model_codes.py",
            ROOT / "tests/device_icons.py",
            ROOT / "tests/booted.py",
            ROOT / "tests/sampling.py",
            ROOT / "tests/aptprobe.py",
            ROOT / "tests/vm_device.py",
            ROOT / "tests/layout.py",
            ROOT / "tests/test_layout.py",
            ROOT / "tests/test_apt.py",
            ROOT / "tests/test_boot.py",
            ROOT / "tests/test_display.py",
            ROOT / "tests/test_soak.py",
            ROOT / "tests/test_architecture.py",
        }

        missing = sorted(path.relative_to(ROOT).as_posix() for path in expected if not path.exists())
        remaining = sorted(path.relative_to(ROOT).as_posix() for path in legacy if path.exists())
        assert not missing, missing
        assert not remaining, remaining

    def test_test_directories_are_packages(self):
        expected = [
            ROOT / "tests/__init__.py",
            ROOT / "tests/browser/__init__.py",
            ROOT / "tests/system/__init__.py",
            ROOT / "tests/unit/__init__.py",
        ]

        missing = sorted(path.relative_to(ROOT).as_posix() for path in expected if not path.exists())
        assert not missing, missing

    def test_pyproject_uses_root_and_tools_pythonpath(self):
        pyproject = (ROOT / "pyproject.toml").read_text()

        assert 'pythonpath = [".", "tools"]' in pyproject
        assert 'pythonpath = ["tests"]' not in pyproject


def production_kotlin_sources():
    return sorted(
        path
        for source_set in ("commonMain", "jvmMain", "jsMain")
        for path in (ROOT / "src" / source_set / "kotlin").rglob("*.kt")
    )


def source_package(path):
    match = re.search(r"(?m)^package (?P<package>[\w.]+)$", path.read_text())
    assert match is not None, path
    return match.group("package")


def imported_packages(path):
    return re.findall(r"(?m)^import (?P<package>[\w.*]+)", path.read_text())


def is_under(package, prefix):
    return package == prefix or package.startswith(prefix + ".")
