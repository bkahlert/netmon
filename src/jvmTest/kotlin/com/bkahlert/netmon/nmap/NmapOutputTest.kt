package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.nmap.NmapOutput.NmapRun
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NmapOutputTest {

    @Test
    fun from_json_scan() = runTest {
        JsonFormat.decodeFromString<NmapOutput>(NMAP_SCAN_OUTPUT) shouldBe NmapOutput(
            nmapRun = NmapRun(
                target = null, host = listOf(
                    NmapOutput.Host(
                        status = NmapOutput.Host.Status(state = NmapOutput.Host.Status.HostStates.up, reason = "arp-response", reasonTtl = "0"),
                        address = listOf(
                            NmapOutput.Host.Address(addr = "192.168.42.180", addrType = NmapOutput.Host.Address.AttrType.ipv4, vendor = null),
                            NmapOutput.Host.Address(
                                addr = "DC:A6:32:A5:BA:B6", addrType = NmapOutput.Host.Address.AttrType.mac, vendor = "Raspberry Pi Trading"
                            ),
                        ),
                        hostnames = NmapOutput.Host.Hostnames(hostname = NmapOutput.Host.Hostname(name = "foo.bar", type = "PTR")),
                    ), NmapOutput.Host(
                        status = NmapOutput.Host.Status(state = NmapOutput.Host.Status.HostStates.up, reason = "arp-response", reasonTtl = "0"),
                        address = listOf(
                            NmapOutput.Host.Address(addr = "192.168.42.190", addrType = NmapOutput.Host.Address.AttrType.ipv4, vendor = null),
                            NmapOutput.Host.Address(
                                addr = "E4:5F:01:34:81:39", addrType = NmapOutput.Host.Address.AttrType.mac, vendor = "Raspberry Pi Trading"
                            ),
                        ),
                        hostnames = NmapOutput.Host.Hostnames(hostname = null),
                    ), NmapOutput.Host(
                        status = NmapOutput.Host.Status(state = NmapOutput.Host.Status.HostStates.up, reason = "localhost-response", reasonTtl = "0"),
                        address = listOf(
                            NmapOutput.Host.Address(addr = "192.168.42.33", addrType = NmapOutput.Host.Address.AttrType.ipv4, vendor = null),
                        ),
                        hostnames = NmapOutput.Host.Hostnames(hostname = null),
                    )
                )
            )
        )
    }

    @Test
    fun from_json_script() = runTest {
        JsonFormat.decodeFromString<NmapOutput>(NMAP_SCRIPT_OUTPUT) should { output ->
            output.nmapRun.host.shouldNotBeNull() should {
                it.first().hostscript shouldBe NmapOutput.Host.Hostscript(
                    NmapOutput.Host.Script(
                        id = "nbstat",
                        output = """
                            NetBIOS name: FOO, NetBIOS user: <unknown>, NetBIOS MAC: <unknown> (unknown)
                            Names:
                              FOO<00>            Flags: <unique><active><permanent>
                              FOO<20>            Flags: <unique><active><permanent>
                        """.trimIndent()
                    )
                )
            }
        }
    }
}

// language=json
val NMAP_SCAN_OUTPUT = """
    {
      "nmaprun": {
        "@scanner": "nmap",
        "@args": "nmap -sn -oX output.xml 192.168.42.0/24",
        "@start": "1691000916",
        "@startstr": "Wed Aug  2 20:28:36 2023",
        "@version": "7.94",
        "@xmloutputversion": "1.05",
        "verbose": {
          "@level": "0",
          "#tail": "\n"
        },
        "debugging": {
          "@level": "0",
          "#tail": "\n"
        },
        "host": [
          {
            "status": {
              "@state": "up",
              "@reason": "arp-response",
              "@reason_ttl": "0",
              "#tail": "\n"
            },
            "address": [
              {
                "@addr": "192.168.42.180",
                "@addrtype": "ipv4",
                "#tail": "\n"
              },
              {
                "@addr": "DC:A6:32:A5:BA:B6",
                "@addrtype": "mac",
                "@vendor": "Raspberry Pi Trading",
                "#tail": "\n"
              }
            ],
            "hostnames": {
              "hostname": {
                "@name": "foo.bar",
                "@type": "PTR",
                "#tail": "\n"
              },
              "#tail": "\n",
              "#text": "\n"
            },
            "times": {
              "@srtt": "6532",
              "@rttvar": "6532",
              "@to": "100000",
              "#tail": "\n"
            },
            "#tail": "\n"
          },
          {
            "status": {
              "@state": "up",
              "@reason": "arp-response",
              "@reason_ttl": "0",
              "#tail": "\n"
            },
            "address": [
              {
                "@addr": "192.168.42.190",
                "@addrtype": "ipv4",
                "#tail": "\n"
              },
              {
                "@addr": "E4:5F:01:34:81:39",
                "@addrtype": "mac",
                "@vendor": "Raspberry Pi Trading",
                "#tail": "\n"
              }
            ],
            "hostnames": {
              "#tail": "\n",
              "#text": "\n"
            },
            "times": {
              "@srtt": "2040",
              "@rttvar": "5000",
              "@to": "100000",
              "#tail": "\n"
            },
            "#tail": "\n"
          },
          {
            "status": {
              "@state": "up",
              "@reason": "localhost-response",
              "@reason_ttl": "0",
              "#tail": "\n"
            },
            "address": {
              "@addr": "192.168.42.33",
              "@addrtype": "ipv4",
              "#tail": "\n"
            },
            "hostnames": {
              "#tail": "\n",
              "#text": "\n"
            },
            "#tail": "\n"
          }
        ],
        "runstats": {
          "finished": {
            "@time": "1691000919",
            "@timestr": "Wed Aug  2 20:28:39 2023",
            "@summary": "Nmap done at Wed Aug  2 20:28:39 2023; 256 IP addresses (18 hosts up) scanned in 3.37 seconds",
            "@elapsed": "3.37",
            "@exit": "success"
          },
          "hosts": {
            "@up": "18",
            "@down": "238",
            "@total": "256",
            "#tail": "\n"
          },
          "#tail": "\n"
        },
        "#text": "\n"
      }
    }
""".trimIndent()

