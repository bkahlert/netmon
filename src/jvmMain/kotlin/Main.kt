import com.bkahlert.io.Logger
import com.bkahlert.netmon.Defaults
import com.bkahlert.netmon.MqttPublisher
import com.bkahlert.netmon.NetworkScanner
import com.bkahlert.netmon.NmapNetworkScanner
import com.bkahlert.netmon.Publisher
import com.bkahlert.netmon.ScanEvent
import com.bkahlert.netmon.ScanEvent.ScanCompletedEvent
import com.bkahlert.netmon.ScanResult
import com.bkahlert.serialization.JsonFormat
import com.github.ajalt.mordant.animation.progressAnimation
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner

var running = true

fun handler(sig: Int) {
    Logger.info("\nSignal $sig received, stopping...")
    running = false
}

fun main(args: Array<String>) {

    val t = Terminal(AnsiLevel.TRUECOLOR)
    TextColors.entries.forEach { color ->
        t.println(color("This text will be ${color.name} on terminals that support color"))
    }

    t.println("Table:")
    table {
        align = TextAlign.RIGHT
        outerBorder = false
        column(0) {
            align = TextAlign.LEFT
            borders = Borders.ALL
            style = TextColors.magenta
        }
        column(3) {
            borders = Borders.ALL
            style = TextColors.magenta
        }
        header {
            style(TextColors.magenta, bold = true)
            row("", "Projected Cost", "Actual Cost", "Difference")
        }
        body {
            rowStyles(TextColors.blue, TextColors.brightBlue)
            borders = Borders.TOM_BOTTOM
            row("Food", "$400", "$200", "$200")
            row("Data", "$100", "$150", "-$50")
            row("Rent", "$800", "$800", "$0")
            row("Candles", "$0", "$3,600", "-$3,600")
            row("Utility", "$145", "$150", "-$5")
        }
        footer {
            style(bold = true)
            row {
                cell("Subtotal")
                cell("$-3,455") { columnSpan = 3 }
            }
        }
        captionBottom("Budget courtesy @dril")
    }.render(t, 80)

    t.println("TextAnimation:")
    val a = t.textAnimation<Int> { frame ->
        (1..50).joinToString("") {
            val hue = (frame + it) * 3 % 360
            TextColors.hsv(hue, 1, 1)("━")
        }
    }

//    t.cursor.hide(showOnExit = true)
    repeat(120) {
        a.update(it)
        Thread.sleep(25)
    }

    t.println("ProgressAnimation:")
    val progress = t.progressAnimation {
        spinner(Spinner.Dots(TextColors.brightBlue))
        text("my-file.bin")
        percentage()
        progressBar()
        completed()
        speed("B/s")
        timeRemaining()
    }

    progress.start()

    // Sleep for a few seconds to show the indeterminate state
    Thread.sleep(5000)

    // Update the progress as the download progresses
    progress.updateTotal(3_000_000_000)
    repeat(20) {
        progress.advance(150_000_000)
        Thread.sleep(50)
    }

    progress.stop()

    // TODO
//    signal(SIGINT, staticCFunction(::handler))

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
    while (running) {
        val new = networkScanner.scan(network).also {
            scansPublisher.publish(ScanCompletedEvent(it))
        }
        old.diff(new).forEach { event ->
            updatesPublisher.publish(event)
        }
        old = old.merge(new).also { it.save() }
        Thread.sleep(1000L)
    }

    Logger.info("Stopped")
}
