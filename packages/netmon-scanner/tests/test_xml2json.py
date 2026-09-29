import json
import subprocess
import sys
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0
SCRIPT = Path(__file__).parents[3] / "src/jvmMain/resources/xml2json.py"
SAMPLE = """<?xml version="1.0"?>
<nmaprun scanner="nmap" args="nmap -sn 10.0.0.0/29"><host><status state="up" reason="arp-response"/>
<address addr="10.0.0.1" addrtype="ipv4"/><address addr="AA:BB:CC:DD:EE:FF" addrtype="mac" vendor="Test"/>
<hostnames><hostname name="router" type="PTR"/></hostnames></host></nmaprun>"""


def test_converts_nmap_xml_with_the_system_python(tmp_path):
    xml = tmp_path / "scan.xml"
    xml.write_text(SAMPLE)

    out = subprocess.run([sys.executable, str(SCRIPT), "--type", "xml2json", str(xml)], capture_output=True, text=True, check=True).stdout

    host = json.loads(out)["nmaprun"]["host"]
    assert host["status"]["@state"] == "up"
    assert {address["@addr"] for address in host["address"]} == {"10.0.0.1", "AA:BB:CC:DD:EE:FF"}
