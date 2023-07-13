import com.bkahlert.exec.checkCommand
import com.bkahlert.netmon.ScanEvent.ScanCompletedEvent
import com.bkahlert.netmon.ScanEvent.ScanRestoredEvent
import com.bkahlert.netmon.ScanResult
import com.bkahlert.serialization.JsonFormat
import kotlinx.cinterop.staticCFunction
import kotlinx.serialization.encodeToString
import platform.posix.SIGINT
import platform.posix.signal
import platform.posix.sleep
import kotlin.concurrent.AtomicInt

data object Config {
    val targets: List<String> = listOf("192.168.16.0/24")
    val resultFile: String = ".netmon-result"
}

val running = AtomicInt(1)

fun handler(sig: Int) {
    println("\nSignal $sig received, stopping...")
    running.value = 0
}

fun main() {
    checkCommand("nmap")

    signal(SIGINT, staticCFunction(::handler))
    var old = ScanResult.load().also {
        JsonFormat.encodeToString(it.hosts.first())
            .also { println(it) }
        ScanRestoredEvent(it).publish()
    }
    while (running.value != 0) {
        val new = ScanResult.get().also {
            ScanCompletedEvent(it).publish()
        }
        old.diff(new).forEach { event ->
            event.publish()
        }
        old = old.merge(new).also { it.save() }
        sleep(1u)
    }

    println("Stopped")
    sleep(1u) // Sleep for a bit to make the loop more manageable
    println("Stopped2")
    sleep(1u) // Sleep for a bit to make the loop more manageable
}
