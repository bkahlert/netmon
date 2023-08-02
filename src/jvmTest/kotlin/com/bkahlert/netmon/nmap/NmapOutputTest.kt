package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.nmap.NmapOutput.NmapRun
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NmapOutputTest {

    @Test
    fun from_json() = runTest {
        JsonFormat.decodeFromString<NmapOutput>(NMAP_OUTPUT) shouldBe NmapOutput(
            nmapRun = NmapRun(
                target = null, host = listOf(
                    NmapOutput.Host(
                        status = NmapOutput.Host.Status(state = NmapOutput.Host.Status.HostStates.up, reason = "arp-response", reasonTtl = "0"),
                        address = listOf(
                            NmapOutput.Host.Address(addr = "192.168.42.180", addrType = NmapOutput.Host.Address.AttrType.ipv4, vendor = null),
                            NmapOutput.Host.Address(
                                addr = "DC:A6:32:A5:AA:B6", addrType = NmapOutput.Host.Address.AttrType.mac, vendor = "Raspberry Pi Trading"
                            ),
                        ),
                        hostnames = NmapOutput.Host.Hostnames(hostname = NmapOutput.Host.Hostname(name = "foo.bar", type = "PTR")),
                    ), NmapOutput.Host(
                        status = NmapOutput.Host.Status(state = NmapOutput.Host.Status.HostStates.up, reason = "arp-response", reasonTtl = "0"),
                        address = listOf(
                            NmapOutput.Host.Address(addr = "192.168.42.190", addrType = NmapOutput.Host.Address.AttrType.ipv4, vendor = null),
                            NmapOutput.Host.Address(
                                addr = "E4:5F:01:34:71:39", addrType = NmapOutput.Host.Address.AttrType.mac, vendor = "Raspberry Pi Trading"
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
}

// language=json
val NMAP_OUTPUT = """
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
                "@addr": "DC:A6:32:A5:AA:B6",
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
                "@addr": "E4:5F:01:34:71:39",
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
