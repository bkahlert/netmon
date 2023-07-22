import com.bkahlert.io.File
import com.bkahlert.io.Logger
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.MqttPublisher
import com.bkahlert.netmon.NetworkScanner
import com.bkahlert.netmon.NmapNetworkScanner
import com.bkahlert.netmon.Publisher
import com.bkahlert.netmon.ScanEvent
import com.bkahlert.netmon.ScanEvent.ScanCompletedEvent
import com.bkahlert.netmon.ScanResult
import com.bkahlert.serialization.JsonFormat
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGINT
import platform.posix.signal
import platform.posix.sleep
import kotlin.concurrent.AtomicInt

data object Defaults {
    val networks: List<Cidr> = listOf(Cidr("192.168.16.0/24"))
    val privileged: Boolean = true
    val resultFile: File = File(".netmon-result.json")
}

val running = AtomicInt(1)

fun handler(sig: Int) {
    Logger.info("\nSignal $sig received, stopping...")
    running.value = 0
}

fun main() {


    signal(SIGINT, staticCFunction(::handler))

    check(Defaults.networks.size <= 1) { "Multiple networks are not supported, yet" }

    val network = Defaults.networks.first()
    val networkScanner: NetworkScanner = NmapNetworkScanner()
    val scansPublisher: Publisher<ScanEvent> = MqttPublisher(
        topic = "dt/netmon/home/scans",
        host = "test.mosquitto.org",
        port = 1883,
        stringFormat = JsonFormat,
        serializer = ScanEvent.serializer(),
    )
    val updatesPublisher: Publisher<ScanEvent> = MqttPublisher(
        topic = "dt/netmon/home/updates",
        host = "test.mosquitto.org",
        port = 1883,
        stringFormat = JsonFormat,
        serializer = ScanEvent.serializer(),
    )

    var old = ScanResult.load() ?: run {
        Logger.info("Performing a quick initial scan...")
        NmapNetworkScanner(timingTemplate = ScanResult.Companion.TimingTemplate.Aggressive).scan(network)
    }
    while (running.value != 0) {
        val new = networkScanner.scan(network).also {
            scansPublisher.publish(ScanCompletedEvent(it))
        }
        old.diff(new).forEach { event ->
            updatesPublisher.publish(event)
        }
        old = old.merge(new).also { it.save() }
        sleep(1u)
    }

    Logger.info("Stopped")
    sleep(1u) // Sleep for a bit to make the loop more manageable
    Logger.info("Stopped2")
    sleep(1u) // Sleep for a bit to make the loop more manageable
}
