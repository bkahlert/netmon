package com.bkahlert.netmon.router

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/** A FRITZ!Box on loopback: SOAP actions on `/upnp/control/hosts`, Digest for the ones that need rights, a list at `/devicehostlist.lua`. */
class FakeFritzBox(
    val credentials: Credentials? = Credentials("netmon", "secret"),
    private val hostList: String = HOST_LIST,
    private val unauthenticated: Map<String, String> = mapOf("GetHostNumberOfEntries" to "<NewHostNumberOfEntries>2</NewHostNumberOfEntries>"),
    private val unknownMacs: Set<String> = emptySet(),
) : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = CopyOnWriteArrayList<String>()
    val base: URI get() = URI("http://127.0.0.1:${server.address.port}")

    init {
        server.createContext("/upnp/control/hosts") { exchange -> control(exchange) }
        server.createContext("/devicehostlist.lua") { exchange -> exchange.reply(200, hostList) }
        server.start()
    }

    private fun control(exchange: HttpExchange) {
        val action = exchange.requestHeaders.getFirst("SoapAction").orEmpty().substringAfter('#').trim('"')
        val body = exchange.requestBody.readBytes().decodeToString()
        requests += "$action ${exchange.requestHeaders.getFirst("Authorization") ?: "-"} ${body.length}"
        if (body.isEmpty()) return exchange.reply(500, FAULT.replace("CODE", "502").replace("TEXT", "XML error"))
        if (action == "GetSpecificHostEntry" && unknownMacs.any { body.contains(it, ignoreCase = true) }) {
            return exchange.reply(500, FAULT.replace("CODE", "714").replace("TEXT", "NoSuchEntryInArray"))
        }
        unauthenticated[action]?.let { return exchange.reply(200, envelope(action, it)) }
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        if (credentials == null || authorization == null || !authorization.contains("username=\"${credentials.user}\"")) {
            exchange.responseHeaders.add("WWW-Authenticate", """Digest realm="HTTPS Access", nonce="0123456789abcdef", algorithm=MD5, qop="auth"""")
            return exchange.reply(401, FAULT.replace("CODE", "401").replace("TEXT", "Unauthorized"))
        }
        val expected = DigestAuth.authorization("""Digest realm="HTTPS Access", nonce="0123456789abcdef", qop="auth"""", "POST", "/upnp/control/hosts", credentials, cnonce = cnonce(authorization), nc = nc(authorization))
        if (response(expected) != response(authorization)) return exchange.reply(401, FAULT.replace("CODE", "401").replace("TEXT", "Unauthorized"))
        when (action) {
            "X_AVM-DE_GetHostListPath" -> exchange.reply(200, envelope(action, "<NewX_AVM-DE_HostListPath>/devicehostlist.lua?sid=abc</NewX_AVM-DE_HostListPath>"))
            "GetSpecificHostEntry" -> exchange.reply(200, envelope(action, "<NewIPAddress>192.0.2.70</NewIPAddress><NewActive>1</NewActive><NewHostName>LEDVANCE-Hallway-TV</NewHostName><NewInterfaceType>802.11</NewInterfaceType>"))
            else -> exchange.reply(500, FAULT.replace("CODE", "401").replace("TEXT", "Invalid Action"))
        }
    }

    override fun close() = server.stop(0)

    companion object {
        fun envelope(action: String, inner: String) = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:${action}Response xmlns:u="urn:dslforum-org:service:Hosts:1">$inner</u:${action}Response></s:Body></s:Envelope>"""
        const val FAULT = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:dslforum-org:control-1-0"><errorCode>CODE</errorCode><errorDescription>TEXT</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
        val HOST_LIST = """<?xml version="1.0" encoding="UTF-8"?>
<List>
<!-- devicehosts :2 -->
<Item><Index>1</Index><IPAddress>192.0.2.70</IPAddress><MACAddress>A8:00:00:00:00:06</MACAddress><Active>1</Active><HostName>LEDVANCE-Hallway-TV</HostName><InterfaceType>802.11</InterfaceType><X_AVM-DE_Port>0</X_AVM-DE_Port><X_AVM-DE_Speed>57</X_AVM-DE_Speed><X_AVM-DE_Model /><X_AVM-DE_Guest>0</X_AVM-DE_Guest><X_AVM-DE_DeviceClass>Generic</X_AVM-DE_DeviceClass><X_AVM-DE_DeviceClassUser>Generic</X_AVM-DE_DeviceClassUser><X_AVM-DE_FriendlyName>LEDVANCE-Hallway-TV</X_AVM-DE_FriendlyName></Item>
<Item><Index>2</Index><IPAddress>192.0.2.11</IPAddress><MACAddress>02:AA:BB:CC:00:17</MACAddress><Active>1</Active><HostName>macbookpro-a</HostName><InterfaceType>802.11</InterfaceType><X_AVM-DE_Port>0</X_AVM-DE_Port><X_AVM-DE_Speed>1088</X_AVM-DE_Speed><X_AVM-DE_Model /><X_AVM-DE_Guest>0</X_AVM-DE_Guest><X_AVM-DE_DeviceClass>Generic</X_AVM-DE_DeviceClass><X_AVM-DE_DeviceClassUser>Generic</X_AVM-DE_DeviceClassUser><X_AVM-DE_FriendlyName>MacBook Pro A</X_AVM-DE_FriendlyName></Item>
<Item><Index>3</Index><IPAddress>192.0.2.43</IPAddress><MACAddress>00:00:00:00:00:14</MACAddress><Active>1</Active><HostName>example-pi</HostName><InterfaceType>Ethernet</InterfaceType><X_AVM-DE_Port>1</X_AVM-DE_Port><X_AVM-DE_Speed>2500</X_AVM-DE_Speed><X_AVM-DE_Model /><X_AVM-DE_Guest>0</X_AVM-DE_Guest><X_AVM-DE_DeviceClass>Generic</X_AVM-DE_DeviceClass><X_AVM-DE_DeviceClassUser>Generic</X_AVM-DE_DeviceClassUser><X_AVM-DE_FriendlyName>example-pi</X_AVM-DE_FriendlyName></Item>
<Item><Index>4</Index><IPAddress>198.51.100.13</IPAddress><MACAddress>02:42:C6:33:64:0C</MACAddress><Active>0</Active><HostName>Example-Unbound</HostName><InterfaceType></InterfaceType><X_AVM-DE_Port>0</X_AVM-DE_Port><X_AVM-DE_Speed>0</X_AVM-DE_Speed><X_AVM-DE_Model /><X_AVM-DE_Guest>0</X_AVM-DE_Guest><X_AVM-DE_DeviceClass>Generic</X_AVM-DE_DeviceClass><X_AVM-DE_DeviceClassUser>Generic</X_AVM-DE_DeviceClassUser><X_AVM-DE_FriendlyName>Example-Unbound</X_AVM-DE_FriendlyName></Item>
<Item><Index>5</Index><IPAddress>198.51.100.13</IPAddress><MACAddress>02:AA:BB:CC:00:16</MACAddress><Active>1</Active><HostName>PC-198-51-100-13</HostName><InterfaceType>Ethernet</InterfaceType><X_AVM-DE_Port>1</X_AVM-DE_Port><X_AVM-DE_Speed>2500</X_AVM-DE_Speed><X_AVM-DE_Model /><X_AVM-DE_Guest>0</X_AVM-DE_Guest><X_AVM-DE_DeviceClass>Printer</X_AVM-DE_DeviceClass><X_AVM-DE_DeviceClassUser>Storage</X_AVM-DE_DeviceClassUser><X_AVM-DE_FriendlyName>PC-198-51-100-13</X_AVM-DE_FriendlyName></Item>
</List>
"""
        private fun response(header: String) = Regex("""response="(?<value>[0-9a-f]+)"""").find(header)?.groups?.get("value")?.value
        private fun cnonce(header: String) = Regex("""cnonce="(?<value>[^"]+)"""").find(header)?.groups?.get("value")?.value.orEmpty()
        private fun nc(header: String) = Regex("""nc=(?<value>[0-9a-f]+)""").find(header)?.groups?.get("value")?.value ?: "00000001"
    }
}

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", "text/xml; charset=\"utf-8\"")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