// language=json
val NMAP_SCRIPT_OUTPUT = """
    {
      "nmaprun": {
        "@scanner": "nmap",
        "@args": "/opt/homebrew/bin/nmap -sU --script nbstat -p 137 -oX - 192.168.42.1",
        "@start": "1691007576",
        "@startstr": "Wed Aug  2 22:19:36 2023",
        "@version": "7.94",
        "@xmloutputversion": "1.05",
        "scaninfo": {
          "@type": "udp",
          "@protocol": "udp",
          "@numservices": "1",
          "@services": "137",
          "#tail": "\n"
        },
        "verbose": {
          "@level": "0",
          "#tail": "\n"
        },
        "debugging": {
          "@level": "0",
          "#tail": "\n"
        },
        "hosthint": {
          "status": {
            "@state": "up",
            "@reason": "arp-response",
            "@reason_ttl": "0",
            "#tail": "\n"
          },
          "address": [
            {
              "@addr": "192.168.42.1",
              "@addrtype": "ipv4",
              "#tail": "\n"
            },
            {
              "@addr": "34:31:C4:6C:17:7F",
              "@addrtype": "mac",
              "@vendor": "AVM GmbH",
              "#tail": "\n"
            }
          ],
          "hostnames": {
            "#tail": "\n",
            "#text": "\n"
          },
          "#tail": "\n"
        },
        "host": {
          "@starttime": "1691007576",
          "@endtime": "1691007576",
          "status": {
            "@state": "up",
            "@reason": "arp-response",
            "@reason_ttl": "0",
            "#tail": "\n"
          },
          "address": [
            {
              "@addr": "192.168.42.1",
              "@addrtype": "ipv4",
              "#tail": "\n"
            },
            {
              "@addr": "34:31:C4:6C:17:7F",
              "@addrtype": "mac",
              "@vendor": "AVM GmbH",
              "#tail": "\n"
            }
          ],
          "hostnames": {
            "#tail": "\n",
            "#text": "\n"
          },
          "ports": {
            "port": {
              "@protocol": "udp",
              "@portid": "137",
              "state": {
                "@state": "open",
                "@reason": "udp-response",
                "@reason_ttl": "64"
              },
              "service": {
                "@name": "netbios-ns",
                "@method": "table",
                "@conf": "3"
              },
              "#tail": "\n"
            },
            "#tail": "\n"
          },
          "hostscript": {
            "script": {
              "@id": "nbstat",
              "@output": "NetBIOS name: FOO, NetBIOS user: <unknown>, NetBIOS MAC: <unknown> (unknown)\nNames:\n  FOO<00>            Flags: <unique><active><permanent>\n  FOO<20>            Flags: <unique><active><permanent>"
            }
          },
          "times": {
            "@srtt": "480",
            "@rttvar": "3796",
            "@to": "100000",
            "#tail": "\n"
          },
          "#tail": "\n"
        },
        "runstats": {
          "finished": {
            "@time": "1691007576",
            "@timestr": "Wed Aug  2 22:19:36 2023",
            "@summary": "Nmap done at Wed Aug  2 22:19:36 2023; 1 IP address (1 host up) scanned in 0.12 seconds",
            "@elapsed": "0.12",
            "@exit": "success"
          },
          "hosts": {
            "@up": "1",
            "@down": "0",
            "@total": "1",
            "#tail": "\n"
          },
          "#tail": "\n"
        },
        "#text": "\n"
      }
    }
""".trimIndent()
