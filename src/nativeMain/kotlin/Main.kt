import com.bkahlert.exec.checkCommand
import com.bkahlert.io.File
import com.bkahlert.io.Logger
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.ScanEvent.ScanCompletedEvent
import com.bkahlert.netmon.ScanResult
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
    checkCommand("nmap")

    signal(SIGINT, staticCFunction(::handler))

    check(Defaults.networks.size <= 1) { "Multiple networks are not supported, yet" }

    val network = Defaults.networks.first()
    var old = ScanResult.load() ?: run {
        Logger.info("Performing a quick initial scan...")
        ScanResult.get(network = network, timingTemplate = ScanResult.Companion.TimingTemplate.Aggressive)
    }
    while (running.value != 0) {
        val new = ScanResult.get(network = network).also {
            ScanCompletedEvent(it).publish()
        }
        old.diff(new).forEach { event ->
            event.publish()
        }
        old = old.merge(new).also { it.save() }
        sleep(1u)
    }

    Logger.info("Stopped")
    sleep(1u) // Sleep for a bit to make the loop more manageable
    Logger.info("Stopped2")
    sleep(1u) // Sleep for a bit to make the loop more manageable
}
