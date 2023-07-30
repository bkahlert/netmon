package com.bkahlert.netmon.mdns

import com.bkahlert.netmon.IP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceTypeListener

/** The [ServiceInfo.getPropertyNames] and their values. */
val ServiceInfo.properties: Map<String, String>
    get() = buildMap {
        propertyNames.iterator().forEach { propertyName ->
            put(propertyName, getPropertyString(propertyName))
        }
    }

val ServiceInfo.ipAddresses: List<IP>
    get() = inetAddresses.map { IP(it) }


val JmDNS.addedServiceTypes: Flow<ServiceEvent>
    get() = callbackFlow {
        val listener: ServiceTypeListener = object : ServiceTypeListener {
            override fun serviceTypeAdded(event: ServiceEvent) {
                trySend(event)
            }

            override fun subTypeForServiceTypeAdded(event: ServiceEvent) {}
        }

        addServiceTypeListener(listener)
        awaitClose { removeServiceTypeListener(listener) }
    }.flowOn(Dispatchers.IO)

fun JmDNS.serviceAdded(type: String): Flow<ServiceEvent> = callbackFlow {
    val listener: ServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            trySend(event)
        }

        override fun serviceRemoved(event: ServiceEvent) {}
        override fun serviceResolved(event: ServiceEvent) {}
    }

    addServiceListener(type, listener)
    awaitClose { removeServiceListener(type, listener) }
}.flowOn(Dispatchers.IO)

fun JmDNS.serviceRemoved(type: String): Flow<ServiceEvent> = callbackFlow {
    val listener: ServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {}
        override fun serviceRemoved(event: ServiceEvent) {
            trySend(event)
        }

        override fun serviceResolved(event: ServiceEvent) {}
    }

    addServiceListener(type, listener)
    awaitClose { removeServiceListener(type, listener) }
}.flowOn(Dispatchers.IO)

fun JmDNS.serviceResolved(type: String): Flow<ServiceInfo> = callbackFlow {
    val listener: ServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {}
        override fun serviceRemoved(event: ServiceEvent) {}
        override fun serviceResolved(event: ServiceEvent) {
            trySend(event.info)
        }
    }

    addServiceListener(type, listener)
    awaitClose { removeServiceListener(type, listener) }
}.flowOn(Dispatchers.IO)
