import re
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0
ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())
CURRENT_DOCS = [
    ROOT / "README.md",
    ROOT / "docs/architecture.md",
    ROOT / "docs/scanner.md",
    ROOT / "docs/display.md",
    ROOT / "docs/mqtt-contract.md",
    ROOT / "docs/how-it-works.md",
    ROOT / "docs/open-issues.md",
]
CURRENT_DOC_LINKS = [
    "docs/architecture.md",
    "docs/scanner.md",
    "docs/display.md",
    "docs/mqtt-contract.md",
    "docs/how-it-works.md",
    "docs/open-issues.md",
]
LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^(#{1,6})\s+(.*)$")


class TestMaintainerDocumentation:
    def test_current_entry_points_exist(self):
        missing = [path.relative_to(ROOT).as_posix() for path in CURRENT_DOCS if not path.exists()]

        assert not missing, missing

    def test_readme_links_to_current_maintainer_pages(self):
        readme = (ROOT / "README.md").read_text()

        for link in CURRENT_DOC_LINKS:
            assert f"]({link})" in readme

    def test_architecture_page_maps_current_ownership_and_boundaries(self):
        architecture = (ROOT / "docs/architecture.md").read_text()

        for section in (
            "## Source ownership map",
            "## Startup and shutdown ownership",
            "## Exact scan order",
            "## Display flow",
            "## Metrics boundary",
            "## Test boundaries",
        ):
            assert section in architecture
        assert "com.bkahlert.netmon.scanner.app" in architecture
        assert "com.bkahlert.netmon.display.app" in architecture
        assert "| Boundary |" in architecture
        assert "| `make test-layout` |" in architecture

    def test_scanner_and_display_pages_point_to_their_packages_and_tests(self):
        scanner = (ROOT / "docs/scanner.md").read_text()
        display = (ROOT / "docs/display.md").read_text()

        assert "src/jvmMain/kotlin/com/bkahlert/netmon/scanner" in scanner
        assert "src/jvmTest/kotlin/com/bkahlert/netmon/scanner" in scanner
        assert "make test-jvm" in scanner
        assert "src/jsMain/kotlin/com/bkahlert/netmon/display" in display
        assert "src/jsTest/kotlin/com/bkahlert/netmon/display" in display
        assert "make test-js" in display
        assert "make test-layout" in display

    def test_mqtt_contract_keeps_wire_topics_delivery_and_compatibility_rules(self):
        contract = (ROOT / "docs/mqtt-contract.md").read_text()

        for snippet in (
            "dt/netmon/${node}/${interface}/${cidr}/scan",
            "dt/netmon/${node}/${interface}/${cidr}/host",
            "dt/netmon/+/metrics",
            "QoS 1",
            "retained",
            "optional",
            "ignore unknown keys",
        ):
            assert snippet in contract

    def test_how_it_works_stays_a_navigation_landing_page(self):
        how_it_works = (ROOT / "docs/how-it-works.md").read_text()

        for link in ("architecture.md", "scanner.md", "display.md", "mqtt-contract.md", "open-issues.md"):
            assert f"]({link})" in how_it_works

    def test_local_links_in_current_docs_resolve(self):
        broken = []
        for document in CURRENT_DOCS:
            for target in local_links(document):
                destination, _, anchor = target.partition("#")
                resolved = (document.parent / destination).resolve() if destination else document
                if destination and not resolved.exists():
                    broken.append(f"{document.relative_to(ROOT)} -> {target} (missing file)")
                    continue
                if anchor and anchor not in headings_of(resolved):
                    broken.append(f"{document.relative_to(ROOT)} -> {target} (missing heading)")

        assert not broken, broken


def local_links(document: Path) -> list[str]:
    links = []
    for target in LINK.findall(document.read_text()):
        if target.startswith(("http://", "https://", "mailto:", "data:")):
            continue
        links.append(target)
    return links


def headings_of(document: Path) -> set[str]:
    anchors = set()
    in_fence = False
    for line in document.read_text().splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        match = HEADING.match(line)
        if match:
            anchors.add(slug(match.group(2)))
    return anchors


def slug(heading: str) -> str:
    heading = re.sub(r"`([^`]+)`", r"\1", heading)
    heading = heading.strip().lower()
    heading = re.sub(r"[^a-z0-9\s_-]", "", heading)
    heading = re.sub(r"[\s_]+", "-", heading)
    heading = re.sub(r"-+", "-", heading)
    return heading.strip("-")
